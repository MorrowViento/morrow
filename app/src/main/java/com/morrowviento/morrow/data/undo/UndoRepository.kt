package com.morrowviento.morrow.data.undo

import android.util.Log
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.domain.model.UndoOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 立即落库 + 完整快照回填，只保留最近一次操作。
 * 前台窗口 6 秒、后台暂停；进程重开后 10 分钟内重新给满窗口。
 * 到期只隐藏内存条目，落盘快照保留给短期重开；主动清理则严格作废落盘数据。
 */
class UndoRepository(
    private val settings: SettingsRepository,
    private val events: EventRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _pending = MutableStateFlow<UndoOperation?>(null)
    val pending: StateFlow<UndoOperation?> = _pending.asStateFlow()

    // 锁顺序始终为事件操作锁 → 撤销持久化锁。生命周期同步状态不跨挂起点持锁。
    private val persistenceMutex = Mutex()
    private val stateLock = Any()
    private var expiryJob: Job? = null
    private var foreground = true
    private var pausedAtMillis: Long? = null

    suspend fun restore() = events.withMutation {
        persistenceMutex.withLock {
            val raw = settings.stringOnce(KEY_PENDING)
            val saved = runCatching { UndoCodec.decode(raw) }.getOrNull()
            if (saved == null || clock() - saved.pushedAtMillis > MAX_RESTORE_AGE_MILLIS) {
                clearLocked()
            } else {
                val refreshed = saved.withDeadline(clock() + UndoOperation.WINDOW_MILLIS)
                persistBestEffort(refreshed)
                synchronized(stateLock) { install(refreshed) }
            }
        }
    }

    /** 调用方把写库与 push 放在 events.withMutation 内，避免迟到快照跨过导入边界。 */
    suspend fun push(operation: UndoOperation) = persistenceMutex.withLock {
        withContext(NonCancellable) {
            try {
                persist(operation)
            } finally {
                // 已落库的操作即使偏好写入失败，本进程仍有撤销机会。
                synchronized(stateLock) { install(operation) }
            }
        }
    }

    fun pauseWindow() = synchronized(stateLock) {
        foreground = false
        if (pausedAtMillis == null && _pending.value != null) pausedAtMillis = clock()
        expiryJob?.cancel()
        expiryJob = null
    }

    suspend fun resumeWindow() = persistenceMutex.withLock {
        val refreshed = synchronized(stateLock) {
            foreground = true
            val pausedAt = pausedAtMillis
            pausedAtMillis = null
            val operation = _pending.value
            if (pausedAt == null || operation == null) null
            else operation.withDeadline(clock() + operation.remainingMillis(pausedAt))
        } ?: return@withLock
        if (refreshed.isExpired(clock())) {
            clearLocked()
        } else {
            persistBestEffort(refreshed)
            synchronized(stateLock) { install(refreshed) }
        }
    }

    suspend fun undo(): Boolean = events.withMutation {
        persistenceMutex.withLock {
            val operation = synchronized(stateLock) { _pending.value } ?: return@withLock false
            val effectiveNow = synchronized(stateLock) { pausedAtMillis } ?: clock()
            if (operation.isExpired(effectiveNow)) {
                clearLocked()
                false
            } else {
                withContext(NonCancellable) {
                    events.restore(operation.snapshot)
                    clearLocked()
                }
                true
            }
        }
    }

    /** 清理失败必须向上传播；导入/彻底清空不能带着旧快照继续写库。 */
    suspend fun clear() = persistenceMutex.withLock { clearLocked() }

    private suspend fun clearLocked() {
        withContext(NonCancellable) {
            settings.removeKeys(listOf(KEY_PENDING, KEY_PAUSED_AT))
            synchronized(stateLock) {
                expiryJob?.cancel()
                expiryJob = null
                pausedAtMillis = null
                _pending.value = null
            }
        }
    }

    /** 只在 stateLock 内调用。 */
    private fun install(operation: UndoOperation) {
        expiryJob?.cancel()
        _pending.value = operation
        pausedAtMillis = if (foreground) null else clock()
        expiryJob = if (!foreground) null else scope.launch {
            delay(operation.remainingMillis(clock()).coerceAtLeast(0L))
            synchronized(stateLock) {
                // 同 id 的续期也是不同对象；旧到期任务不得清掉新窗口。
                if (foreground && _pending.value === operation) _pending.value = null
            }
        }
    }

    private suspend fun persist(operation: UndoOperation) =
        settings.putString(KEY_PENDING, UndoCodec.encode(operation))

    private suspend fun persistBestEffort(operation: UndoOperation) {
        try {
            persist(operation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "续期撤销窗口落盘失败", error)
        }
    }

    companion object {
        private const val TAG = "MorrowUndo"
        const val MAX_RESTORE_AGE_MILLIS = 10 * 60 * 1000L
        const val KEY_PENDING = "undo.pending_v1"
        private const val KEY_PAUSED_AT = "undo.paused_at_v1"
    }
}
