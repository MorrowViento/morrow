package com.morrowviento.morrow.ui.calendar

import androidx.compose.runtime.Immutable
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.time.CalendarMath
import com.morrowviento.morrow.domain.time.LunarCalendar
import java.time.LocalDate
import java.time.YearMonth

/** 日历四个视图。[label] 用于分段控件的按钮文字。 */
enum class CalendarView(val label: String) {
    MONTH("月"),
    YEAR("年"),
    LIST("列表"),
    /** 时间块：一天 24 小时的纵向轴，事件按时刻落成块（重叠自动分列）。 */
    TIMELINE("时间块"),
}

/**
 * 日历的浏览位置。
 *
 * 三种视图共用一个 [month] 作为锚点：切到年视图看的是该年，切到列表看的是该年，
 * 点年视图里的某个月再回到月视图也只需改 [month]。避免为每个视图各存一个位置后互不同步。
 */
@Immutable
data class CalendarNav(
    val view: CalendarView = CalendarView.MONTH,
    val month: YearMonth,
    val selected: LocalDate? = null,
) {
    val year: Int get() = month.year

    companion object {
        fun initial(today: LocalDate): CalendarNav =
            CalendarNav(view = CalendarView.MONTH, month = YearMonth.from(today), selected = today)

        /** 农历年表范围；超出后格子没有副标题，故直接夹住，防止用户翻到无数据区。 */
        private val MIN_MONTH = YearMonth.of(LunarCalendar.MIN_YEAR, 1)
        private val MAX_MONTH = YearMonth.of(LunarCalendar.MAX_YEAR, 12)

        fun clamp(month: YearMonth): YearMonth = when {
            month < MIN_MONTH -> MIN_MONTH
            month > MAX_MONTH -> MAX_MONTH
            else -> month
        }

        /** 时间块视图翻日也要夹在农历年表内，否则会滚到没有农历副标题的区域。 */
        fun clampDay(date: LocalDate): LocalDate = when {
            date < MIN_MONTH.atDay(1) -> MIN_MONTH.atDay(1)
            date > MAX_MONTH.atEndOfMonth() -> MAX_MONTH.atEndOfMonth()
            else -> date
        }
    }
}

/** 月视图的一格。 */
@Immutable
data class CalendarCell(
    val date: LocalDate,
    /** 是否属于当前显示月份；相邻月的补位格为 false。 */
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** 农历副标题；超出年表范围为空串。 */
    val lunarLabel: String,
    val isFestival: Boolean,
    val eventCount: Int,
)

/**
 * 日历条目。刻意不复用 [com.morrowviento.morrow.ui.event.EventCardModel]：
 * 卡片带「今日剩余时分秒」「进度」这类每秒变化且依赖 now 的字段，
 * 放进按天刷新的日历状态里会立即变味。日历行只需要「那一天是哪天、差几天」。
 */
@Immutable
data class CalendarEntry(
    val date: LocalDate,
    val id: String,
    val title: String,
    val category: String,
    /** 该日期相对今天的天数：0 = 今天，负 = 已过去。 */
    val dayDelta: Int,
    val isCompleted: Boolean,
    val isAllDay: Boolean,
    val repeatType: RepeatType,
    /** 重复的人话描述（「每周」「每年·农历」「每 2 周 周一、周三」）。 */
    val repeatLabel: String = "",
    /** 持续事件的跨度（「共 5 天」），单日事件为 null。 */
    val spanLabel: String? = null,
)

/** 年视图的一个月。 */
@Immutable
data class MonthSummary(
    val month: YearMonth,
    val eventCount: Int,
    /** 小日历只含公历与标记，不为十二个月预计算农历。 */
    val cells: List<CalendarCell>,
    /** 最多三条日期/标题摘要，同一天多事件各自计数。 */
    val sampleEntries: List<CalendarEntry>,
)

/** 列表视图的一个月分组。 */
@Immutable
data class MonthGroup(
    val month: YearMonth,
    val entries: List<CalendarEntry>,
)

@Immutable
data class CalendarUiState(
    val view: CalendarView = CalendarView.MONTH,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate? = null,
    /** 固定 42 格（6 行 × 7 列），避免月份切换时行数跳动。 */
    val cells: List<CalendarCell> = emptyList(),
    val selectedEntries: List<CalendarEntry> = emptyList(),
    /** 当前显示月份的落点总数（同一天多个事件分别计数）。 */
    val monthEventCount: Int = 0,
    val monthSummaries: List<MonthSummary> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
    /** 时间块视图的当天布局；其他视图为 null。 */
    val timeline: TimelineDay? = null,
    val yearEventCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** 年视图/列表视图的总数为 0 时用于空态。 */
    val isEmpty: Boolean get() = yearEventCount == 0
}

/**
 * 当前页 + 左右相邻页。
 *
 * 界面通过完整当前快照的 Transition 保留退出页，不读取这里的惰性邻页。
 * 翻页边界只使用 [canPrev] / [canNext]；数据构建仍由 ViewModel 后台执行。
 *
 * 所以这里把相邻页改成**惰性**：`canPrev` / `canNext` 由 [CalendarNav.clamp] 直接判断
 * （纯月份比较），邻页的 42 格、农历副标题只有真去读 `prev` / `next` 时才构建。
 * 之前每次数据变化都要多算两整页——日历一屏的农历换算是它的主要成本。
 *
 * 仍然保留 `prev` / `next` 的实体：将来若重新引入带位移的翻页动画，直接读它们即可。
 */
class CalendarStrip private constructor(
    val current: CalendarUiState,
    private val events: List<CountdownEvent>,
    private val nav: CalendarNav,
    private val today: LocalDate,
    /** 还能不能往左 / 往右翻；到农历年表边界时为 false。 */
    val canPrev: Boolean,
    val canNext: Boolean,
) {
    val prev: CalendarUiState? by lazy(LazyThreadSafetyMode.NONE) {
        neighbor(-1)
    }

    val next: CalendarUiState? by lazy(LazyThreadSafetyMode.NONE) {
        neighbor(1)
    }

    private fun neighbor(delta: Long): CalendarUiState? {
        if ((delta < 0 && !canPrev) || (delta > 0 && !canNext)) return null
        val target = when (nav.view) {
            CalendarView.MONTH -> nav.copy(
                month = CalendarNav.clamp(nav.month.plusMonths(delta)),
                selected = null,
            )
            // 时间块按天翻：选区就是当前显示的那一天，翻页后落在新的一天
            CalendarView.TIMELINE -> {
                val day = CalendarNav.clampDay((nav.selected ?: today).plusDays(delta))
                nav.copy(month = YearMonth.from(day), selected = day)
            }
            else -> nav.copy(
                month = CalendarNav.clamp(nav.month.plusYears(delta)),
                selected = null,
            )
        }
        return CalendarPresentation.build(events, target, today)
    }

    /** 只在当前页上比较：邻页是惰性的，不该为了判等把两页都算出来。 */
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CalendarStrip && other.current == current && other.canPrev == canPrev && other.canNext == canNext)

    override fun hashCode(): Int = 31 * current.hashCode() + (if (canPrev) 1 else 0) * 2 + (if (canNext) 1 else 0)

    override fun toString(): String =
        "CalendarStrip(view=${current.view}, month=${current.month}, canPrev=$canPrev, canNext=$canNext)"

    companion object {
        fun of(
            events: List<CountdownEvent>,
            nav: CalendarNav,
            today: LocalDate,
        ): CalendarStrip = CalendarStrip(
            current = CalendarPresentation.build(events, nav, today),
            events = events,
            nav = nav,
            today = today,
            canPrev = neighborExists(nav, -1, today),
            canNext = neighborExists(nav, 1, today),
        )

        /** 只包一层「还没有数据 / 读取出错」的壳：不建 42 格，也不建邻页。 */
        fun shell(state: CalendarUiState): CalendarStrip = CalendarStrip(
            current = state,
            events = emptyList(),
            nav = CalendarNav.initial(state.today),
            today = state.today,
            canPrev = false,
            canNext = false,
        )

        /** 边界判定与 [CalendarPresentation.build] 无关，只看周期有没有被夹住。 */
        private fun neighborExists(nav: CalendarNav, delta: Long, today: LocalDate): Boolean =
            when (nav.view) {
                CalendarView.MONTH ->
                    CalendarNav.clamp(nav.month.plusMonths(delta)) != nav.month
                CalendarView.TIMELINE -> {
                    val current = nav.selected ?: today
                    CalendarNav.clampDay(current.plusDays(delta)) != current
                }
                else -> CalendarNav.clamp(nav.month.plusYears(delta)) != nav.month
            }
    }
}

/**
 * 日历展示逻辑（纯函数，无 Android 依赖）。
 *
 * 重复事件按「当年周年」落位：月/日取自 `repeatMonth/repeatDay`（缺省取原目标日期），
 * 因此每年都能在日历上看到同一个纪念日，而不是只有「下一次」那一次。
 */
object CalendarPresentation {

    const val CELL_COUNT = 42
    val WEEKDAY_LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 该年首个落点；月/年/列表的完整落点统一由 RepeatRule 生成。 */
    fun occurrenceIn(event: CountdownEvent, year: Int): LocalDate? =
        com.morrowviento.morrow.domain.time.RepeatRule.occurrencesBetween(
            event, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
        ).firstOrNull()

    /** 某月的日期 → 事件列表（同一天多个事件按未完成优先、标题排序）。 */
    fun marksOf(events: List<CountdownEvent>, month: YearMonth): Map<LocalDate, List<CountdownEvent>> =
        collect(events, month) { event, start, end ->
            com.morrowviento.morrow.domain.time.RepeatRule.occurrencesBetween(event, start, end)
        }

    /**
     * 同 [marksOf]，但持续事件会铺满整个区间。
     *
     * 月视图的 42 格与「选中某天」用它：9/1–9/5 的出差，9/3 那格也该亮，
     * 点进去也能看到这条事件。年视图/列表仍按**起点**计一次，否则一个月的出差
     * 会在年摘要里刷出 30 行同一件事。
     */
    fun coveringOf(events: List<CountdownEvent>, month: YearMonth): Map<LocalDate, List<CountdownEvent>> =
        collect(events, month) { event, start, end ->
            com.morrowviento.morrow.domain.time.RepeatRule.coverageBetween(event, start, end)
        }

    private inline fun collect(
        events: List<CountdownEvent>,
        month: YearMonth,
        dates: (CountdownEvent, LocalDate, LocalDate) -> List<LocalDate>,
    ): Map<LocalDate, List<CountdownEvent>> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val out = LinkedHashMap<LocalDate, MutableList<CountdownEvent>>()
        events.forEach { event ->
            dates(event, start, end).forEach { date ->
                out.getOrPut(date) { mutableListOf() }.add(event)
            }
        }
        return out.mapValues { (_, list) -> list.sortedWith(ENTRY_ORDER) }
    }

    /** 已完成的事件同样上日历，由 UI 用弱化色区分，不在这里过滤。 */
    fun build(events: List<CountdownEvent>, nav: CalendarNav, today: LocalDate): CalendarUiState =
        when (nav.view) {
            CalendarView.MONTH -> buildMonth(events, nav, today)
            CalendarView.YEAR -> buildYear(events, nav, today)
            CalendarView.LIST -> buildList(events, nav, today)
            CalendarView.TIMELINE -> buildTimeline(events, nav, today)
        }

    /**
     * 时间块视图：只算「那一天」。
     *
     * 不预先算前后两天——时间块是逐日看的，翻页时算下一页即可，
     * 与月视图「一次只算一页 42 格」同一个取舍。
     */
    private fun buildTimeline(
        events: List<CountdownEvent>,
        nav: CalendarNav,
        today: LocalDate,
    ): CalendarUiState {
        val date = CalendarNav.clampDay(nav.selected ?: today)
        val timeline = TimelineLayout.build(events, date)
        return CalendarUiState(
            view = nav.view,
            month = YearMonth.from(date),
            today = today,
            selected = date,
            timeline = timeline,
            // 头部副标题复用这个字段：当天有几件事（全天条 + 定时块）
            monthEventCount = timeline.allDay.size + timeline.blocks.size,
            isLoading = false,
        )
    }

    /**
     * 当前页（+ 惰性相邻页）。
     *
     * 相邻页只在真被读取时才构建，界面现在只读 [CalendarStrip.canPrev] / [CalendarStrip.canNext]，
     * 所以一次数据变化只算一页 42 格，而不是三页。
     */
    fun strip(events: List<CountdownEvent>, nav: CalendarNav, today: LocalDate): CalendarStrip =
        CalendarStrip.of(events, nav, today)

    private fun buildMonth(
        events: List<CountdownEvent>,
        nav: CalendarNav,
        today: LocalDate,
    ): CalendarUiState {
        val dates = monthCells(nav.month)
        val selected = nav.selected
        // 42 格最多横跨 3 个月，按月算一次再按日期取，避免逐格重复扫描全部事件
        val months = (dates.map { YearMonth.from(it) } + listOfNotNull(selected?.let(YearMonth::from)))
            .distinct()
        val marks = months.associateWith { coveringOf(events, it) }

        val cells = dates.map { date ->
            val thisMonth = YearMonth.from(date)
            // 农历只换算一次：副标题与「是否节日」来自同一份 LunarDate
            val (lunarLabel, isFestival) = LunarCalendar.cell(date)
            CalendarCell(
                date = date,
                inMonth = thisMonth == nav.month,
                isToday = date == today,
                isSelected = date == selected,
                lunarLabel = lunarLabel.orEmpty(),
                isFestival = isFestival,
                eventCount = marks[thisMonth]?.get(date).orEmpty().size,
            )
        }
        val selectedEntries = if (selected == null) {
            emptyList()
        } else {
            marks[YearMonth.from(selected)]?.get(selected).orEmpty()
                .map { entryOf(it, selected, today) }
        }
        return CalendarUiState(
            view = nav.view,
            month = nav.month,
            today = today,
            selected = nav.selected,
            cells = cells,
            selectedEntries = selectedEntries,
            monthEventCount = cells.filter { it.inMonth }.sumOf { it.eventCount },
            isLoading = false,
        )
    }

    private fun buildYear(
        events: List<CountdownEvent>,
        nav: CalendarNav,
        today: LocalDate,
    ): CalendarUiState {
        val summaries = (1..12).map { month ->
            val ym = YearMonth.of(nav.year, month)
            val marks = marksOf(events, ym)
            val entries = marks.toSortedMap().flatMap { (date, list) -> list.map { entryOf(it, date, today) } }
            MonthSummary(
                month = ym,
                eventCount = entries.size,
                cells = monthCells(ym).map { date ->
                    val inMonth = YearMonth.from(date) == ym
                    CalendarCell(date, inMonth, inMonth && date == today, false, "", false,
                        if (inMonth) marks[date].orEmpty().size else 0)
                },
                sampleEntries = entries.take(3),
            )
        }
        return CalendarUiState(
            view = nav.view,
            month = nav.month,
            today = today,
            selected = nav.selected,
            monthSummaries = summaries,
            yearEventCount = summaries.sumOf { it.eventCount },
            isLoading = false,
        )
    }

    private fun buildList(
        events: List<CountdownEvent>,
        nav: CalendarNav,
        today: LocalDate,
    ): CalendarUiState {
        val groups = (1..12).mapNotNull { month ->
            val ym = YearMonth.of(nav.year, month)
            val marks = marksOf(events, ym)
            if (marks.isEmpty()) return@mapNotNull null
            MonthGroup(
                month = ym,
                entries = marks.toSortedMap().flatMap { (date, list) ->
                    list.map { entryOf(it, date, today) }
                },
            )
        }
        return CalendarUiState(
            view = nav.view,
            month = nav.month,
            today = today,
            selected = nav.selected,
            monthGroups = groups,
            yearEventCount = groups.sumOf { it.entries.size },
            isLoading = false,
        )
    }

    /** 以周一为起点的 42 格日期序列。 */
    fun monthCells(month: YearMonth): List<LocalDate> {
        val firstDay = month.atDay(1)
        // DayOfWeek: 周一=1 … 周日=7；转成「周一为 0」的偏移
        val lead = (firstDay.dayOfWeek.value + 6) % 7
        val start = firstDay.minusDays(lead.toLong())
        return (0 until CELL_COUNT).map { start.plusDays(it.toLong()) }
    }

    private fun entryOf(event: CountdownEvent, date: LocalDate, today: LocalDate) = CalendarEntry(
        date = date,
        id = event.id,
        title = event.title,
        category = event.category,
        dayDelta = CalendarMath.daysUntil(date, today),
        isCompleted = event.isCompleted,
        isAllDay = event.isAllDay,
        repeatType = event.repeatType,
        repeatLabel = com.morrowviento.morrow.domain.time.RepeatText.short(event),
        spanLabel = com.morrowviento.morrow.domain.time.RepeatText.span(event),
    )

    /** 同一天内：未完成在前，其次按标题，保证每秒刷新也不会跳序。 */
    private val ENTRY_ORDER: Comparator<CountdownEvent> =
        compareBy<CountdownEvent> { it.isCompleted }.thenBy { it.title }.thenBy { it.id }
}
