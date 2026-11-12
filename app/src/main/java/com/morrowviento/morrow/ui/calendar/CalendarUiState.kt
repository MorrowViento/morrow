package com.morrowviento.morrow.ui.calendar

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.time.CalendarMath
import com.morrowviento.morrow.domain.time.LunarCalendar
import java.time.LocalDate
import java.time.YearMonth

/** 日历三种视图。[label] 用于分段控件的按钮文字。 */
enum class CalendarView(val label: String) {
    MONTH("月"),
    YEAR("年"),
    LIST("列表"),
}

/**
 * 日历的浏览位置。
 *
 * 三种视图共用一个 [month] 作为锚点：切到年视图看的是该年，切到列表看的是该年，
 * 点年视图里的某个月再回到月视图也只需改 [month]。避免为每个视图各存一个位置后互不同步。
 */
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
    }
}

/** 月视图的一格。 */
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
data class CalendarEntry(
    val date: LocalDate,
    val id: String,
    val title: String,
    val category: String,
    /** 该日期相对今天的天数：0 = 今天，负 = 已过去。 */
    val dayDelta: Int,
    val isCompleted: Boolean,
    val isAllDay: Boolean,
    val repeatsYearly: Boolean,
)

/** 年视图的一个月。 */
data class MonthSummary(
    val month: YearMonth,
    val eventCount: Int,
    /** 最多 3 个事件标题，用于卡片上给一点信息量。 */
    val sampleTitles: List<String>,
)

/** 列表视图的一个月分组。 */
data class MonthGroup(
    val month: YearMonth,
    val entries: List<CalendarEntry>,
)

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
 * 相邻页是为了让翻页动画**两页同时在屏上**：旧版是「当前页滑出去 → 换数据 → 新页从另一侧滑进来」，
 * 中间有一整屏空白（真机反馈「两个界面之间有一段空白动画间隔」）。现在滑到哪一页、哪一页就已经
 * 渲染在那里，空白期从结构上不存在。
 *
 * [prev] / [next] 在边界（已到农历年表尽头）为 null，调用方据此不做翻页动画，直接回弹。
 */
data class CalendarStrip(
    val current: CalendarUiState,
    val prev: CalendarUiState? = null,
    val next: CalendarUiState? = null,
)

/**
 * 日历展示逻辑（纯函数，无 Android 依赖）。
 *
 * 重复事件按「当年周年」落位：月/日取自 `repeatMonth/repeatDay`（缺省取原目标日期），
 * 因此每年都能在日历上看到同一个纪念日，而不是只有「下一次」那一次。
 */
object CalendarPresentation {

    const val CELL_COUNT = 42
    val WEEKDAY_LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 事件在 [year] 年的落点日期。
     *
     * 非重复事件只在目标年份匹配时出现；每年重复事件落在当年同月同日，
     * 早于首次目标日期的不算（否则会把事件「倒推」到创建之前的年份）。
     */
    fun occurrenceIn(event: CountdownEvent, year: Int): LocalDate? {
        if (event.repeatType != RepeatType.YEARLY) {
            return event.targetDate.toLocalDate().takeIf { it.year == year }
        }
        val month = event.repeatMonth ?: event.targetDate.monthValue
        val day = event.repeatDay ?: event.targetDate.dayOfMonth
        val date = CalendarMath.anniversaryOn(year, month, day)
        val first = event.targetDate.toLocalDate()
        return date.takeIf { !it.isBefore(first) }
    }

    /** 某月的日期 → 事件列表（同一天多个事件按未完成优先、标题排序）。 */
    fun marksOf(events: List<CountdownEvent>, month: YearMonth): Map<LocalDate, List<CountdownEvent>> {
        val out = LinkedHashMap<LocalDate, MutableList<CountdownEvent>>()
        events.forEach { event ->
            occurrenceIn(event, month.year)?.let { date ->
                if (YearMonth.from(date) == month) out.getOrPut(date) { mutableListOf() }.add(event)
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
        }

    /**
     * 当前页 + 相邻页。
     *
     * 相邻页只在翻页动画期间被渲染；代价是每次数据变化多算两页（月视图 42 格 × 2），
     * 换来的是滑动时屏上始终有内容——比省这点计算量重要。
     */
    fun strip(events: List<CountdownEvent>, nav: CalendarNav, today: LocalDate): CalendarStrip =
        CalendarStrip(
            current = build(events, nav, today),
            prev = neighbor(events, nav, today, -1),
            next = neighbor(events, nav, today, 1),
        )

    /** 相邻页；已到边界（clamp 后还是本月/今年）返回 null，调用方据此回弹而不是空翻一页。 */
    private fun neighbor(
        events: List<CountdownEvent>,
        nav: CalendarNav,
        today: LocalDate,
        delta: Long,
    ): CalendarUiState? {
        val target = if (nav.view == CalendarView.MONTH) {
            nav.month.plusMonths(delta)
        } else {
            nav.month.plusYears(delta)
        }
        val clamped = CalendarNav.clamp(target)
        if (clamped == nav.month) return null
        // 翻页会清空选区（见 EventViewModel.shiftCalendarMonth），相邻页保持一致
        return build(events, nav.copy(month = clamped, selected = null), today)
    }

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
        val marks = months.associateWith { marksOf(events, it) }

        val cells = dates.map { date ->
            CalendarCell(
                date = date,
                inMonth = YearMonth.from(date) == nav.month,
                isToday = date == today,
                isSelected = date == selected,
                lunarLabel = LunarCalendar.cellLabel(date).orEmpty(),
                isFestival = LunarCalendar.festival(date) != null,
                eventCount = marks[YearMonth.from(date)]?.get(date).orEmpty().size,
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
            val entries = marks.toSortedMap().values.flatten()
            MonthSummary(
                month = ym,
                eventCount = entries.size,
                sampleTitles = entries.map { it.title }.distinct().take(3),
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
        repeatsYearly = event.repeatsYearly,
    )

    /** 同一天内：未完成在前，其次按标题，保证每秒刷新也不会跳序。 */
    private val ENTRY_ORDER: Comparator<CountdownEvent> =
        compareBy<CountdownEvent> { it.isCompleted }.thenBy { it.title }.thenBy { it.id }
}
