package com.jiuxina.ying.data.undo

import android.util.Log
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.domain.model.UndoOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 撤销队列（M2c）。
 *
 * 语义：
 * - 每个操作**立即落库**，同时把受影响事件的完整快照存进内存 + DataStore；
 * - [undo] 用快照整批 upsert 回填，删除/状态切换共用一条回滚路径；
 * - 只保留**最近一次**操作（新操作顶替旧的），旧操作的效果已落库，不再可撤销；
 * - 窗口是 6 秒，且**只在前台消耗**：
 *   - 离开前台 [pauseWindow] 记下冻结那一刻的剩余量，回来 [resumeWindow] 按剩余量重新排期；
 *   - 进程被回收后重开（[restore]），只要这条删除发生在 10 分钟内，就**重新给满 6 秒**。
 *
 * 最后一条是刻意为之：真机上用户「退出后台」多半是从最近任务划掉应用＝进程终止，
 * 若仍按墙钟判过期，用户会看到「划掉重开就没有撤销」，而他对「进程被谁杀掉」毫无控制力。
 * 撤销是快照回填，重复给窗口不会产生错误数据，代价只是多几秒的撤销条。
 */
class UndoRepository(
    private val settings: SettingsRepository,
    private val events: EventRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _pending = MutableStateFlow<UndoOperation?>(null)
    val pending: StateFlow<UndoOperation?> = _pending.asStateFlow()

    private var expiryJob: Job? = null

    /**
     * 离开前台的时刻，`null` 表示窗口正在正常走动。
     *
     * 只记在内存里就够了：进程活着由 [resumeWindow] 按剩余量续期；进程死了则由
     * [restore] 直接给满窗口，不依赖这个值。
     */
    private var pausedAtMillis: Long? = null

    /**
     * 启动恢复。
     *
     * 与「墙钟倒计时」不同：只要这条操作发生在 [MAX_RESTORE_AGE_MILLIS] 之内，就重新给满一个
     * 6 秒窗口。原因是进程随时可能被系统回收（国产 ROM 更激进），用户对「什么时候被杀」
     * 没有任何控制力，不该为此丢掉撤销机会。
     *
     * 超过时限则清空：隔夜回来还弹一条只剩几秒的撤销，只会像 bug。
     */
    suspend fun restore() {
        pausedAtMillis = null
        val saved = runCatching { UndoCodec.decode(settings.rawString(KEY_PENDING).first()) }.getOrNull()
        if (saved == null) {
            clearPersistedOnly()
            return
        }
        if (clock() - saved.pushedAtMillis > MAX_RESTORE_AGE_MILLIS) {
            clear()
            return
        }

        val refreshed = saved.withDeadline(clock() + UndoOperation.WINDOW_MILLIS)
        _pending.value = refreshed
        // 落盘失败不影响本次撤销：内存里已经有了，只是下次重开不再恢复
        runCatching { persist(refreshed) }.onFailure { Log.w(TAG, "重启续期撤销窗口落盘失败", it) }
        scheduleExpiry(refreshed)
    }

    /** 记录一次新操作（替换旧的待撤销项）并开始计时。 */
    suspend fun push(operation: UndoOperation) {
        pausedAtMillis = null
        _pending.value = operation
        persist(operation)
        scheduleExpiry(operation)
    }

    /**
     * 应用离开前台：冻结倒计时。
     *
     * 只记下时刻并取消定时任务——剩余量按「冻结那一刻」算，回来时连同后台耗时一起扣掉，
     * 所以不存在「后台把窗口烧完」的问题。
     */
    fun pauseWindow() {
        if (pausedAtMillis != null || _pending.value == null) return
        pausedAtMillis = clock()
        expiryJob?.cancel()
        expiryJob = null
    }

    /**
     * 回到前台：按冻结那一刻的剩余量重新排期。
     *
     * 例如离开时还剩 4 秒，后台待 1 分钟，回来仍然剩 4 秒。
     */
    suspend fun resumeWindow() {
        val pausedAt = pausedAtMillis ?: return
        pausedAtMillis = null
        val operation = _pending.value ?: return
        val remaining = operation.remainingMillis(pausedAt)
        if (remaining <= 0L) {
            clear()
            return
        }

        val refreshed = operation.withDeadline(clock() + remaining)
        _pending.value = refreshed
        runCatching { persist(refreshed) }.onFailure { Log.w(TAG, "续期撤销窗口落盘失败", it) }
        scheduleExpiry(refreshed)
    }

    /**
     * 执行撤销。
     *
     * @return 是否真的回填了数据。窗口已过期或没有待撤销项时返回 false，由 UI 提示。
     */
    suspend fun undo(): Boolean {
        val operation = _pending.value ?: return false
        if (operation.isExpired(clock())) {
            clear()
            return false
        }
        events.restore(operation.snapshot)
        clear()
        return true
    }

    /** 主动丢弃待撤销项（用户忽略、或已被新操作顶替后超时）。 */
    suspend fun clear() {
        expiryJob?.cancel()
        expiryJob = null
        pausedAtMillis = null
        _pending.value = null
        clearPersistedOnly()
    }

    private fun scheduleExpiry(operation: UndoOperation) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(operation.remainingMillis(clock()).coerceAtLeast(0L))
            // 期间可能已被新操作替换，只有仍是同一条时才清内存。
            // 刻意**不删落盘数据**：用户很可能在这之后就划掉应用，重开时 restore 还要靠它
            // 给一次撤销机会；真正作废由 restore 的时限判断，或下一次 push 覆盖。
            if (_pending.value?.id == operation.id) {
                _pending.value = null
            }
        }
    }

    private suspend fun persist(operation: UndoOperation) {
        settings.putString(KEY_PENDING, UndoCodec.encode(operation))
    }

    private suspend fun clearPersistedOnly() {
        runCatching { settings.remove(KEY_PENDING) }
        // 上一版把冻结时刻单独存过一份，这里顺手清掉，避免老数据被误读
        runCatching { settings.remove(KEY_PAUSED_AT) }
    }

    companion object {
        private const val TAG = "YingUndo"

        /**
         * 一条撤销最多还能「复活」多久。
         *
         * 10 分钟足够覆盖「切出去看一眼」「划掉卡片马上重开」「被系统杀掉再点图标」；
         * 再久就没有意义了——那时的删除早已成为用户认知里的事实。
         */
        const val MAX_RESTORE_AGE_MILLIS = 10 * 60 * 1000L

        /** 新键；旧版无撤销持久化需求，无迁移问题。 */
        const val KEY_PENDING = "undo.pending_v1"

        /** 旧版（16194e8/099ba22）用过的冻结时刻键，仅保留用于清理。 */
        private const val KEY_PAUSED_AT = "undo.paused_at_v1"
    }
}
