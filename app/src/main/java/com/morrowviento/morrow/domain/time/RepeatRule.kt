package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventPrimaryAction
import com.morrowviento.morrow.domain.model.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 重复规则推导（纯函数）。
 *
 * **原日期始终是锚点**：短月夹取（1 月 31 日 → 2 月 28 日）不改变后续月份的重复日，
 * 否则一次二月就让整条规则永久漂移。
 *
 * 四档重复各自的推进方式：
 * - [RepeatType.DAILY]：锚点 + 间隔天数；
 * - [RepeatType.WEEKLY]：以锚点所在周的周一为格点，每 N 周取选中的星期，
 *   没选就取锚点那一天；
 * - [RepeatType.MONTHLY]：每 N 月取锚点日（夹到月末）；
 * - [RepeatType.YEARLY]：每 N 年取锚点月日；[CountdownEvent.repeatOnLunar] 为真时
 *   锚点的月日按**农历**解释，逐年换算回公历（农历生日、忌日走这条路）。
 *
 * 结束条件只有 [CountdownEvent.repeatUntil]（含当天）。序列在到达该日之后自然终止，
 * 于是「已结束的系列」不需要额外状态——`firstOrNull()` 为空就是结束。
 */
object RepeatRule {

    fun upcomingTarget(event: CountdownEvent, today: LocalDate): LocalDateTime {
        val time = event.targetDate.toLocalTime()
        return (futureOccurrences(event, today).firstOrNull() ?: event.dateOnly).atTime(time)
    }

    /**
     * 从 [from] 起的未来落点（起点日，不含持续区间的中间日）。
     *
     * 会同时尊重「已处理游标」：用户点过「下一次」之后，那一次不该再被算成未来。
     */
    fun futureOccurrences(event: CountdownEvent, from: LocalDate): Sequence<LocalDate> {
        val lower = maxOf(
            from,
            event.dateOnly,
            event.lastHandledOccurrence?.plusDays(1) ?: event.dateOnly,
        )
        return occurrencesFrom(event, lower)
    }

    /** 日历查询不使用处理游标，处理过的周期仍是系列历史。 */
    fun occurrencesBetween(event: CountdownEvent, start: LocalDate, end: LocalDate): List<LocalDate> =
        occurrencesFrom(event, maxOf(start, event.dateOnly)).takeWhile { it <= end }.toList()

    /**
     * 区间内被覆盖的**所有**日期，含持续事件的中间日。
     *
     * 日历格子与月历标记要的是「这一天有没有这件事」，持续 5 天的出差必须在中间三天
     * 也亮起来。单日事件下结果与 [occurrencesBetween] 相同。
     */
    fun coverageBetween(event: CountdownEvent, start: LocalDate, end: LocalDate): List<LocalDate> {
        if (start > end) return emptyList()
        val span = event.spanDays
        // 起点最早可能是 end - span：一次发生在区间之前、但尾巴伸进区间里的持续事件
        val earliest = if (span == 0) start else start.minusDays(span.toLong())
        val lower = maxOf(earliest, event.dateOnly)
        return occurrencesFrom(event, lower)
            .takeWhile { it <= end }
            .flatMap { day -> (0..span).asSequence().map { day.plusDays(it.toLong()) } }
            .filter { it >= start && it <= end }
            .distinct()
            .toList()
            .sorted()
    }

    /** 某一天是否被覆盖（持续事件的中间日也算）。 */
    fun coversOn(event: CountdownEvent, date: LocalDate): Boolean = coveringEnd(event, date) != null

    /**
     * 覆盖 [date] 的那一次发生的**起始日**；不在任何区间内返回 null。
     *
     * 进行中的持续事件，卡片要的是「这次是哪天开始的」——不是「下一次是哪天」。
     */
    fun coveringStart(event: CountdownEvent, date: LocalDate): LocalDate? {
        val span = event.spanDays.toLong()
        val earliest = date.minusDays(span)
        return occurrencesFrom(event, maxOf(earliest, event.dateOnly))
            .takeWhile { it <= date }
            .lastOrNull { it.plusDays(span) >= date }
    }

    /**
     * 覆盖 [date] 的那一次发生的**最后一天**；不在任何区间内返回 null。
     *
     * 卡片要显示「还剩几天」，光知道「今天在里面」不够——得知道这次到哪天结束。
     */
    fun coveringEnd(event: CountdownEvent, date: LocalDate): LocalDate? =
        coveringStart(event, date)?.plusDays(event.spanDays.toLong())

    /** 系列是否已经走到 [CountdownEvent.repeatUntil] 之后（非重复事件恒为 false）。 */
    fun isSeriesEnded(event: CountdownEvent, today: LocalDate): Boolean {
        if (event.repeatType == RepeatType.NONE) return false
        // 有界才能安全地取空（无限序列 firstOrNull 会立刻返回，也不会挂）
        return futureOccurrences(event, today).firstOrNull() == null
    }

    private fun occurrencesFrom(event: CountdownEvent, lower: LocalDate): Sequence<LocalDate> {
        val raw = rawOccurrences(event, lower)
        val until = event.repeatUntil ?: return raw
        return raw.takeWhile { it <= until }
    }

    private fun rawOccurrences(event: CountdownEvent, lower: LocalDate): Sequence<LocalDate> {
        val anchor = event.dateOnly
        val step = event.repeatInterval.coerceIn(1, CountdownEvent.MAX_REPEAT_INTERVAL)
        return when (event.repeatType) {
            RepeatType.NONE ->
                if (anchor >= lower) sequenceOf(anchor) else emptySequence()

            RepeatType.DAILY -> {
                val days = ChronoUnit.DAYS.between(anchor, lower)
                val skips = if (days <= 0) 0 else (days + step - 1) / step
                generateSequence(anchor.plusDays(skips * step)) { it.plusDays(step.toLong()) }
            }

            RepeatType.WEEKLY -> {
                val weekdays = weekdaySet(event)
                val firstWeek = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeksApart = ChronoUnit.DAYS.between(firstWeek, lower) / 7
                val aligned = if (weeksApart <= 0) 0L else (weeksApart / step) * step
                generateSequence(firstWeek.plusWeeks(aligned)) { it.plusWeeks(step.toLong()) }
                    .flatMap { week ->
                        weekdays.asSequence().map { week.plusDays((it.value - 1).toLong()) }
                    }
                    .filter { it >= lower }
            }

            RepeatType.MONTHLY -> {
                val day = (event.repeatDay ?: anchor.dayOfMonth).coerceIn(1, 31)
                val firstMonth = YearMonth.from(anchor)
                val monthsApart = ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(lower))
                val aligned = if (monthsApart <= 0) 0L else (monthsApart / step) * step
                generateSequence(firstMonth.plusMonths(aligned)) { it.plusMonths(step.toLong()) }
                    .map { CalendarMath.dateClamped(it.year, it.monthValue, day) }
                    .filter { it >= lower }
            }

            RepeatType.YEARLY -> if (event.repeatOnLunar) {
                lunarYearly(event, anchor, lower, step)
            } else {
                val month = (event.repeatMonth ?: anchor.monthValue).coerceIn(1, 12)
                val day = (event.repeatDay ?: anchor.dayOfMonth).coerceIn(1, 31)
                val yearsApart = lower.year - anchor.year
                val aligned = if (yearsApart <= 0) 0 else (yearsApart / step) * step
                generateSequence(anchor.year + aligned) { it + step }
                    .map { CalendarMath.dateClamped(it, month, day) }
                    .filter { it >= lower }
            }
        }
    }

    /**
     * 农历年循环。
     *
     * 月日按农历解释，逐年换算回公历；农历三十碰上小月时取廿九（[LunarCalendar.toSolar]
     * 负责夹取）。闰月不参与：闰四月的生日按四月过，这是通行做法，也避免了
     * 「某些年根本没有闰四月」导致整年跳过。
     */
    private fun lunarYearly(
        event: CountdownEvent,
        anchor: LocalDate,
        lower: LocalDate,
        step: Int,
    ): Sequence<LocalDate> {
        val anchorLunar = LunarCalendar.fromSolar(anchor)
        // 月日以**锚点换算出的农历**为准，repeatMonth/repeatDay 只在锚点超出年表时兜底。
        // 这两个字段在公历重复里是公历月日、在农历重复里是农历月日，单看数值分不出来；
        // 锚点日期是用户当初挑的那一天，永远是权威来源。
        val month = (anchorLunar?.month ?: event.repeatMonth ?: anchor.monthValue).coerceIn(1, 12)
        val day = (anchorLunar?.day ?: event.repeatDay ?: anchor.dayOfMonth).coerceIn(1, 30)
        val baseYear = anchorLunar?.year ?: anchor.year
        val lowerLunarYear = LunarCalendar.fromSolar(lower)?.year ?: baseYear
        val yearsApart = lowerLunarYear - baseYear
        val aligned = if (yearsApart <= 0) 0 else (yearsApart / step) * step
        return generateSequence(baseYear + aligned) { it + step }
            .takeWhile { it <= LunarCalendar.MAX_YEAR }
            .mapNotNull { LunarCalendar.toSolar(it, month, day) }
            .filter { it >= lower }
    }

    /** 每周重复的星期集合；没选就取锚点那一天。升序，保证同周内日期递增。 */
    private fun weekdaySet(event: CountdownEvent): List<DayOfWeek> {
        val values = event.repeatWeekdays.filter { it in 1..7 }.toSortedSet()
        if (values.isEmpty()) return listOf(event.dateOnly.dayOfWeek)
        return values.map { DayOfWeek.of(it) }
    }

    /** 调用方在事件操作锁内核验快照；周期日期校验也阻止迟到动作推进新周期。 */
    fun applyPrimaryAction(event: CountdownEvent, today: LocalDate, expectedOccurrence: LocalDate): CountdownEvent? =
        when (event.primaryAction) {
            EventPrimaryAction.NONE -> null
            EventPrimaryAction.RESTORE -> event.copy(isCompleted = false)
            EventPrimaryAction.COMPLETE -> if (expectedOccurrence == event.dateOnly) event.copy(isCompleted = true) else null
            EventPrimaryAction.NEXT_MONTH,
            EventPrimaryAction.NEXT_YEAR,
            EventPrimaryAction.NEXT_OCCURRENCE ->
                if (upcomingTarget(event, today).toLocalDate() == expectedOccurrence) {
                    event.copy(lastHandledOccurrence = expectedOccurrence, isCompleted = false)
                } else null
        }

    fun isDueToday(event: CountdownEvent, today: LocalDate): Boolean =
        upcomingTarget(event, today).toLocalDate() == today

    fun daysUntilNext(event: CountdownEvent, today: LocalDate): Int =
        CalendarMath.daysUntil(upcomingTarget(event, today).toLocalDate(), today)
}
