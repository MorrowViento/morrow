package com.jiuxina.ying.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.settings.LegacySettingsKeys
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.domain.model.SortMode
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

/**
 * 首页 ViewModel。
 *
 * 数据链路：Room Flow + 设置里的排序偏好 + 每秒时间源 → 一次性映射成 [HomeUiState]。
 * 「秒级刷新」用时间源驱动重算，而不是让每个卡片各自起协程：卡顿面更小，
 * 且跨午夜时天数会自动翻转（时间源本身就变了）。
 */
class EventViewModel(
    private val events: EventRepository,
    private val settings: SettingsRepository,
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
        ticker,
    ) { list, sortPref, expanded, now ->
        val mode = SortMode.fromPref(sortPref)
        val today = now.toLocalDate()
        val (active, completed) = EventPresentation.partition(list, mode, today, now)
        HomeUiState(
            active = active,
            completed = completed,
            completedExpanded = expanded,
            sortMode = mode,
            isLoading = false,
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

    /** 表单保存：校验在 [EventFormState.toEvent] 内完成，非法直接丢弃。 */
    fun save(form: EventFormState) {
        val event = form.toEvent() ?: return
        viewModelScope.launch { events.save(event) }
    }

    companion object {
        fun factory(events: EventRepository, settings: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EventViewModel(events, settings) as T
            }
    }
}
