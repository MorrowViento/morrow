package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 重复规则。当前版本仅有「每年重复」（与 Flutter 版 `EventRepeatType { none, yearly }` 对齐）。
 */
object RepeatRule {

    /**
     * 返回该事件在 [today] 之后（含当天）的下一个目标时刻。
     * 非重复事件原样返回。
     *
     * 每年重复的语义：月/日取自 `repeatMonth/repeatDay`（缺省取原始目标日期的月/日），
     * 时刻沿用原始目标时刻。
     */
    fun upcomingTarget(event: CountdownEvent, today: LocalDate): LocalDateTime {
        if (event.repeatType != RepeatType.YEARLY) return event.targetDate

        val month = event.repeatMonth ?: event.targetDate.monthValue
        val day = event.repeatDay ?: event.targetDate.dayOfMonth
        val time = event.targetDate.toLocalTime()

        var candidate = CalendarMath.anniversaryOn(today.year, month, day)
        if (candidate.isBefore(today)) {
            candidate = CalendarMath.anniversaryOn(today.year + 1, month, day)
        }
        return candidate.atTime(time)
    }

    /** 该事件在 [today] 是否为「当天到期」（含重复事件的当年周年）。 */
    fun isDueToday(event: CountdownEvent, today: LocalDate): Boolean =
        upcomingTarget(event, today).toLocalDate() == today

    /**
     * 距离下一次目标还有多少天（未来为正，当天为 0）。
     */
    fun daysUntilNext(event: CountdownEvent, today: LocalDate): Int =
        CalendarMath.daysUntil(upcomingTarget(event, today).toLocalDate(), today)
}
