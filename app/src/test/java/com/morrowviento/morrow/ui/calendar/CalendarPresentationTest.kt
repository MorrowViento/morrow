package com.morrowviento.morrow.ui.calendar

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class CalendarPresentationTest {

    private val today: LocalDate = LocalDate.parse("2026-03-14")

    private fun event(
        id: String = "e1",
        title: String = "事件",
        target: String = "2026-03-14T09:00",
        repeat: RepeatType = RepeatType.NONE,
        repeatMonth: Int? = null,
        repeatDay: Int? = null,
        completed: Boolean = false,
    ) = CountdownEvent(
        id = id,
        title = title,
        targetDate = LocalDateTime.parse(target),
        repeatType = repeat,
        repeatMonth = repeatMonth,
        repeatDay = repeatDay,
        isCompleted = completed,
        createdAt = LocalDateTime.parse("2026-01-01T00:00"),
    )

    private fun yearly(month: Int, day: Int, id: String = "y1", title: String = "周年") = event(
        id = id,
        title = title,
        target = "2020-%02d-%02dT09:00".format(month, day),
        repeat = RepeatType.YEARLY,
        repeatMonth = month,
        repeatDay = day,
    )

    // ---- 月网格 ----

    @Test
    fun `月网格固定 42 格且以周一开头`() {
        val cells = CalendarPresentation.monthCells(YearMonth.of(2026, 3))
        assertEquals(42, cells.size)
        assertEquals(DayOfWeek.MONDAY, cells.first().dayOfWeek)
        // 2026-03-01 是周日，故落在第 7 列（下标 6）
        assertEquals(LocalDate.parse("2026-02-23"), cells.first())
        assertEquals(LocalDate.parse("2026-03-01"), cells[6])
        assertEquals(LocalDate.parse("2026-04-05"), cells.last())
    }

    @Test
    fun `月网格日期连续`() {
        val cells = CalendarPresentation.monthCells(YearMonth.of(2026, 1))
        cells.zipWithNext().forEach { (a, b) ->
            assertEquals(a.plusDays(1), b)
        }
    }

    // ---- 重复事件落位 ----

    @Test
    fun `每年重复事件按当年周年落位`() {
        val e = yearly(month = 3, day = 14)
        assertEquals(LocalDate.parse("2026-03-14"), CalendarPresentation.occurrenceIn(e, 2026))
        assertEquals(LocalDate.parse("2027-03-14"), CalendarPresentation.occurrenceIn(e, 2027))
        assertEquals(LocalDate.parse("2024-03-14"), CalendarPresentation.occurrenceIn(e, 2024))
    }

    @Test
    fun `重复事件不早于首次目标日期`() {
        val e = yearly(month = 3, day = 14)
        assertNull(CalendarPresentation.occurrenceIn(e, 2019))
        assertEquals(LocalDate.parse("2020-03-14"), CalendarPresentation.occurrenceIn(e, 2020))
    }

    @Test
    fun `二月二十九的周年在平年夹到二十八`() {
        val e = event(
            id = "leap",
            target = "2024-02-29T09:00",
            repeat = RepeatType.YEARLY,
            repeatMonth = 2,
            repeatDay = 29,
        )
        assertEquals(LocalDate.parse("2025-02-28"), CalendarPresentation.occurrenceIn(e, 2025))
        assertEquals(LocalDate.parse("2028-02-29"), CalendarPresentation.occurrenceIn(e, 2028))
    }

    @Test
    fun `非重复事件只在目标年份出现`() {
        val e = event(target = "2026-03-14T09:00")
        assertEquals(LocalDate.parse("2026-03-14"), CalendarPresentation.occurrenceIn(e, 2026))
        assertNull(CalendarPresentation.occurrenceIn(e, 2025))
        assertNull(CalendarPresentation.occurrenceIn(e, 2027))
    }

    // ---- 月视图构建 ----

    @Test
    fun `月视图统计当天事件数与选中日事件`() {
        val events = listOf(
            event(id = "a", title = "项目上线", target = "2026-03-14T09:00"),
            event(id = "b", title = "体检", target = "2026-03-14T18:00"),
            event(id = "c", title = "别的日子", target = "2026-03-20T09:00"),
        )
        val state = CalendarPresentation.build(
            events = events,
            nav = CalendarNav(view = CalendarView.MONTH, month = YearMonth.of(2026, 3), selected = LocalDate.parse("2026-03-14")),
            today = today,
        )
        val cell = state.cells.first { it.date == LocalDate.parse("2026-03-14") }
        assertEquals(2, cell.eventCount)
        assertTrue(cell.isToday)
        assertTrue(cell.isSelected)
        assertEquals(3, state.monthEventCount)
        // 同一天内两个都未完成，顺序按标题的码位比较（中文不保证拼音序），只校验集合
        assertEquals(setOf("a", "b"), state.selectedEntries.map { it.id }.toSet())
    }

    @Test
    fun `同一天内未完成排在已完成前面`() {
        val events = listOf(
            event(id = "done", title = "已完成的事", target = "2026-03-14T09:00", completed = true),
            event(id = "todo", title = "待办的事", target = "2026-03-14T09:00"),
        )
        val state = CalendarPresentation.build(
            events = events,
            nav = CalendarNav(CalendarView.MONTH, YearMonth.of(2026, 3), LocalDate.parse("2026-03-14")),
            today = today,
        )
        assertEquals(listOf("todo", "done"), state.selectedEntries.map { it.id })
    }

    @Test
    fun `补位格使用相邻月份的事件`() {
        // 2026-03-30 之后是 4 月；网格最后一列会带出 4 月初的日期
        val events = listOf(event(id = "apr", target = "2026-04-02T09:00"))
        val state = CalendarPresentation.build(
            events = events,
            nav = CalendarNav(CalendarView.MONTH, YearMonth.of(2026, 3), null),
            today = today,
        )
        val cell = state.cells.first { it.date == LocalDate.parse("2026-04-02") }
        assertEquals(false, cell.inMonth)
        assertEquals(1, cell.eventCount)
        // 不属于本月，不计入本月总数
        assertEquals(0, state.monthEventCount)
    }

    @Test
    fun `月视图农历副标题与节日标记`() {
        val state = CalendarPresentation.build(
            events = emptyList(),
            nav = CalendarNav(CalendarView.MONTH, YearMonth.of(2026, 9), null),
            today = today,
        )
        val mid = state.cells.first { it.date == LocalDate.parse("2026-09-25") }
        assertEquals("中秋", mid.lunarLabel)
        assertTrue(mid.isFestival)
        val plain = state.cells.first { it.date == LocalDate.parse("2026-09-26") }
        assertEquals("十六", plain.lunarLabel)
        assertEquals(false, plain.isFestival)
    }

    // ---- 年视图 ----

    @Test
    fun `年视图覆盖十二个月并统计总数`() {
        val events = listOf(
            event(id = "a", title = "项目上线", target = "2026-01-10T09:00"),
            event(id = "b", title = "体检", target = "2026-03-14T09:00"),
            event(id = "c", title = "年会", target = "2026-03-20T09:00"),
        )
        val state = CalendarPresentation.build(
            events = events,
            nav = CalendarNav(CalendarView.YEAR, YearMonth.of(2026, 3), null),
            today = today,
        )
        assertEquals(12, state.monthSummaries.size)
        assertEquals(3, state.yearEventCount)
        assertEquals(2, state.monthSummaries[2].eventCount)
        assertEquals(listOf("体检", "年会"), state.monthSummaries[2].sampleTitles)
        assertEquals(1, state.monthSummaries[0].eventCount)
    }

    @Test
    fun `年视图按月统计重复事件`() {
        val state = CalendarPresentation.build(
            events = listOf(yearly(month = 12, day = 31)),
            nav = CalendarNav(CalendarView.YEAR, YearMonth.of(2027, 1), null),
            today = today,
        )
        assertEquals(1, state.yearEventCount)
        assertEquals(1, state.monthSummaries[11].eventCount)
    }

    // ---- 列表视图 ----

    @Test
    fun `列表视图按月分组且跳过空月`() {
        val events = listOf(
            event(id = "a", target = "2026-03-14T09:00"),
            event(id = "b", target = "2026-05-01T09:00"),
            yearly(month = 8, day = 15, id = "c"),
        )
        val state = CalendarPresentation.build(
            events = events,
            nav = CalendarNav(CalendarView.LIST, YearMonth.of(2026, 1), null),
            today = today,
        )
        assertEquals(listOf(3, 5, 8), state.monthGroups.map { it.month.monthValue })
        assertEquals(3, state.yearEventCount)
        assertEquals(listOf("a"), state.monthGroups[0].entries.map { it.id })
        // 每月分组内按日期升序
        assertTrue(state.monthGroups.all { group ->
            group.entries.zipWithNext().all { (x, y) -> !x.date.isAfter(y.date) }
        })
    }

    @Test
    fun `列表视图条目的天数差以今天为基准`() {
        val events = listOf(
            event(id = "past", target = "2026-03-01T09:00"),
            event(id = "now", target = "2026-03-14T09:00"),
            event(id = "future", target = "2026-03-20T09:00"),
        )
        val state = CalendarPresentation.build(
            events = events,
            nav = CalendarNav(CalendarView.LIST, YearMonth.of(2026, 3), null),
            today = today,
        )
        val deltas = state.monthGroups.single().entries.associate { it.id to it.dayDelta }
        assertEquals(-13, deltas["past"])
        assertEquals(0, deltas["now"])
        assertEquals(6, deltas["future"])
    }

    // ---- 边界 ----

    @Test
    fun `年视图空年份仍返回十二个月`() {
        val state = CalendarPresentation.build(
            events = emptyList(),
            nav = CalendarNav(CalendarView.YEAR, YearMonth.of(2030, 1), null),
            today = today,
        )
        assertEquals(12, state.monthSummaries.size)
        assertEquals(0, state.yearEventCount)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `浏览范围被夹在农历年表区间内`() {
        assertEquals(YearMonth.of(1900, 1), CalendarNav.clamp(YearMonth.of(1800, 6)))
        assertEquals(YearMonth.of(2100, 12), CalendarNav.clamp(YearMonth.of(2200, 3)))
        assertEquals(YearMonth.of(2026, 3), CalendarNav.clamp(YearMonth.of(2026, 3)))
    }

    @Test
    fun `相邻页给出前后月份且清空选区`() {
        val nav = CalendarNav(view = CalendarView.MONTH, month = YearMonth.of(2026, 3), selected = today)
        val strip = CalendarPresentation.strip(listOf(event()), nav, today)

        assertEquals(YearMonth.of(2026, 3), strip.current.month)
        assertEquals(today, strip.current.selected)
        assertEquals(YearMonth.of(2026, 2), strip.prev?.month)
        assertEquals(YearMonth.of(2026, 4), strip.next?.month)
        // 翻页会清空选区，相邻页必须和翻过去之后的状态一致，否则动画收尾时选区会闪一下
        assertNull(strip.prev?.selected)
        assertNull(strip.next?.selected)
    }

    @Test
    fun `相邻页在年视图按年翻`() {
        val nav = CalendarNav(view = CalendarView.YEAR, month = YearMonth.of(2026, 3))
        val strip = CalendarPresentation.strip(emptyList(), nav, today)

        assertEquals(2025, strip.prev?.month?.year)
        assertEquals(2027, strip.next?.month?.year)
        assertEquals(CalendarView.YEAR, strip.next?.view)
    }

    @Test
    fun `翻到年表边界时没有相邻页`() {
        val first = CalendarNav(view = CalendarView.MONTH, month = CalendarNav.clamp(YearMonth.of(1800, 1)))
        val firstStrip = CalendarPresentation.strip(emptyList(), first, today)
        assertNull("已到最早月份，不该再有上一页（否则会空翻一页）", firstStrip.prev)
        assertTrue(firstStrip.next != null)

        val last = CalendarNav(view = CalendarView.MONTH, month = CalendarNav.clamp(YearMonth.of(2200, 12)))
        val lastStrip = CalendarPresentation.strip(emptyList(), last, today)
        assertNull("已到最晚月份，不该再有下一页", lastStrip.next)
        assertTrue(lastStrip.prev != null)
    }
}
