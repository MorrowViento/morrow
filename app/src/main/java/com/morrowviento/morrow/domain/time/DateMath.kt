package com.morrowviento.morrow.domain.time

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 日期计算器的纯函数部分（无 Android 依赖，可 JVM 单测）。
 *
 * 刻意不做「工作日 / 节假日」这类计算：那需要一份会过期的调休表，
 * 而这份表的维护成本远高于它带来的便利。这里只回答两种确定的问题——
 * 「两天差多少」「某天加减几天是哪天」。
 */
object DateMath {

    /** 有符号天数差（后者晚于前者为正）。 */
    fun signedDays(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /**
     * 含首尾的天数。
     *
     * 9 月 1 日到 9 月 1 日是 1 天，不是 0 天——「请假几天」这类问法都是含首尾的。
     */
    fun inclusiveDays(from: LocalDate, to: LocalDate): Long = abs(signedDays(from, to)) + 1

    /** 把总天数拆成「几周零几天」。 */
    fun weeksAndDays(days: Long): Pair<Long, Long> {
        val safe = abs(days)
        return safe / 7 to safe % 7
    }

    /** 两个日期之间的「几年几个月几天」，按日历规则算（借位按实际月长）。 */
    fun periodBetween(from: LocalDate, to: LocalDate): Period =
        if (from <= to) Period.between(from, to) else Period.between(to, from)

    fun addDays(date: LocalDate, days: Long): LocalDate = date.plusDays(days)

    /** 周几：周一 … 周日。不依赖 Locale，避免在测试里拿到英文。 */
    fun weekdayLabel(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    /** 中文日期：`2026年11月18日 周三`。 */
    fun longLabel(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(date)}"

    /** 农历文案：超出年表返回 null。 */
    fun lunarLabel(date: LocalDate): String? =
        LunarCalendar.fromSolar(date)?.let { "农历${it.monthLabel}${it.dayLabel}" }

    /** 公历年份对应的「周岁」拆分（用于「在一起多久」这类展示）。 */
    fun ageLabel(from: LocalDate, to: LocalDate): String {
        val period = periodBetween(from, to)
        return buildString {
            if (period.years > 0) append("${period.years} 年 ")
            if (period.months > 0) append("${period.months} 个月 ")
            append("${period.days} 天")
        }
    }

    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
}
