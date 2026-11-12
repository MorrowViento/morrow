package com.morrowviento.morrow.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 农历换算单测。
 *
 * 基准值不是凭印象写的：春节取 2019–2031 年的公历日期，闰月取 2020/2023/2025 三年的闰月首日，
 * 均与年表独立推算结果一致。这三类断言能同时锁住「年表未错位」和「闰月分支未写反」。
 */
class LunarCalendarTest {

    private fun lunar(date: String): LunarDate? = LunarCalendar.fromSolar(LocalDate.parse(date))

    @Test
    fun `年表起点是 1900 年正月初一`() {
        assertEquals(LunarDate(1900, 1, 1, false), lunar("1900-01-31"))
    }

    @Test
    fun `春节落在正月初一`() {
        assertEquals(LunarDate(2026, 1, 1, false), lunar("2026-02-17"))
        assertEquals("春节", LunarCalendar.festival(LocalDate.parse("2026-02-17")))
    }

    @Test
    fun `2019 到 2031 年春节日期全部正确`() {
        val expected = mapOf(
            2019 to "2019-02-05",
            2020 to "2020-01-25",
            2021 to "2021-02-12",
            2022 to "2022-02-01",
            2023 to "2023-01-22",
            2024 to "2024-02-10",
            2025 to "2025-01-29",
            2026 to "2026-02-17",
            2027 to "2027-02-06",
            2028 to "2028-01-26",
            2029 to "2029-02-13",
            2030 to "2030-02-03",
            2031 to "2031-01-23",
        )
        expected.forEach { (year, date) ->
            val lunar = lunar(date)
            assertEquals("$year 年春节应为正月初一", year, lunar?.year)
            assertEquals(1, lunar?.month)
            assertEquals(1, lunar?.day)
            assertEquals(false, lunar?.isLeapMonth)
        }
    }

    @Test
    fun `闰月首日带闰月标记`() {
        // 2020 闰四月、2023 闰二月、2025 闰六月
        assertEquals(LunarDate(2020, 4, 1, true), lunar("2020-05-23"))
        assertEquals(LunarDate(2023, 2, 1, true), lunar("2023-03-22"))
        assertEquals(LunarDate(2025, 6, 1, true), lunar("2025-07-25"))
        assertEquals("闰二月", lunar("2023-03-22")?.monthLabel)
    }

    @Test
    fun `闰月与同名平月要能区分`() {
        // 2023 年二月与闰二月是两个不同的月
        assertEquals(LunarDate(2023, 2, 1, false), lunar("2023-02-20"))
        assertEquals(LunarDate(2023, 2, 1, true), lunar("2023-03-22"))
    }

    @Test
    fun `中秋在八月十五`() {
        assertEquals(LunarDate(2026, 8, 15, false), lunar("2026-09-25"))
        assertEquals("中秋", LunarCalendar.festival(LocalDate.parse("2026-09-25")))
        assertEquals(LunarDate(2025, 8, 15, false), lunar("2025-10-06"))
    }

    @Test
    fun `腊月最后一天是除夕`() {
        // 2025 年腊月为小月（29 天），除夕即廿九
        val eve = LocalDate.parse("2026-02-16")
        assertEquals(LunarDate(2025, 12, 29, false), LunarCalendar.fromSolar(eve))
        assertEquals("除夕", LunarCalendar.festival(eve))
    }

    @Test
    fun `闰月不过节`() {
        val leapMonth = LunarCalendar.fromSolar(LocalDate.parse("2020-05-23"))!!
        assertTrue(leapMonth.isLeapMonth)
        assertNull(LunarCalendar.festival(leapMonth))
    }

    @Test
    fun `格子副标题初一显示月名其余显示日名`() {
        // 节日优先于月名/日名，这是日历格子的既定展示规则
        assertEquals("春节", LunarCalendar.cellLabel(LocalDate.parse("2026-02-17")))
        assertEquals("廿六", LunarCalendar.cellLabel(LocalDate.parse("2026-03-14")))
        assertEquals("中秋", LunarCalendar.cellLabel(LocalDate.parse("2026-09-25")))
        // 非节日的初一只显示月名
        assertEquals("二月", LunarCalendar.cellLabel(LocalDate.parse("2026-03-19")))
    }

    @Test
    fun `日名覆盖整十与整十边界`() {
        assertEquals("初一", LunarCalendar.dayName(1))
        assertEquals("初九", LunarCalendar.dayName(9))
        assertEquals("初十", LunarCalendar.dayName(10))
        assertEquals("十一", LunarCalendar.dayName(11))
        assertEquals("十九", LunarCalendar.dayName(19))
        assertEquals("二十", LunarCalendar.dayName(20))
        assertEquals("廿一", LunarCalendar.dayName(21))
        assertEquals("廿九", LunarCalendar.dayName(29))
        assertEquals("三十", LunarCalendar.dayName(30))
    }

    @Test
    fun `月名覆盖冬月与腊月`() {
        assertEquals("正月", LunarCalendar.monthName(1))
        assertEquals("十月", LunarCalendar.monthName(10))
        assertEquals("冬月", LunarCalendar.monthName(11))
        assertEquals("腊月", LunarCalendar.monthName(12))
    }

    @Test
    fun `年表范围外返回空`() {
        assertNull(LunarCalendar.fromSolar(LocalDate.parse("1900-01-30")))
        // 年表覆盖的是「农历年」1900–2100：2101 年初仍属农历 2100 年腊月，故不越界
        assertEquals(LunarDate(2100, 12, 2, false), LunarCalendar.fromSolar(LocalDate.parse("2101-01-01")))
        assertNull(LunarCalendar.fromSolar(LocalDate.parse("2101-06-01")))
        // 边界内最后一天仍可换算
        assertEquals(LunarDate(2100, 12, 1, false), LunarCalendar.fromSolar(LocalDate.parse("2100-12-31")))
    }

    @Test
    fun `年天数与闰月天数自洽`() {
        assertEquals(2, LunarCalendar.leapMonth(2023))
        assertEquals(384, LunarCalendar.yearDays(2023))
        assertEquals(29, LunarCalendar.leapDays(2023))
        assertEquals(0, LunarCalendar.leapDays(2026))
    }
}
