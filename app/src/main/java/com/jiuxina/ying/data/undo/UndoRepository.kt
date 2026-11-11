package com.jiuxina.ying.data.undo

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
 * - [UndoOperation.expiresAtMillis] 随快照一起持久化，进程被杀后重启仍按剩余时间提供撤销。
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
     * 启动恢复：仅当窗口未过期时才恢复撤销条。
     *
     * 过期或内容损坏一律清掉持久化，避免用户下次启动看到一条永远点不动的"已失效撤销"。
     */
    suspend fun restore() {
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
        _pending.value = operation
        persist(operation)
        scheduleExpiry(operation)
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
        /** 新键；旧版无撤销持久化需求，无迁移问题。 */
        const val KEY_PENDING = "undo.pending_v1"
    }
}
