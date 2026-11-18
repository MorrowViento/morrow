package com.morrowviento.morrow.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 日期计算器的纯函数：边界都钉住，避免「差一天」这种最容易出错的偏差。 */
class DateMathTest {

    @Test
    fun `同一天含首尾是 1 天，不含首尾是 0 天`() {
        val day = LocalDate.of(2026, 11, 18)

        assertEquals(0L, DateMath.signedDays(day, day))
        assertEquals(1L, DateMath.inclusiveDays(day, day))
    }

    @Test
    fun `相差天数有符号，含首尾取绝对值加一`() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 18)

        assertEquals(17L, DateMath.signedDays(from, to))
        assertEquals(-17L, DateMath.signedDays(to, from))
        assertEquals(18L, DateMath.inclusiveDays(from, to))
        assertEquals(18L, DateMath.inclusiveDays(to, from))
    }

    @Test
    fun `周数拆分：17 天是 2 周 3 天`() {
        assertEquals(2L to 3L, DateMath.weeksAndDays(17))
        assertEquals(0L to 0L, DateMath.weeksAndDays(0))
        // 负数按绝对值拆：UI 上已经有「相差」两字，不需要再带符号
        assertEquals(4L to 2L, DateMath.weeksAndDays(-30))
    }

    @Test
    fun `跨月跨年的加减按日历走，不是按 30 天估算`() {
        assertEquals(LocalDate.of(2027, 1, 18), DateMath.addDays(LocalDate.of(2026, 12, 18), 31))
        // 2028 是闰年：2 月 28 日加一天是 29 日
        assertEquals(LocalDate.of(2028, 2, 29), DateMath.addDays(LocalDate.of(2028, 2, 28), 1))
        assertEquals(LocalDate.of(2026, 2, 28), DateMath.addDays(LocalDate.of(2026, 3, 1), -1))
    }

    @Test
    fun `年月日拆分按实际月长借位`() {
        val period = DateMath.periodBetween(LocalDate.of(2025, 1, 31), LocalDate.of(2026, 3, 1))

        // 1 月 31 日到 3 月 1 日：整年 + 1 个月，日数按 2 月实际长度借位
        assertEquals(1, period.years)
        assertEquals(1, period.months)
        assertEquals(1, period.days)
    }

    @Test
    fun `周几不依赖 Locale`() {
        // 2026-11-18 是周三
        assertEquals("周三", DateMath.weekdayLabel(LocalDate.of(2026, 11, 18)))
        assertEquals("周日", DateMath.weekdayLabel(LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun `长日期带周几，农历超出年表返回 null`() {
        assertEquals("2026年11月18日 周三", DateMath.longLabel(LocalDate.of(2026, 11, 18)))
        assertEquals("农历十月初十", DateMath.lunarLabel(LocalDate.of(2026, 11, 18)))
        // 1900 年之前没有年表
        assertNull(DateMath.lunarLabel(LocalDate.of(1899, 12, 31)))
    }
}
