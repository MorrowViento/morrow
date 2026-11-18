package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 完整重复规则：日 / 周（多选星期、隔周）/ 月（短月夹取）/ 年（公历与农历）/ 结束日期，
 * 以及持续事件的区间覆盖。
 *
 * 这一组几乎全是「一年才暴露一次」的边界，靠真机试不出来，必须在这里钉死。
 */
class RepeatRuleTest {

    private fun event(
        start: LocalDate,
        type: RepeatType,
        interval: Int = 1,
        weekdays: Set<Int> = emptySet(),
        lunar: Boolean = false,
        until: LocalDate? = null,
        end: LocalDate? = null,
        handled: LocalDate? = null,
    ) = CountdownEvent(
        id = "e",
        title = "t",
        targetDate = LocalDateTime.of(start, java.time.LocalTime.of(9, 0)),
        repeatType = type,
        repeatMonth = if (type == RepeatType.YEARLY) start.monthValue else null,
        repeatDay = if (type == RepeatType.NONE) null else start.dayOfMonth,
        repeatInterval = interval,
        repeatWeekdays = weekdays,
        repeatOnLunar = lunar,
        repeatUntil = until,
        endDate = end,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        lastHandledOccurrence = handled,
    )

    @Test
    fun `每天重复按间隔推进，并尊重已处理游标`() {
        val e = event(LocalDate.of(2026, 11, 1), RepeatType.DAILY, interval = 3)
        val next = RepeatRule.futureOccurrences(e, LocalDate.of(2026, 11, 10)).take(3).toList()
        // 11/1 起每 3 天：… 11/10 正好落在格点上，之后 11/13、11/16
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 16)),
            next,
        )
        val handled = e.copy(lastHandledOccurrence = LocalDate.of(2026, 11, 12))
        assertEquals(
            LocalDate.of(2026, 11, 13),
            RepeatRule.futureOccurrences(handled, LocalDate.of(2026, 11, 10)).first(),
        )
    }

    @Test
    fun `每周多选星期，隔周只落在选中的那一周`() {
        // 2026-11-18 是周三
        val e = event(
            LocalDate.of(2026, 11, 18),
            RepeatType.WEEKLY,
            interval = 2,
            weekdays = setOf(1, 3), // 周一、周三
        )
        val dates = RepeatRule.futureOccurrences(e, LocalDate.of(2026, 11, 19)).take(4).toList()
        // 锚点所在周（11/16 周一起）是第 0 周；下一次是两周后那一周的周一/周三
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 2),
                LocalDate.of(2026, 12, 14),
                LocalDate.of(2026, 12, 16),
            ),
            dates,
        )
        assertEquals(DayOfWeek.MONDAY, dates[0].dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, dates[1].dayOfWeek)
    }

    @Test
    fun `不选星期时按锚点那一天重复`() {
        val e = event(LocalDate.of(2026, 11, 18), RepeatType.WEEKLY)
        val dates = RepeatRule.futureOccurrences(e, LocalDate.of(2026, 11, 19)).take(3).toList()
        assertEquals(listOf(25, 2, 9), dates.map { it.dayOfMonth })
        dates.forEach { assertEquals(DayOfWeek.WEDNESDAY, it.dayOfWeek) }
    }

    @Test
    fun `每月重复在短月夹到月末，且不改变后续月份的日子`() {
        val e = event(LocalDate.of(2026, 1, 31), RepeatType.MONTHLY)
        val dates = RepeatRule.futureOccurrences(e, LocalDate.of(2026, 2, 1)).take(3).toList()
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
            ),
            dates,
        )
    }

    @Test
    fun `每年公历重复按锚点月日推进`() {
        val e = event(LocalDate.of(2026, 11, 18), RepeatType.YEARLY)
        assertEquals(
            LocalDate.of(2027, 11, 18),
            RepeatRule.futureOccurrences(e, LocalDate.of(2026, 11, 19)).first(),
        )
    }

    @Test
    fun `农历年循环逐年都是同一个农历月日`() {
        // 2026-02-17 是农历正月初一（与 LunarCalendarTest 共用同一份年表结论）
        val springFestival = LocalDate.of(2026, 2, 17)
        val lunar = LunarCalendar.fromSolar(springFestival)!!
        assertEquals(1, lunar.month)
        assertEquals(1, lunar.day)

        val e = event(springFestival, RepeatType.YEARLY, lunar = true)
        val dates = RepeatRule.futureOccurrences(e, LocalDate.of(2027, 1, 1)).take(3).toList()
        assertEquals(3, dates.size)
        dates.forEach { date ->
            val back = LunarCalendar.fromSolar(date)!!
            assertEquals("每次都应落在农历正月初一：$date", 1, back.month)
            assertEquals("每次都应落在农历正月初一：$date", 1, back.day)
        }
        // 相邻两次相隔一个农历年（353–385 天）
        dates.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b) }
            .forEach { assertTrue("农历年长度应在 353–385 天之间，实际 $it", it in 353..385) }
    }

    @Test
    fun `农历三十碰上小月时取廿九`() {
        // 找一个腊月只有 29 天的农历年，验证「三十」不会溢出到下个月
        val candidate = (2026..2035).mapNotNull { year ->
            val last = LunarCalendar.toSolar(year, 12, 30)!!
            val back = LunarCalendar.fromSolar(last)!!
            if (back.month == 12 && back.day < 30) last to back.day else null
        }.firstOrNull()
        // 年表覆盖的十年里必定存在腊月小月；若不存在说明数据出了问题
        assertTrue("十年内应出现腊月小月", candidate != null)
        assertEquals(12, LunarCalendar.fromSolar(candidate!!.first)!!.month)
    }

    @Test
    fun `农历换算往返一致`() {
        // 扫 2026 整年：公历 → 农历 → 公历必须回到同一天
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            val lunar = LunarCalendar.fromSolar(date)
            if (lunar != null) {
                // 闰月无法反查（生日不按闰月过），只在平月断言
                if (!lunar.isLeapMonth) {
                    assertEquals("往返失败：$date", date, LunarCalendar.toSolar(lunar.year, lunar.month, lunar.day))
                }
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `结束日期之后不再产生落点`() {
        val e = event(
            LocalDate.of(2026, 11, 1),
            RepeatType.DAILY,
            until = LocalDate.of(2026, 11, 5),
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5).map { LocalDate.of(2026, 11, it) },
            RepeatRule.futureOccurrences(e, LocalDate.of(2026, 11, 1)).toList(),
        )
        assertNull(RepeatRule.futureOccurrences(e, LocalDate.of(2026, 11, 6)).firstOrNull())
        assertTrue(RepeatRule.isSeriesEnded(e, LocalDate.of(2026, 11, 6)))
        // 系列结束后卡片仍要有锚点日期，不能崩在 null 上
        assertEquals(
            LocalDateTime.of(2026, 11, 1, 9, 0),
            RepeatRule.upcomingTarget(e, LocalDate.of(2026, 11, 6)),
        )
    }

    @Test
    fun `持续事件覆盖区间内的每一天`() {
        val e = event(
            LocalDate.of(2026, 9, 1),
            RepeatType.NONE,
            end = LocalDate.of(2026, 9, 5),
        )
        assertEquals(4, e.spanDays)
        val today = LocalDate.of(2026, 9, 3)
        assertEquals(LocalDate.of(2026, 9, 1), RepeatRule.coveringStart(e, today))
        assertEquals(LocalDate.of(2026, 9, 5), RepeatRule.coveringEnd(e, today))
        assertTrue(RepeatRule.coversOn(e, today))
        // 区间外：9/6 已经不是这件事了
        assertNull(RepeatRule.coveringStart(e, LocalDate.of(2026, 9, 6)))

        assertEquals(
            listOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4)),
            RepeatRule.coverageBetween(e, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 4)),
        )
        // 起点在区间之前、尾巴伸进来的那次也要算
        assertEquals(
            listOf(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 5)),
            RepeatRule.coverageBetween(e, LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 6)),
        )
    }

    @Test
    fun `持续事件与每周重复叠加时，每次发生都带着同样的跨度`() {
        val e = event(
            LocalDate.of(2026, 11, 2), // 周一
            RepeatType.WEEKLY,
            end = LocalDate.of(2026, 11, 4), // 每次占周一到周三
        )
        assertEquals(2, e.spanDays)
        // 11/9 那周：11/9 是新的起点，11/10、11/11 也在区间里
        assertEquals(LocalDate.of(2026, 11, 9), RepeatRule.coveringStart(e, LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2026, 11, 11), RepeatRule.coveringEnd(e, LocalDate.of(2026, 11, 10)))
        // 周四（11/12）已经结束，下一次是下周一
        assertNull(RepeatRule.coveringStart(e, LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun `主操作对每天每周重复推进到下一次`() {
        val e = event(LocalDate.of(2026, 11, 18), RepeatType.WEEKLY)
        val advanced = RepeatRule.applyPrimaryAction(
            e,
            LocalDate.of(2026, 11, 18),
            LocalDate.of(2026, 11, 18),
        )
        assertEquals(LocalDate.of(2026, 11, 18), advanced?.lastHandledOccurrence)
        // 迟到的动作（期望落点已经不是当前这次）不推进
        assertNull(
            RepeatRule.applyPrimaryAction(e, LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 18)),
        )
    }
}
