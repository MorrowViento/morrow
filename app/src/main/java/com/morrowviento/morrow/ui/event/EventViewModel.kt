package com.morrowviento.morrow.ui.event

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.reminder.ReminderScheduler
import com.morrowviento.morrow.data.settings.LegacySettingsKeys
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.undo.UndoRepository
import com.morrowviento.morrow.data.widget.WidgetRefreshTrigger
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.SortMode
import com.morrowviento.morrow.domain.model.UndoKind
import com.morrowviento.morrow.domain.model.UndoOperation
import com.morrowviento.morrow.ui.calendar.CalendarNav
import com.morrowviento.morrow.ui.calendar.CalendarPresentation
import com.morrowviento.morrow.ui.calendar.CalendarStrip
import com.morrowviento.morrow.ui.calendar.CalendarUiState
import com.morrowviento.morrow.ui.calendar.CalendarView
import com.morrowviento.morrow.ui.calendar.TimelineLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/**
 * 首页 ViewModel。
 *
 * 数据链路：Room Flow + 排序偏好 + 折叠状态 + 待撤销项 + 每秒时间源 → 一次性映射成 [HomeUiState]。
 * 「秒级刷新」用时间源驱动重算，而不是让每个卡片各自起协程：卡顿面更小，
 * 且跨午夜时天数会自动翻转（时间源本身就变了）；撤销条倒计时同时复用这个源。
 */
@OptIn(FlowPreview::class)
class EventViewModel(
    private val events: EventRepository,
    private val settings: SettingsRepository,
    private val undo: UndoRepository,
    private val reminders: ReminderScheduler,
    private val widgetRefresh: WidgetRefreshTrigger = WidgetRefreshTrigger.NONE,
    private val cancelNotification: (String) -> Unit = {},
) : ViewModel() {

    private val completedExpanded = MutableStateFlow(false)
    private val operationError = MutableStateFlow<String?>(null)

    private val sharedEvents = events.observeAll()
        .map { Result.success(it) }
        .catch { emit(Result.failure(it)) }
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /**
     * 数据一变就刷小部件。
     *
     * 挂在 `observeAll()` 上而不是逐个 save/delete/undo 里调用：撤销、批量清理、表单保存
     * 最终都会落到这张表，一处订阅就不会漏掉后来新增的写路径。`drop(1)` 跳过首次快照
     * （那时小部件刚由 onUpdate 刷过），`debounce` 把「删除 + 撤销」这种连击合并成一次。
     */
    init {
        viewModelScope.launch {
            sharedEvents
                .filter { it.isSuccess }
                .map { it.getOrThrow() }
                .drop(1)
                .debounce(300)
                .collect {
                    // 拼 RemoteViews 会走 Binder IPC，别占主线程。
                    // 再包一层：viewModelScope 没有异常处理器，这里抛出去会直达默认
                    // UncaughtExceptionHandler 直接杀进程——真机上「保存后退回桌面」就是这样，
                    // 数据已落库所以重开还在。小部件坏了不该拖累保存。
                    runCatching { withContext(Dispatchers.IO) { widgetRefresh.refreshAll() } }
                        .onFailure { Log.w(TAG, "小部件刷新失败，已忽略", it) }
                }
        }
    }

    /**
     * 每秒一发的时间源；只在有订阅者时跑（见 stateIn 的 WhileSubscribed）。
     *
     * 保守地保持 1 秒：真正让这条路变轻的是下面的 [CardsSnapshot]——
     * 同一秒里没有实质变化时它整批复用旧实例，于是既没有对象分配，
     * `HomeUiState` 也与上一帧相等、StateFlow 不再向下游发值（Compose 整页跳过）。
     * 换成「闲时 15 秒一拍」看着更省电，代价是刚建完当天事件要等一拍才开始跳秒，
     * 反而像个卡住的界面。
     */
    private val ticker: Flow<LocalDateTime> = flow {
        while (currentCoroutineContext().isActive) {
            emit(LocalDateTime.now())
            delay(1_000L)
        }
    }

    /**
     * 上一次算出的卡片列表。`events` 用**引用**比较：Room 每次查询都会给出新 List，
     * 只要还是同一个实例，内容就没变，可以整批复用。
     *
     * 复用时推进今日秒数与量化进度；只有字段真正变化的卡片才复制。
     */
    private class CardsSnapshot(
        val events: List<CountdownEvent>,
        val mode: SortMode,
        val today: LocalDate,
        val active: List<EventCardModel>,
        val completed: List<EventCardModel>,
        val archived: List<EventCardModel>,
    )

    private var snapshot: CardsSnapshot? = null

    val state: StateFlow<HomeUiState> = combine(
        sharedEvents,
        settings.sortMode,
        completedExpanded,
        undo.pending,
        ticker,
    ) { result, sortPref, expanded, pending, now ->
        val list = result.getOrThrow()
        val mode = SortMode.fromPref(sortPref)
        val today = now.toLocalDate()
        val nowMillis = System.currentTimeMillis()

        val cached = snapshot
        val cards = if (cached != null && cached.events === list && cached.mode == mode && cached.today == today) {
            // 同一份数据、同一排序、同一天：只把「今日剩余」推进一秒。
            // 两份列表都没变时连快照对象也不新建（一秒一个对象的垃圾也省了）。
            val active = EventPresentation.refreshClock(cached.active, today, now)
            val completed = EventPresentation.refreshClock(cached.completed, today, now)
            // 归档列表不参与秒级刷新：归档箱里不显示倒计时，原样复用即可
            if (active === cached.active && completed === cached.completed) {
                cached
            } else {
                CardsSnapshot(list, mode, today, active, completed, cached.archived)
            }
        } else {
            // 一次分区同时拿到三段：归档的事件本来就不该进「进行中 / 已完成」
            val parts = EventPresentation.partition(list, mode, today, now)
            CardsSnapshot(list, mode, today, parts.active, parts.completed, parts.archived)
        }
        snapshot = cards

        val undoState = pending
            ?.takeIf { !it.isExpired(nowMillis) }
            ?.let { UndoUiState(message = it.message, remainingSeconds = it.remainingSeconds(nowMillis)) }

        HomeUiState(
            active = cards.active,
            completed = cards.completed,
            archived = cards.archived,
            completedExpanded = expanded,
            sortMode = mode,
            isLoading = false,
            undo = undoState,
        )
    }
        .catch { emit(HomeUiState(isLoading = false, error = it.message ?: "读取事件失败")) }
        .combine(operationError) { state, error -> if (error == null) state else state.copy(error = error) }
        // 映射 + 排序一律离开主线程：每秒一次的重算即使只有几十微秒，
        // 也不该和绘制挤在同一帧里（stateIn 默认在主线程上游收集）
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggleCompletedSection() {
        completedExpanded.value = !completedExpanded.value
    }

    // ---- M2d：日历三视图（与首页共用同一份 Room 数据流）----

    private val calendarNav = MutableStateFlow(CalendarNav.initial(LocalDate.now()))

    /**
     * 日历只需按天刷新：跨午夜后 today 变化，格子标记、重复事件落点与天数差一并重算。
     * 刻意不复用每秒时间源——月视图 42 格每秒重组一次毫无收益。
     */
    private val dayTick: Flow<LocalDate> = flow {
        while (currentCoroutineContext().isActive) {
            emit(LocalDate.now())
            delay(30_000L)
        }
    }.distinctUntilChanged()

    /**
     * 当前页（相邻页惰性）：界面只用 `canPrev` / `canNext`，不再需要预先算两页。
     *
     * 只有一个 Flow，日历不额外订阅 Room——否则 events.observeAll() 会被 collect 多次。
     * 42 格的农历换算与事件落点放到后台线程算，翻月时不会卡住手势。
     */
    val calendarStrip: StateFlow<CalendarStrip> = combine(
        sharedEvents,
        calendarNav,
        dayTick,
    ) { result, nav, today ->
        // 归档的事件不画在日历上：归档是「从日常视线里拿走」，日历属于日常视线。
        // 归档箱里仍能看到它们（那边读的是首页状态里的 archived 段）。
        CalendarPresentation.strip(result.getOrThrow().filter { !it.isArchived }, nav, today)
    }
        .catch { emit(CalendarStrip.shell(CalendarUiState(isLoading = false, error = it.message ?: "读取事件失败"))) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarStrip.shell(CalendarUiState(isLoading = true)),
        )

    fun setCalendarView(view: CalendarView) {
        calendarNav.update { it.copy(view = view) }
    }

    /** 月视图翻月。选区一并清空，否则会停在一个看不见的日期上。 */
    fun shiftCalendarMonth(delta: Long) {
        calendarNav.update {
            it.copy(month = CalendarNav.clamp(it.month.plusMonths(delta)), selected = null)
        }
    }

    /** 年视图/列表视图翻年。 */
    fun shiftCalendarYear(delta: Long) {
        calendarNav.update {
            it.copy(month = CalendarNav.clamp(it.month.plusYears(delta)), selected = null)
        }
    }

    /** 再点一次同一天视为取消选中。 */
    fun selectCalendarDay(date: LocalDate) {
        calendarNav.update { it.copy(selected = if (it.selected == date) null else date) }
    }

    fun openCalendarMonth(month: YearMonth) {
        calendarNav.update {
            it.copy(view = CalendarView.MONTH, month = CalendarNav.clamp(month), selected = null)
        }
    }

    fun calendarGoToToday() {
        val today = LocalDate.now()
        calendarNav.update { it.copy(month = CalendarNav.clamp(YearMonth.from(today)), selected = today) }
    }

    /** 时间块视图翻日：选区即当前显示日，翻到哪天就看哪天。 */
    fun shiftCalendarDay(delta: Long) {
        calendarNav.update { nav ->
            val base = nav.selected ?: LocalDate.now()
            val day = CalendarNav.clampDay(base.plusDays(delta))
            nav.copy(selected = day, month = YearMonth.from(day))
        }
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
        if (!form.validate().isValid) return
        mutate {
            val existing = form.id?.let { events.findById(it) }
            if (form.id != null && existing == null) return@mutate
            val event = form.toEvent(existing) ?: return@mutate
            undo.clear()
            events.save(event)
            // sync 是幂等的「先清后设」：改提醒、改日期、取消提醒都由它一次收敛
            withContext(Dispatchers.IO) {
                cancelNotification(event.id)
                reminders.cancel(event.id)
                reminders.sync(event)
            }
        }
    }

    // ---- M2c：详情页操作（全部进入 6 秒撤销队列）----

    fun deleteEvent(id: String) {
        mutate {
            val event = events.findById(id) ?: return@mutate
            undo.clear()
            events.delete(id)
            pushUndo(UndoKind.DELETE, "已删除「${event.title}」", listOf(event))
            withContext(Dispatchers.IO) {
                cancelNotification(id)
                reminders.cancel(id)
            }
        }
    }

    fun toggleEventCompleted(id: String) {
        val expected = (state.value.active + state.value.completed).firstOrNull { it.id == id } ?: return
        mutate {
            val event = events.findById(id) ?: return@mutate
            if (event.isCompleted != expected.isCompleted || event.primaryAction != expected.primaryAction) return@mutate
            val updated = com.morrowviento.morrow.domain.time.RepeatRule.applyPrimaryAction(
                event, LocalDate.now(), expected.target.toLocalDate(),
            ) ?: return@mutate
            undo.clear()
            events.save(updated)
            pushUndo(
                kind = UndoKind.TOGGLE_COMPLETED,
                message = when (event.primaryAction) {
                    com.morrowviento.morrow.domain.model.EventPrimaryAction.NEXT_MONTH -> "「${event.title}」已进入下一月"
                    com.morrowviento.morrow.domain.model.EventPrimaryAction.NEXT_YEAR -> "「${event.title}」已进入下一年"
                    com.morrowviento.morrow.domain.model.EventPrimaryAction.RESTORE -> "「${event.title}」已恢复为进行中"
                    else -> "「${event.title}」已完成"
                },
                snapshot = listOf(event),
            )
            withContext(Dispatchers.IO) {
                cancelNotification(id)
                reminders.cancel(id)
                reminders.sync(updated)
            }
        }
    }

    /**
     * 时间块视图拖底边改时长。
     *
     * 刻意不进撤销队列：这是连续手势的终点，一次拖动理应只有一个结果，
     * 若每松手一次就压一条撤销，撤销条会被「恢复 45 分钟」这类噪声塞满。
     * 时长只影响块的观感，不改提醒与倒计时语义，因此也不需要重排闹钟。
     */
    fun setDuration(id: String, minutes: Int) {
        val safe = minutes.coerceIn(
            TimelineLayout.MIN_BLOCK_MINUTES,
            CountdownEvent.MAX_DURATION_MINUTES,
        )
        mutate {
            val event = events.findById(id) ?: return@mutate
            if (event.durationMinutes == safe) return@mutate
            events.save(event.copy(durationMinutes = safe))
        }
    }

    /**
     * 勾选 / 取消一条子任务。
     *
     * 只动 checklist 这一个字段：不改 `isCompleted`、不动提醒、不重排闹钟。
     * 子任务是「步骤」，一件事做完了仍旧由「标记完成」显式确认；反过来，
     * 全部勾完也不该在用户没点的情况下把事件挪进「已完成」区。
     */
    fun toggleChecklistItem(eventId: String, itemId: String) {
        mutate {
            val event = events.findById(eventId) ?: return@mutate
            val item = event.checklist.firstOrNull { it.id == itemId } ?: return@mutate
            val updated = event.copy(
                checklist = event.checklist.map {
                    if (it.id == itemId) it.copy(done = !it.done) else it
                },
            )
            undo.clear()
            events.save(updated)
            pushUndo(
                kind = UndoKind.TOGGLE_CHECKLIST,
                message = "「${item.title}」${if (item.done) "已取消勾选" else "已勾选"}",
                snapshot = listOf(event),
            )
        }
    }

    fun togglePinned(id: String) {
        mutate {
            val event = events.findById(id) ?: return@mutate
            undo.clear()
            events.save(event.copy(isPinned = !event.isPinned))
            pushUndo(
                kind = UndoKind.TOGGLE_PINNED,
                message = if (event.isPinned) "已取消置顶「${event.title}」" else "已置顶「${event.title}」",
                snapshot = listOf(event),
            )
        }
    }

    fun clearCompleted() {
        mutate {
            // 排除已归档：归档箱有自己的「清空」，不该被这里顺手删掉
            val done = events.loadAll().filter { it.isCompleted && !it.isArchived }
            if (done.isEmpty()) return@mutate
            undo.clear()
            events.deleteMany(done.map { it.id })
            pushUndo(UndoKind.CLEAR_COMPLETED, "已清理 ${done.size} 个已完成事件", done)
            withContext(Dispatchers.IO) { done.forEach { cancelNotification(it.id); reminders.cancel(it.id) } }
        }
    }

    // ---- 归档（M2f）：与「完成」分开的一条状态线 ----

    /**
     * 归档 / 取消归档。
     *
     * 归档只把 [CountdownEvent.archivedAt] 写上，**不动** `isCompleted`：
     * 用户放弃一件做过一半的事，不该被记成「完成」；把一件已完成的事收进抽屉，
     * 也不该丢掉它那一次「完成」的记录。两件事互不覆盖。
     */
    fun setArchived(id: String, archived: Boolean) {
        mutate {
            val event = events.findById(id) ?: return@mutate
            if (event.isArchived == archived) return@mutate
            val updated = event.copy(
                archivedAt = if (archived) LocalDateTime.now() else null,
            )
            undo.clear()
            events.save(updated)
            pushUndo(
                kind = UndoKind.TOGGLE_ARCHIVED,
                message = if (archived) "已归档「${event.title}」" else "已恢复到列表「${event.title}」",
                snapshot = listOf(event),
            )
            withContext(Dispatchers.IO) {
                cancelNotification(id)
                reminders.cancel(id)
                // 归档期间不排提醒；恢复时按最新状态重新收敛一遍
                if (!archived) reminders.sync(updated)
            }
        }
    }

    /** 清空归档箱：真的删除，进撤销队列。 */
    fun clearArchived() {
        mutate {
            val archived = events.loadAll().filter { it.isArchived }
            if (archived.isEmpty()) return@mutate
            undo.clear()
            events.deleteMany(archived.map { it.id })
            pushUndo(UndoKind.CLEAR_ARCHIVED, "已清空归档箱（${archived.size} 个事件）", archived)
            withContext(Dispatchers.IO) {
                archived.forEach { cancelNotification(it.id); reminders.cancel(it.id) }
            }
        }
    }

    fun clearAll() {
        mutate {
            val all = events.loadAll()
            if (all.isEmpty()) return@mutate
            undo.clear()
            events.clearAll()
            pushUndo(UndoKind.CLEAR_ALL, "已清空 ${all.size} 个事件", all)
            withContext(Dispatchers.IO) { all.forEach { cancelNotification(it.id); reminders.cancel(it.id) } }
        }
    }

    /** 撤销最近一次操作；窗口过期或无事可撤时静默忽略（撤销条本身已消失）。 */
    fun undoLast() {
        launchOperation {
            if (undo.undo()) {
                // 与其它写入串行化，避免旧对账在删除后重新排入提醒。
                events.withMutation {
                    withContext(Dispatchers.IO) { reminders.syncAll(events.loadAll()) }
                }
            }
        }
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

    private fun mutate(block: suspend () -> Unit) = launchOperation {
        events.withMutation {
            // 一旦开始写库，快照记录必须完成，不能因页面退出只留下半个操作。
            withContext(kotlinx.coroutines.NonCancellable) { block() }
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operationError.value = null
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "事件操作失败", error)
                operationError.value = error.message ?: "事件操作失败"
            }
        }
    }

    companion object {
        private const val TAG = "MorrowEventVM"


        fun factory(
            events: EventRepository,
            settings: SettingsRepository,
            undo: UndoRepository,
            reminders: ReminderScheduler,
            widgetRefresh: WidgetRefreshTrigger,
            cancelNotification: (String) -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EventViewModel(events, settings, undo, reminders, widgetRefresh, cancelNotification) as T
        }
    }
}
