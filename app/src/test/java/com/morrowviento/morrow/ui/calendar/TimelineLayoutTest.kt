package com.morrowviento.morrow.ui.calendar

import com.morrowviento.morrow.domain.model.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 时间块的布局算法：分列与簇宽是最容易写错的地方，这里逐条钉住。
 * 时间一律固定，避免用例随真实日期漂移。
 */
class TimelineLayoutTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 18)

    private fun event(
        id: String,
        hour: Int,
        minute: Int = 0,
        durationMinutes: Int = 60,
        isAllDay: Boolean = false,
        endDate: LocalDate? = null,
    ) = CountdownEvent(
        id = id,
        title = id,
        targetDate = LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)),
        isAllDay = isAllDay,
        durationMinutes = durationMinutes,
        endDate = endDate,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    @Test
    fun `互不重叠的事件各占满一行`() {
        val timeline = TimelineLayout.build(
            listOf(event("a", 9), event("b", 14)),
            day,
        )

        assertEquals(listOf("a", "b"), timeline.blocks.map { it.id })
        assertTrue(timeline.blocks.all { it.laneCount == 1 && it.lane == 0 })
    }

    @Test
    fun `两个重叠的块并排成两列`() {
        val timeline = TimelineLayout.build(
            listOf(event("a", 9), event("b", 9, 30)),
            day,
        )

        // 9:00–10:00 与 9:30–10:30 交叠 30 分钟 → 两列
        assertEquals(listOf(0, 1), timeline.blocks.map { it.lane })
        assertTrue(timeline.blocks.all { it.laneCount == 2 })
    }

    @Test
    fun `三件同时开始的事并排成三列`() {
        val timeline = TimelineLayout.build(
            listOf(event("a", 9), event("b", 9), event("c", 9)),
            day,
        )

        assertEquals(listOf(0, 1, 2), timeline.blocks.map { it.lane })
        assertTrue(timeline.blocks.all { it.laneCount == 3 })
    }

    @Test
    fun `列宽只在簇内统一，下午独占的块仍然满宽`() {
        val timeline = TimelineLayout.build(
            listOf(event("a", 9), event("b", 9, 30), event("c", 14)),
            day,
        )

        val a = timeline.blocks.first { it.id == "a" }
        val b = timeline.blocks.first { it.id == "b" }
        val c = timeline.blocks.first { it.id == "c" }
        assertEquals(2, a.laneCount)
        assertEquals(2, b.laneCount)
        // 关键：如果按「全天最大并发」统一列数，c 会被压成三分之一宽
        assertEquals(1, c.laneCount)
        assertEquals(0, c.lane)
    }

    @Test
    fun `时长决定块高，未指定时按一小时`() {
        val timeline = TimelineLayout.build(
            listOf(
                event("half", 9, durationMinutes = 30),
                event("two", 11, durationMinutes = 120),
                event("unset", 14, durationMinutes = 0),
            ),
            day,
        )

        assertEquals(30, timeline.blocks.first { it.id == "half" }.endMinute - 9 * 60)
        assertEquals(120, timeline.blocks.first { it.id == "two" }.endMinute - 11 * 60)
        assertEquals(60, timeline.blocks.first { it.id == "unset" }.endMinute - 14 * 60)
    }

    @Test
    fun `深夜开始的块不会被挤出轴外`() {
        val timeline = TimelineLayout.build(listOf(event("late", 23, 58)), day)

        val block = timeline.blocks.single()
        assertTrue(block.endMinute <= TimelineLayout.MINUTES_PER_DAY)
        // 起点回退到留出最小高度，否则 23:58 + 20 分钟会变成负高度的块
        assertTrue(block.startMinute <= TimelineLayout.MINUTES_PER_DAY - TimelineLayout.MIN_BLOCK_MINUTES)
        assertTrue(block.endMinute > block.startMinute)
    }

    @Test
    fun `全天与跨天进顶部条，跨天中间日标出第几天`() {
        val multiDay = event("trip", 9, endDate = day.plusDays(2))
        val allDay = event("holiday", 0, isAllDay = true)
        val timeline = TimelineLayout.build(listOf(multiDay, allDay, event("meeting", 10)), day)

        assertEquals(listOf("holiday", "trip"), timeline.allDay.map { it.id }.sorted())
        assertEquals("第 1/3 天", timeline.allDay.first { it.id == "trip" }.dayIndexLabel)
        // 全天事件不是「第 1/1 天」——单日就不该有这段话
        assertNull(timeline.allDay.first { it.id == "holiday" }.dayIndexLabel)
        assertEquals(listOf("meeting"), timeline.blocks.map { it.id })
    }

    @Test
    fun `不是这一天的重复事件不出现`() {
        // 每 2 周周三：11 月 4 日、11 月 18 日…
        val weekly = CountdownEvent(
            id = "weekly",
            title = "weekly",
            targetDate = LocalDateTime.of(2026, 11, 4, 9, 0),
            repeatType = com.morrowviento.morrow.domain.model.RepeatType.WEEKLY,
            repeatInterval = 2,
            repeatWeekdays = setOf(3),
            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        )

        assertTrue(TimelineLayout.build(listOf(weekly), day).blocks.isNotEmpty())
        assertTrue(TimelineLayout.build(listOf(weekly), day.plusDays(1)).isEmpty)
    }

    @Test
    fun `空的一天也能算滚动位置，默认停在早上八点`() {
        val timeline = TimelineLayout.build(emptyList(), day)

        assertTrue(timeline.isEmpty)
        assertEquals(8 * 60, timeline.scrollToMinute)
        // 有事件时停在第一个事件前半小时
        assertEquals(9 * 60 - 30, TimelineLayout.build(listOf(event("a", 9)), day).scrollToMinute)
    }
}
