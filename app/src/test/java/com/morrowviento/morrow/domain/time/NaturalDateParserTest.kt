package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.RepeatType
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自然语言输入解析（纯规则、无云端）。
 *
 * 这里的断言同时也是**产品说明**：写进测试的句式就是承诺能认的句式；
 * 认不出的句子必须 `matched = false`，宁可让用户手填，也不能猜出一个日期。
 */
class NaturalDateParserTest {

    /** 固定基准日：2026-03-01 是周日，星期推算的用例都按它算。 */
    private val now: LocalDate = LocalDate.of(2026, 3, 1)

    private fun parse(text: String, time: LocalTime = LocalTime.of(9, 0)) =
        NaturalDateParser.parse(text, now, time)

    @Test
    fun `明天下午三点加时长`() {
        val parsed = parse("明天下午三点 开会 1 小时")

        assertEquals(now.plusDays(1), parsed.date)
        assertEquals(LocalTime.of(15, 0), parsed.time)
        assertEquals(60, parsed.durationMinutes)
        assertEquals("开会", parsed.title)
        // 说了时刻就不是全天
        assertFalse(parsed.isAllDay)
        assertTrue(parsed.matched)
    }

    @Test
    fun `只有日期时按全天处理`() {
        val parsed = parse("3月5日 交报告")

        assertEquals(LocalDate.of(2026, 3, 5), parsed.date)
        assertEquals(null, parsed.time)
        assertTrue(parsed.isAllDay)
        assertEquals("交报告", parsed.title)
    }

    @Test
    fun `月日已过则取明年`() {
        // 2 月 20 日已经过了（基准是 3 月 1 日），说出来通常指下一次
        assertEquals(LocalDate.of(2027, 2, 20), parse("2月20日 复查").date)
    }

    @Test
    fun `带年份的绝对日期`() {
        assertEquals(LocalDate.of(2027, 1, 1), parse("2027年1月1日 元旦").date)
        assertEquals(LocalDate.of(2027, 1, 1), parse("2027-01-01 元旦").date)
    }

    @Test
    fun `相对日与相对跨度`() {
        assertEquals(now.plusDays(2), parse("后天 体检").date)
        assertEquals(now.plusDays(3), parse("三天后 体检").date)
        assertEquals(now.plusWeeks(2), parse("2周后 体检").date)
        assertEquals(now.plusMonths(1), parse("一个月后 体检").date)
    }

    @Test
    fun `每周一三五整体认，不把一三五漏成标题`() {
        val parsed = parse("每周一三五 健身")

        assertEquals(RepeatType.WEEKLY, parsed.repeatType)
        assertEquals(setOf(1, 3, 5), parsed.repeatWeekdays)
        assertTrue(parsed.weekdaysExplicit)
        // 第一个落点是最近的周一（基准日是周日）
        assertEquals(LocalDate.of(2026, 3, 2), parsed.date)
        assertEquals("健身", parsed.title)
    }

    @Test
    fun `下周三以下一周为基准，不会落到下下周三`() {
        val parsed = parse("下周三 述职")
        // 基准 2026-03-01 是周日；下周三 = 2026-03-11
        assertEquals(LocalDate.of(2026, 3, 11), parsed.date)
        assertEquals("述职", parsed.title)
    }

    @Test
    fun `单个星期不带修饰词取最近一次`() {
        assertEquals(LocalDate.of(2026, 3, 4), parse("周三 述职").date)
        // 今天就是周日：nextOrSame 得到今天，而不是七天后
        assertEquals(now, parse("周日 休息").date)
    }

    @Test
    fun `重复词各档`() {
        assertEquals(RepeatType.DAILY, parse("每天 背单词").repeatType)
        assertEquals(RepeatType.WEEKLY, parse("每周 复盘").repeatType)
        assertEquals(RepeatType.MONTHLY, parse("每月 交房租").repeatType)
        assertEquals(RepeatType.YEARLY, parse("每年 体检").repeatType)
        // 重复事件没说时刻也按全天走，不留一个假的「现在几点」
        assertTrue(parse("每天 背单词").isAllDay)
    }

    @Test
    fun `农历换算后自洽`() {
        val parsed = parse("农历八月十五 中秋")
        val date = parsed.date
        assertNotNull(date)
        val lunar = LunarCalendar.fromSolar(date!!)
        assertNotNull(lunar)
        assertEquals(8, lunar!!.month)
        assertEquals(15, lunar.day)
        assertEquals("中秋", parsed.title)
    }

    @Test
    fun `农历初十不会解析成空`() {
        val parsed = parse("农历正月初十 拜年")
        val date = parsed.date
        assertNotNull(date)
        val lunar = LunarCalendar.fromSolar(date!!)
        assertEquals(1, lunar!!.month)
        assertEquals(10, lunar.day)
    }

    @Test
    fun `时刻写法：冒号与时段词`() {
        assertEquals(LocalTime.of(9, 30), parse("9:30 站会").time)
        assertEquals(LocalTime.of(20, 0), parse("晚上八点 看电影").time)
        assertEquals(LocalTime.of(7, 30), parse("早上七点半 晨跑").time)
        assertEquals(LocalTime.of(13, 15), parse("下午一点一刻 面谈").time)
    }

    @Test
    fun `时长写法`() {
        assertEquals(30, parse("半小时 冥想").durationMinutes)
        assertEquals(45, parse("45 分钟 复盘").durationMinutes)
        assertEquals(120, parse("2 小时 打扫").durationMinutes)
    }

    @Test
    fun `认不出的句子必须 matched 为 false`() {
        assertFalse(parse("随便写点什么").matched)
        assertFalse(parse("").matched)
        assertFalse(parse("   ").matched)
    }

    @Test
    fun `只剩标题时不会凭空造出日期`() {
        val parsed = parse("买菜")
        assertFalse(parsed.matched)
        assertEquals(null, parsed.date)
    }

    @Test
    fun `中文数字解析`() {
        assertEquals(15, NaturalDateParser.parseNumber("十五"))
        assertEquals(20, NaturalDateParser.parseNumber("二十"))
        assertEquals(23, NaturalDateParser.parseNumber("二十三"))
        assertEquals(9, NaturalDateParser.parseNumber("9"))
        assertEquals(null, NaturalDateParser.parseNumber(""))
    }
}
