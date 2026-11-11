package com.jiuxina.ying.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.settings.LegacySettingsKeys
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.data.undo.UndoRepository
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.SortMode
import com.jiuxina.ying.domain.model.UndoKind
import com.jiuxina.ying.domain.model.UndoOperation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID

/**
 * 首页 ViewModel。
 *
 * 数据链路：Room Flow + 排序偏好 + 折叠状态 + 待撤销项 + 每秒时间源 → 一次性映射成 [HomeUiState]。
 * 「秒级刷新」用时间源驱动重算，而不是让每个卡片各自起协程：卡顿面更小，
 * 且跨午夜时天数会自动翻转（时间源本身就变了）；撤销条倒计时同时复用这个源。
 */
class EventViewModel(
    private val events: EventRepository,
    private val settings: SettingsRepository,
    private val undo: UndoRepository,
) : ViewModel() {

    private val completedExpanded = MutableStateFlow(false)

    /** 每秒一发的时间源；只在有订阅者时跑（见 stateIn 的 WhileSubscribed）。 */
    private val ticker: Flow<LocalDateTime> = flow {
        while (currentCoroutineContext().isActive) {
            emit(LocalDateTime.now())
            delay(1_000L)
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        events.observeAll(),
        settings.sortMode,
        completedExpanded,
        undo.pending,
        ticker,
    ) { list, sortPref, expanded, pending, now ->
        val mode = SortMode.fromPref(sortPref)
        val today = now.toLocalDate()
        val (active, completed) = EventPresentation.partition(list, mode, today, now)
        val nowMillis = System.currentTimeMillis()
        HomeUiState(
            active = active,
            completed = completed,
            completedExpanded = expanded,
            sortMode = mode,
            isLoading = false,
            undo = pending
                ?.takeIf { !it.isExpired(nowMillis) }
                ?.let { UndoUiState(message = it.message, remainingSeconds = it.remainingSeconds(nowMillis)) },
        )
    }
        .catch { emit(HomeUiState(isLoading = false, error = it.message ?: "读取事件失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggleCompletedSection() {
        completedExpanded.value = !completedExpanded.value
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            settings.putString(LegacySettingsKeys.EVENT_SORT_MODE, mode.prefValue)
        }
    }

    /**
     * 表单保存：校验在 [EventFormState.toEvent] 内完成，非法直接丢弃。
     *
     * 保存不进撤销队列——它是用户主动确认的动作，且保存后可用「编辑」改回；
     * 撤销只服务于删除与批量清理这类难以手动复原的操作。
     */
    fun save(form: EventFormState) {
        val event = form.toEvent() ?: return
        viewModelScope.launch { events.save(event) }
    }

    // ---- M2c：详情页操作（全部进入 6 秒撤销队列）----

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            val event = events.findById(id) ?: return@launch
            events.delete(id)
            pushUndo(UndoKind.DELETE, "已删除「${event.title}」", listOf(event))
        }
    }

    fun toggleEventCompleted(id: String) {
        viewModelScope.launch {
            val event = events.findById(id) ?: return@launch
            events.save(event.copy(isCompleted = !event.isCompleted))
            pushUndo(
                kind = UndoKind.TOGGLE_COMPLETED,
                message = if (event.isCompleted) {
                    "「${event.title}」已恢复为进行中"
                } else {
                    "「${event.title}」已完成"
                },
                snapshot = listOf(event),
            )
        }
    }

    fun togglePinned(id: String) {
        viewModelScope.launch {
            val event = events.findById(id) ?: return@launch
            events.save(event.copy(isPinned = !event.isPinned))
            pushUndo(
                kind = UndoKind.TOGGLE_PINNED,
                message = if (event.isPinned) "已取消置顶「${event.title}」" else "已置顶「${event.title}」",
                snapshot = listOf(event),
            )
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            val done = events.loadAll().filter { it.isCompleted }
            if (done.isEmpty()) return@launch
            events.deleteMany(done.map { it.id })
            pushUndo(UndoKind.CLEAR_COMPLETED, "已清理 ${done.size} 个已完成事件", done)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val all = events.loadAll()
            if (all.isEmpty()) return@launch
            events.clearAll()
            pushUndo(UndoKind.CLEAR_ALL, "已清空 ${all.size} 个事件", all)
        }
    }

    /** 撤销最近一次操作；窗口过期或无事可撤时静默忽略（撤销条本身已消失）。 */
    fun undoLast() {
        viewModelScope.launch { undo.undo() }
    }

    /** 详情页点「编辑」时取完整事件用于回填表单。 */
    suspend fun formFor(id: String): EventFormState? =
        events.findById(id)?.let { EventFormState.fromEvent(it) }

    private suspend fun pushUndo(kind: UndoKind, message: String, snapshot: List<CountdownEvent>) {
        undo.push(
            UndoOperation(
                id = UUID.randomUUID().toString(),
                kind = kind,
                message = message,
                snapshot = snapshot,
                expiresAtMillis = System.currentTimeMillis() + UndoOperation.WINDOW_MILLIS,
            ),
        )
    }

    companion object {
        fun factory(
            events: EventRepository,
            settings: SettingsRepository,
            undo: UndoRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EventViewModel(events, settings, undo) as T
        }
    }
}
