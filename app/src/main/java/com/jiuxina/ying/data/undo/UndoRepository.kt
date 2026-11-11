package com.jiuxina.ying.data.undo

import android.util.Log
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.domain.model.UndoOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 撤销队列（M2c）。
 *
 * 语义：
 * - 每个操作**立即落库**，同时把受影响事件的完整快照存进内存 + DataStore；
 * - [undo] 用快照整批 upsert 回填，删除/状态切换共用一条回滚路径；
 * - 只保留**最近一次**操作（新操作顶替旧的），旧操作的效果已落库，不再可撤销；
 * - [UndoOperation.expiresAtMillis] 随快照一起持久化，进程被杀后重启仍按剩余时间提供撤销；
 * - 窗口按**前台可见时间**计：离开前台 [pauseWindow] 冻结、回来 [resumeWindow] 补回；
 *   冻结时刻本身也落盘，所以「删完切后台 → 从最近任务划掉 → 重开」同样能接着撤销。
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
     */
    private var pausedAtMillis: Long? = null

    /**
     * 启动恢复：仅当窗口未过期时才恢复撤销条。
     *
     * 过期或内容损坏一律清掉持久化，避免用户下次启动看到一条永远点不动的"已失效撤销"。
     *
     * 若上次是**在后台被杀**，[KEY_PAUSED_AT] 里留着冻结时刻，这里把「冻结到进程死亡」
     * 这段时长补回到期时刻——用户从最近任务划掉应用再点图标重开，撤销条不该因此消失。
     */
    suspend fun restore() {
        pausedAtMillis = null
        val saved = runCatching { UndoCodec.decode(settings.rawString(KEY_PENDING).first()) }.getOrNull()
        if (saved == null) {
            clearPersistedOnly()
            return
        }

        val pausedAt = runCatching { settings.stringOnce(KEY_PAUSED_AT) }.getOrNull()?.toLongOrNull()
        val frozenMillis = if (pausedAt != null) (clock() - pausedAt).coerceAtLeast(0L) else 0L
        if (frozenMillis > MAX_FROZEN_MILLIS) {
            // 离开太久（隔夜、跨天）：一条早就没意义的撤销条弹出来只会让人困惑
            clear()
            return
        }

        val restored =
            if (frozenMillis > 0L) saved.copy(expiresAtMillis = saved.expiresAtMillis + frozenMillis) else saved
        if (restored.isExpired(clock())) {
            clear()
            return
        }

        _pending.value = restored
        if (frozenMillis > 0L) {
            runCatching { persist(restored) }.onFailure { Log.w(TAG, "重启顺延撤销窗口落盘失败", it) }
        }
        clearPausedAtPersisted()
        scheduleExpiry(restored)
    }

    /** 记录一次新操作（替换旧的待撤销项）并开始计时。 */
    suspend fun push(operation: UndoOperation) {
        pausedAtMillis = null
        _pending.value = operation
        persist(operation)
        // 上一轮的冻结标记不该跟着新操作走，否则重启后会把别人的后台时长算进来
        clearPausedAtPersisted()
        scheduleExpiry(operation)
    }

    /**
     * 应用离开前台：冻结倒计时，后台时间不计入撤销窗口。
     *
     * 冻结时刻要落盘：用户很可能（尤其国产 ROM 上）直接从最近任务把应用划掉，
     * 那是进程终止而非"回到后台"，只留内存值的话，重开又变成按墙钟判过期。
     */
    suspend fun pauseWindow() {
        if (pausedAtMillis != null || _pending.value == null) return
        val at = clock()
        pausedAtMillis = at
        expiryJob?.cancel()
        expiryJob = null
        // NonCancellable：调用方是 onStop 起的协程，紧接着可能就 onDestroy（划掉最近任务），
        // 普通协程会在写盘路上被取消，冻结时刻就丢了
        withContext(NonCancellable) {
            runCatching { settings.putString(KEY_PAUSED_AT, at.toString()) }
                .onFailure { Log.w(TAG, "冻结撤销窗口落盘失败", it) }
        }
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
        clearPausedAtPersisted()

        val operation = _pending.value ?: return
        val frozenMillis = (clock() - pausedAt).coerceAtLeast(0L)
        if (frozenMillis > MAX_FROZEN_MILLIS) {
            clear()
            return
        }
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
        clearPausedAtPersisted()
    }

    private suspend fun clearPausedAtPersisted() {
        runCatching { settings.remove(KEY_PAUSED_AT) }
    }

    companion object {
        private const val TAG = "YingUndo"

        /**
         * 冻结最多保留 10 分钟。
         *
         * 允许「切出去看一眼再回来」和「划掉卡片马上重开」，但隔夜回来还弹一条撤销没有意义：
         * 那时的删除早已成了用户认知里的事实，给他一条 4 秒的撤销反而像 bug。
         */
        const val MAX_FROZEN_MILLIS = 10 * 60 * 1000L

        /** 新键；旧版无撤销持久化需求，无迁移问题。 */
        const val KEY_PENDING = "undo.pending_v1"

        /** 冻结起点（毫秒时间戳的字符串形式）。与 [KEY_PENDING] 分开存，撤销项本身不必感知它。 */
        const val KEY_PAUSED_AT = "undo.paused_at_v1"
    }
}
