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
 * - [UndoOperation.expiresAtMillis] 随快照一起持久化，进程被杀后重启仍按剩余时间提供撤销；
 * - 窗口按**前台可见时间**计：应用离开前台时 [pauseWindow] 冻结、回来时 [resumeWindow] 补回。
 *
 * 到期清理由内部定时任务完成；UI 不需要自己计倒计时，只读 [pending] 与 `remainingSeconds`。
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
     * 窗口冻结的起点，`null` 表示窗口正在正常走动。
     *
     * 真机反馈：删完事件切出去（或按 Home）超过 6 秒再回来，撤销条已经没了，用户根本来不及点。
     * 所以窗口算的是「前台可见时间」而不是墙钟时间——后台那段时间不计入 6 秒。
     *
     * 刻意不落盘：进程若在后台被杀，重启后 [restore] 仍按墙钟判过期。这条路径要求
     * 「点完删除 6 秒内被系统回收」，重开时剩余的可用时间本就只有几百毫秒，不值得为它多存一份状态。
     */
    private var pausedAtMillis: Long? = null

    /**
     * 启动恢复：仅当窗口未过期时才恢复撤销条。
     *
     * 过期或内容损坏一律清掉持久化，避免用户下次启动看到一条永远点不动的"已失效撤销"。
     */
    suspend fun restore() {
        pausedAtMillis = null
        val saved = runCatching { UndoCodec.decode(settings.rawString(KEY_PENDING).first()) }.getOrNull()
        if (saved == null) {
            clearPersistedOnly()
            return
        }
        if (saved.isExpired(clock())) {
            clear()
            return
        }
        _pending.value = saved
        scheduleExpiry(saved)
    }

    /** 记录一次新操作（替换旧的待撤销项）并开始计时。 */
    suspend fun push(operation: UndoOperation) {
        pausedAtMillis = null
        _pending.value = operation
        persist(operation)
        scheduleExpiry(operation)
    }

    /**
     * 应用离开前台：冻结倒计时，后台时间不计入撤销窗口。
     *
     * 只取消定时任务、记下时刻；`pending` 继续保留，回来时由 [resumeWindow] 顺延到期时刻。
     */
    fun pauseWindow() {
        if (pausedAtMillis != null || _pending.value == null) return
        pausedAtMillis = clock()
        expiryJob?.cancel()
        expiryJob = null
    }

    /**
     * 回到前台：把离开期间冻结掉的时长补回到期时刻，再按剩余时间重新排期。
     *
     * 到期时刻变了就一并落盘，让持久化副本与内存一致——否则下一次冷启动 [restore]
     * 会拿一个已经走完的旧时刻来判过期，等于白冻结一场。
     */
    suspend fun resumeWindow() {
        val pausedAt = pausedAtMillis ?: return
        pausedAtMillis = null
        val operation = _pending.value ?: return
        val frozenMillis = (clock() - pausedAt).coerceAtLeast(0L)
        if (frozenMillis == 0L) {
            scheduleExpiry(operation)
            return
        }
        val shifted = operation.copy(expiresAtMillis = operation.expiresAtMillis + frozenMillis)
        _pending.value = shifted
        // 落盘失败不影响本次撤销：内存里的 pending 已经顺延，只是进程被杀后不再恢复
        runCatching { persist(shifted) }.onFailure { Log.w(TAG, "顺延撤销窗口落盘失败", it) }
        scheduleExpiry(shifted)
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
            // 期间可能已被新操作替换，只有仍是同一条时才清理
            if (_pending.value?.id == operation.id) {
                _pending.value = null
                clearPersistedOnly()
            }
        }
    }

    private suspend fun persist(operation: UndoOperation) {
        settings.putString(KEY_PENDING, UndoCodec.encode(operation))
    }

    private suspend fun clearPersistedOnly() {
        runCatching { settings.remove(KEY_PENDING) }
    }

    companion object {
        private const val TAG = "YingUndo"

        /** 新键；旧版无撤销持久化需求，无迁移问题。 */
        const val KEY_PENDING = "undo.pending_v1"
    }
}
