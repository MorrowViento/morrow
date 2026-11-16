package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventPrimaryAction
import com.morrowviento.morrow.domain.model.RepeatType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 原日期始终作为锚点；短月夹取不改变后续月份的重复日。 */
object RepeatRule {
    fun upcomingTarget(event: CountdownEvent, today: LocalDate): LocalDateTime {
        if (event.repeatType == RepeatType.NONE) return event.targetDate
        return futureOccurrences(event, today).first().atTime(event.targetDate.toLocalTime())
    }

    fun futureOccurrences(event: CountdownEvent, from: LocalDate): Sequence<LocalDate> {
        val lower = maxOf(from, event.dateOnly, event.lastHandledOccurrence?.plusDays(1) ?: event.dateOnly)
        return occurrencesFrom(event, lower)
    }

    /** 日历查询不使用处理游标，处理过的周期仍是系列历史。 */
    fun occurrencesBetween(event: CountdownEvent, start: LocalDate, end: LocalDate): List<LocalDate> =
        occurrencesFrom(event, maxOf(start, event.dateOnly)).takeWhile { it <= end }.toList()

    private fun occurrencesFrom(event: CountdownEvent, lower: LocalDate): Sequence<LocalDate> {
        if (event.repeatType == RepeatType.NONE) {
            return if (event.dateOnly >= lower) sequenceOf(event.dateOnly) else emptySequence()
        }
        val day = (event.repeatDay ?: event.targetDate.dayOfMonth).coerceIn(1, 31)
        val month = (event.repeatMonth ?: event.targetDate.monthValue).coerceIn(1, 12)
        fun inMonth(ym: YearMonth): LocalDate = CalendarMath.dateClamped(ym.year, ym.monthValue, day)
        fun inYear(year: Int): LocalDate = CalendarMath.dateClamped(year, month, day)
        var first = if (event.repeatType == RepeatType.MONTHLY) inMonth(YearMonth.from(lower)) else inYear(lower.year)
        fun next(date: LocalDate): LocalDate =
            if (event.repeatType == RepeatType.MONTHLY) inMonth(YearMonth.from(date).plusMonths(1)) else inYear(date.year + 1)
        if (first < lower) first = next(first)
        return generateSequence(first, ::next)
    }

    /** 调用方在事件操作锁内核验快照；周期日期校验也阻止迟到动作推进新周期。 */
    fun applyPrimaryAction(event: CountdownEvent, today: LocalDate, expectedOccurrence: LocalDate): CountdownEvent? =
        when (event.primaryAction) {
            EventPrimaryAction.NONE -> null
            EventPrimaryAction.RESTORE -> event.copy(isCompleted = false)
            EventPrimaryAction.COMPLETE -> if (expectedOccurrence == event.dateOnly) event.copy(isCompleted = true) else null
            EventPrimaryAction.NEXT_MONTH, EventPrimaryAction.NEXT_YEAR ->
                if (upcomingTarget(event, today).toLocalDate() == expectedOccurrence) {
                    event.copy(lastHandledOccurrence = expectedOccurrence, isCompleted = false)
                } else null
        }

    fun isDueToday(event: CountdownEvent, today: LocalDate): Boolean =
        upcomingTarget(event, today).toLocalDate() == today

    fun daysUntilNext(event: CountdownEvent, today: LocalDate): Int =
        CalendarMath.daysUntil(upcomingTarget(event, today).toLocalDate(), today)
}
