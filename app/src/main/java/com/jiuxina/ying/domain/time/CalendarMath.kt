package com.jiuxina.ying.domain.time

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 日历日计算。全部基于 [LocalDate]，天然规避夏令时 23/25 小时导致的天数偏差
 * （Flutter 版通过 UTC 零点比较解决同一问题，此处更直接）。
 */
object CalendarMath {

    /** [to] 相对 [from] 的日历天数差，未来为正。 */
    fun calendarDayDelta(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).toInt()

    /** 今天相对事件日期：0 = 就是今天，负数 = 已过去。 */
    fun daysUntil(target: LocalDate, today: LocalDate = LocalDate.now()): Int =
        calendarDayDelta(today, target)

    /** 一年后的日期；2 月 29 日在平年回落为 2 月 28 日。 */
    fun anniversaryOn(year: Int, month: Int, day: Int): LocalDate {
        val safeDay = day.coerceAtMost(java.time.YearMonth.of(year, month).lengthOfMonth())
        return LocalDate.of(year, month, safeDay)
    }

    /**
     * 按年月构造日期，日越界时夹取到该月最后一天（1 月 31 日切到 2 月 → 28/29 日）。
     * 滚轮换月时用，避免 `LocalDate.of` 抛 `DateTimeException`。
     */
    fun dateClamped(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, maxDay))
    }

    // ---------------------------------------------------------------- 日期滚轮的年份范围

    /**
     * 新建/编辑事件时可选的年份。
     *
     * 与农历年表、日历页翻页边界共用 [LunarCalendar.MIN_YEAR]..[LunarCalendar.MAX_YEAR]：
     * 早于此范围的事件在日历里翻不到、农历也查不出来，滚轮给出来只会让用户建一个
     * 「看不见」的事件。旧版写死「去年..今年+20」（2026 年时最早只能选 2025），
     * 纪念类倒数日（出生年、纪念日、历史日期）完全选不了。
     */
    val SELECTABLE_YEARS: List<Int> = (LunarCalendar.MIN_YEAR..LunarCalendar.MAX_YEAR).toList()

    /** 年份 → 滚轮下标。旧数据越界时夹到最近的一端，不让 `indexOf` 返回 -1。 */
    fun yearIndex(year: Int): Int =
        (year - LunarCalendar.MIN_YEAR).coerceIn(0, SELECTABLE_YEARS.lastIndex)

    /** 滚轮下标 → 年份。下标恒来自滚轮，仍做一次夹取防止越界日期。 */
    fun yearAt(index: Int): Int =
        SELECTABLE_YEARS.getOrNull(index) ?: LunarCalendar.MAX_YEAR
}
