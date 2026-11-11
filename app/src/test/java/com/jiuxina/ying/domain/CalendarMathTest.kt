package com.jiuxina.ying.domain

import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import com.jiuxina.ying.domain.time.CalendarMath
import com.jiuxina.ying.domain.time.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarMathTest {

    @Test
    fun `同日差为 0`() {
        val day = LocalDate.of(2026, 11, 11)
        assertEquals(0, CalendarMath.calendarDayDelta(day, day))
    }

    @Test
    fun `跨月与跨年按日历日计算`() {
        assertEquals(20, CalendarMath.calendarDayDelta(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 12, 1)))
        assertEquals(365, CalendarMath.calendarDayDelta(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `闰年 2 月多出一天`() {
        // 2028 为闰年
        assertEquals(366, CalendarMath.calendarDayDelta(LocalDate.of(2028, 1, 1), LocalDate.of(2029, 1, 1)))
    }

    @Test
    fun `闰日周年在平年回落为 2 月 28 日`() {
        assertEquals(LocalDate.of(2027, 2, 28), CalendarMath.anniversaryOn(2027, 2, 29))
        assertEquals(LocalDate.of(2028, 2, 29), CalendarMath.anniversaryOn(2028, 2, 29))
    }

    @Test
    fun `过期事件在 auto 模式下按已过天数累加`() {
        val event = CountdownEvent(
            id = "1",
            title = "已过",
            targetDate = LocalDateTime.of(2026, 11, 1, 0, 0),
            createdAt = LocalDateTime.of(2026, 10, 1, 0, 0),
        )
        val today = LocalDate.of(2026, 11, 11)
        assertTrue(event.isCountingUp(today))
        assertEquals(10, event.displayDays(today))
    }

    @Test
    fun `未来事件不算累加`() {
        val event = CountdownEvent(
            id = "2",
            title = "未来",
            targetDate = LocalDateTime.of(2026, 12, 1, 0, 0),
            createdAt = LocalDateTime.of(2026, 10, 1, 0, 0),
        )
        val today = LocalDate.of(2026, 11, 11)
        assertFalse(event.isCountingUp(today))
        assertEquals(20, event.displayDays(today))
    }

    @Test
    fun `每年重复取今年未过期的周年`() {
        val event = CountdownEvent(
            id = "3",
            title = "生日",
            targetDate = LocalDateTime.of(1990, 12, 25, 8, 30),
            repeatType = RepeatType.YEARLY,
            repeatMonth = 12,
            repeatDay = 25,
            createdAt = LocalDateTime.of(1990, 12, 25, 8, 30),
        )
        val today = LocalDate.of(2026, 11, 11)
        val next = RepeatRule.upcomingTarget(event, today)
        assertEquals(LocalDateTime.of(2026, 12, 25, 8, 30), next)
        assertEquals(44, RepeatRule.daysUntilNext(event, today))
    }

    @Test
    fun `每年重复在周年已过时顺延到明年`() {
        val event = CountdownEvent(
            id = "4",
            title = "纪念日",
            targetDate = LocalDateTime.of(1990, 3, 1, 0, 0),
            repeatType = RepeatType.YEARLY,
            repeatMonth = 3,
            repeatDay = 1,
            createdAt = LocalDateTime.of(1990, 3, 1, 0, 0),
        )
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(LocalDateTime.of(2027, 3, 1, 0, 0), RepeatRule.upcomingTarget(event, today))
    }

    @Test
    fun `非重复事件原样返回目标时刻`() {
        val target = LocalDateTime.of(2026, 11, 20, 18, 0)
        val event = CountdownEvent(id = "5", title = "x", targetDate = target, createdAt = target)
        assertEquals(target, RepeatRule.upcomingTarget(event, LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun `全天事件提醒锚点为当天 9 点`() {
        val event = CountdownEvent(
            id = "6",
            title = "全天",
            targetDate = LocalDateTime.of(2026, 11, 20, 23, 45),
            isAllDay = true,
            createdAt = LocalDateTime.of(2026, 11, 1, 0, 0),
        )
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0), event.reminderAnchor)
    }

    @Test
    fun `提醒归一化去重排序并限制五条`() {
        val input = listOf(
            EventReminder("a", 30),
            EventReminder("b", 10),
            EventReminder("c", 30),
            EventReminder("d", -5),
            EventReminder("e", 1440),
            EventReminder("f", 60),
            EventReminder("g", 120),
        )
        val normalized = EventReminder.normalize(input)
        assertEquals(5, normalized.size)
        assertEquals(listOf(1440, 120, 60, 30, 10), normalized.map { it.minutesBefore })
        // 去重时保留首个
        assertEquals("a", normalized.first { it.minutesBefore == 30 }.id)
    }

    @Test
    fun `提醒文案按分钟小时天分档`() {
        assertEquals("事件发生时", EventReminder.labelForMinutes(0))
        assertEquals("提前 30 分钟", EventReminder.labelForMinutes(30))
        assertEquals("提前 2 小时", EventReminder.labelForMinutes(120))
        assertEquals("提前 3 天", EventReminder.labelForMinutes(4320))
        assertEquals("提前 90 分钟", EventReminder.labelForMinutes(90))
    }
}
