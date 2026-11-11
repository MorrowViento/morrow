package com.jiuxina.ying.data.undo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.db.CategoryUsage
import com.jiuxina.ying.data.db.EventDao
import com.jiuxina.ying.data.db.EventEntity
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.domain.model.UndoKind
import com.jiuxina.ying.domain.model.UndoOperation
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 撤销窗口的「前台计时 + 跨进程续期」语义。
 *
 * 真机反馈（两轮）：① 删完切到别的应用超过 6 秒回来，撤销条已经消失；② 从最近任务划掉应用
 * （进程终止）再重开同样没有——用户对「进程什么时候被杀」毫无控制力，不该因此丢掉撤销。
 * 现在的规则：前台只按剩余量倒计时；冷启动重开只要这条删除发生在 10 分钟内，就重新给满 6 秒。
 *
 * 时间全部走可控时钟 + 虚拟调度器：仓库内部的到期任务挂在 `backgroundScope` 上，
 * 不会被真实墙钟提前触发（否则这类测试会在慢机器上偶发失败）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UndoWindowTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** DataStore 自己的写入作用域，与撤销到期计时无关。 */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 可控时钟：所有断言都基于这个值，不依赖真实等待。 */
    private var now = 1_000_000L

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    /** DataStore 不接受「已存在但内容为空」的文件，每次用新名字。 */
    private fun newSettings(): SettingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = storeScope) {
            File(temp.root, "undo-${UUID.randomUUID()}.preferences_pb")
        },
    )

    private fun TestScope.repository(settings: SettingsRepository = newSettings()): UndoRepository =
        UndoRepository(
            settings = settings,
            events = EventRepository(FakeEventDao()),
            scope = backgroundScope,
            clock = { now },
        )

    private fun operation(expiresAt: Long) = UndoOperation(
        id = "u",
        kind = UndoKind.DELETE,
        message = "已删除「春节回家」",
        snapshot = emptyList(),
        expiresAtMillis = expiresAt,
    )

    @Test
    fun `后台一分钟再回来，窗口顺延，撤销条还在且剩余时间不变`() = runTest {
        val repo = repository()
        repo.push(operation(expiresAt = now + UndoOperation.WINDOW_MILLIS))

        now += 2_000 // 前台已经看了 2 秒
        repo.pauseWindow()
        now += 60_000 // 后台待了 1 分钟
        repo.resumeWindow()

        val pending = repo.pending.value
        assertNotNull("回到前台后撤销条必须还在（now=$now）", pending)
        assertEquals("后台时间不应计入窗口，应剩 4 秒", 4_000L, pending!!.remainingMillis(now))
    }

    @Test
    fun `后台期间不排到期任务，回到前台才继续倒数`() = runTest {
        val repo = repository()
        repo.push(operation(expiresAt = now + UndoOperation.WINDOW_MILLIS))
        repo.pauseWindow()
        now += 120_000
        // 冻结中：没有定时任务把它清掉（清掉就是用户看到的那次「回来就没了」）
        assertNotNull(repo.pending.value)
    }

    /**
     * 用户最常见的操作其实是「划掉最近任务」——那是进程终止，不是回到后台。
     * 冷启动重开时不再按墙钟判过期，而是重新给满一个窗口，否则这条撤销就白做了。
     */
    @Test
    fun `划掉任务重开，重新给满 6 秒且能撤销`() = runTest {
        val settings = newSettings()
        val before = repository(settings)
        before.push(operation(expiresAt = now + UndoOperation.WINDOW_MILLIS))
        now += 2_000
        before.pauseWindow()
        now += 60_000 // 后台待 1 分钟，随后进程被杀：不会再有 resumeWindow

        // 新进程：读同一份落盘数据，仓库实例是新的
        val after = repository(settings)
        after.restore()

        val pending = after.pending.value
        assertNotNull("重开后仍应看到撤销条（now=$now）", pending)
        assertEquals(
            "重开应重新给满一个窗口，而不是接着墙钟的残值",
            UndoOperation.WINDOW_MILLIS,
            pending!!.remainingMillis(now),
        )
        assertTrue("这时点撤销必须真的回填", after.undo())
    }

    @Test
    fun `删除超过十分钟再重开，不再恢复撤销条`() = runTest {
        val settings = newSettings()
        val before = repository(settings)
        before.push(operation(expiresAt = now + UndoOperation.WINDOW_MILLIS))
        before.pauseWindow()
        now += UndoRepository.MAX_RESTORE_AGE_MILLIS + 1_000

        val after = repository(settings)
        after.restore()

        assertNull("搁太久的撤销条弹出来只会像 bug", after.pending.value)
    }

    @Test
    fun `没暂停时 resumeWindow 不动到期时刻`() = runTest {
        val repo = repository()
        val expiresAt = now + UndoOperation.WINDOW_MILLIS
        repo.push(operation(expiresAt = expiresAt))
        repo.resumeWindow()
        assertEquals(expiresAt, repo.pending.value?.expiresAtMillis)
    }

    @Test
    fun `前台过期后划掉重开，十分钟内仍能撤销`() = runTest {
        val settings = newSettings()
        val before = repository(settings)
        before.push(operation(expiresAt = now + UndoOperation.WINDOW_MILLIS))
        // 前台就把 6 秒耗光（撤销条早消失），随后用户划掉应用
        now += 30_000

        val after = repository(settings)
        after.restore()

        val pending = after.pending.value
        assertNotNull("过期只清内存，落盘那条要留给重开的机会", pending)
        assertEquals(UndoOperation.WINDOW_MILLIS, pending!!.remainingMillis(now))
    }

    @Test
    fun `撤销之后不会再被冻结残留影响`() = runTest {
        val repo = repository()
        repo.push(operation(expiresAt = now + UndoOperation.WINDOW_MILLIS))
        repo.pauseWindow()
        now += 1_000
        repo.resumeWindow()
        repo.undo()
        assertNull(repo.pending.value)

        // 下一次操作从零开始计时，不会把上一条的冻结时长带进来
        now += 30_000
        val expiresAt = now + UndoOperation.WINDOW_MILLIS
        repo.push(operation(expiresAt = expiresAt))
        assertEquals(expiresAt, repo.pending.value?.expiresAtMillis)
    }

    /** 撤销窗口的测试不碰数据库，给个空壳即可。 */
    private class FakeEventDao : EventDao {
        override fun observeAll(): Flow<List<EventEntity>> = flowOf(emptyList())
        override suspend fun loadAll(): List<EventEntity> = emptyList()
        override suspend fun findById(id: String): EventEntity? = null
        override suspend fun count(): Int = 0
        override suspend fun upsert(entity: EventEntity) = Unit
        override suspend fun upsertAll(entities: List<EventEntity>) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun deleteByIds(ids: List<String>): Int = 0
        override suspend fun clear() = Unit
        override fun observeCategoryUsage(): Flow<List<CategoryUsage>> = flowOf(emptyList())
        override suspend fun countByCategory(category: String): Int = 0
        override suspend fun renameCategory(from: String, to: String): Int = 0
    }
}
