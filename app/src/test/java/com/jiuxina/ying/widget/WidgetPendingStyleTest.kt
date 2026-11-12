package com.jiuxina.ying.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jiuxina.ying.data.settings.SettingsRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「先设置、再落桌面」的待认领槽位。
 *
 * 桌面只在 pin 成功后才分配 widgetId，所以用户在配置台里调好的样式必须先挂在槽位上，
 * 等新实例第一次 onUpdate 时认领。这一层的出错方式都很隐蔽（认领错、认领两次、
 * 过期了还认领），而表现在用户眼里只有一句「我设置的外观没生效」，所以在存储层锁住。
 *
 * 跑在 Robolectric + runTest 下（与 [com.jiuxina.ying.data.undo.UndoWindowTest] 同一搭法）：
 * 纯 JVM 的 Okio 存储在「读 → 再写」时文件句柄释放滞后，Windows 上 rename 会间歇性失败
 * （"Unable to rename settings.preferences_pb.tmp"），不是被测逻辑的问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetPendingStyleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** DataStore 不接受「已存在但内容为空」的文件，每次用新名字。 */
    private fun openRepo(): SettingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(temp.root, "pending-${UUID.randomUUID()}.preferences_pb")
        },
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `新实例认领到待用样式并落成自己的配置`() = runTest {
        val repo = openRepo()
        val store = WidgetConfigStore(repo)
        store.savePending(isDetail = false, WidgetStyle(preset = WidgetPreset.DETAILED, showProgress = true))

        val adopted = store.adoptPending(widgetId = 9, isDetail = false)

        assertNotNull("新加的实例应当认领到这份样式", adopted)
        assertEquals(WidgetPreset.DETAILED, store.load(9).preset)
        assertEquals(true, store.load(9).showProgress)
    }

    @Test
    fun `一次设置只作用一个新实例`() = runTest {
        val store = WidgetConfigStore(openRepo())
        store.savePending(isDetail = false, WidgetStyle(preset = WidgetPreset.DETAILED))

        assertNotNull(store.adoptPending(widgetId = 9, isDetail = false))
        assertNull("第二次 pin 不该复用同一份待用样式", store.adoptPending(widgetId = 10, isDetail = false))
    }

    @Test
    fun `已有专属配置的实例不认领`() = runTest {
        val repo = openRepo()
        val store = WidgetConfigStore(repo)
        store.save(7, WidgetStyle(preset = WidgetPreset.MINIMAL))
        store.savePending(isDetail = false, WidgetStyle(preset = WidgetPreset.DETAILED))

        assertNull("老实例被编辑过，不该被新样式顶掉", store.adoptPending(widgetId = 7, isDetail = false))
        assertEquals(WidgetPreset.MINIMAL, store.load(7).preset)
        assertNotNull(
            "这份槽位没被消耗，应留给下一个真正的新实例",
            repo.stringOnce(WidgetConfigStore.pendingKey(isDetail = false)),
        )
    }

    @Test
    fun `过期槽位不认领但会被清掉`() = runTest {
        val repo = openRepo()
        val store = WidgetConfigStore(repo)
        store.savePending(isDetail = false, WidgetStyle(preset = WidgetPreset.DETAILED))
        // 把写入时刻改成很久以前，模拟「点了添加到桌面，但系统确认框上取消了」
        repo.putStrings(mapOf(WidgetConfigStore.pendingAtKey(isDetail = false) to "1"))

        assertNull(store.adoptPending(widgetId = 11, isDetail = false))
        assertNull(
            "过期槽位必须清掉，否则几天后手动加小部件会莫名套上旧样式",
            repo.stringOnce(WidgetConfigStore.pendingKey(isDetail = false)),
        )
    }

    @Test
    fun `总览与单事件的槽位互不干扰`() = runTest {
        val store = WidgetConfigStore(openRepo())
        store.savePending(isDetail = true, WidgetStyle(eventId = "event-1"))

        assertNull("总览不该认领单事件的样式", store.adoptPending(widgetId = 5, isDetail = false))
        assertEquals("event-1", store.adoptPending(widgetId = 6, isDetail = true)?.eventId)
    }

    @Test
    fun `没有槽位时不产生任何配置`() = runTest {
        val store = WidgetConfigStore(openRepo())

        assertNull(store.adoptPending(widgetId = 12, isDetail = false))
        assertEquals(false, store.hasOwn(12))
    }
}
