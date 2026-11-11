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
}
