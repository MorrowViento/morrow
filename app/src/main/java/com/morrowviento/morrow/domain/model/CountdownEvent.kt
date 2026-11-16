package com.morrowviento.morrow.domain.model

/**
 * 倒数日事件。保留 ying 原事件字段，新增业务类型与已处理周期，旧事件默认按待办兼容。
 */
data class CountdownEvent(
    val id: String,
    val title: String,
    /** 目标时刻（本地时区）。全天事件时仅日期部分有意义。 */
    val targetDate: java.time.LocalDateTime,
    val category: String = DEFAULT_CATEGORY,
    val note: String = "",
    val icon: String = "",
    val direction: CountDirection = CountDirection.AUTO,
    val reminders: List<EventReminder> = emptyList(),
    val isAllDay: Boolean = false,
    val isCompleted: Boolean = false,
    val isPinned: Boolean = false,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatMonth: Int? = null,
    val repeatDay: Int? = null,
    val createdAt: java.time.LocalDateTime,
    val kind: EventKind = EventKind.TODO,
    val lastHandledOccurrence: java.time.LocalDate? = null,
) {
    val primaryAction: EventPrimaryAction
        get() = when {
            kind == EventKind.TODO && isCompleted -> EventPrimaryAction.RESTORE
            repeatType == RepeatType.YEARLY -> EventPrimaryAction.NEXT_YEAR
            repeatType == RepeatType.MONTHLY -> EventPrimaryAction.NEXT_MONTH
            kind == EventKind.TODO -> EventPrimaryAction.COMPLETE
            else -> EventPrimaryAction.NONE
        }
    /** 仅日期部分（等价 Dart 侧 `dateOnly`）。 */
    val dateOnly: java.time.LocalDate get() = targetDate.toLocalDate()

    /**
     * 提醒锚点：全天事件取当天 09:00（与原实现一致），否则取目标时刻。
     */
    val reminderAnchor: java.time.LocalDateTime
        get() = if (isAllDay) targetDate.toLocalDate().atTime(9, 0) else targetDate

    val repeatsYearly: Boolean get() = repeatType == RepeatType.YEARLY

    /** 与今天相差的日历天数（未来为正）。 */
    fun dayDelta(today: java.time.LocalDate = java.time.LocalDate.now()): Int =
        com.morrowviento.morrow.domain.time.CalendarMath.calendarDayDelta(today, dateOnly)

    /** 是否显示为“已经 N 天”（auto 模式下过期即累加）。 */
    fun isCountingUp(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
        direction == CountDirection.COUNTUP ||
            (direction == CountDirection.AUTO && dayDelta(today) < 0)

    fun displayDays(today: java.time.LocalDate = java.time.LocalDate.now()): Int =
        kotlin.math.abs(dayDelta(today))

    companion object {
        const val DEFAULT_CATEGORY = "生活"
    }
}

enum class CountDirection { AUTO, COUNTDOWN, COUNTUP }

enum class EventKind(val label: String) { TODO("待办"), ANNIVERSARY("纪念日") }

enum class RepeatType(val label: String) { NONE("不重复"), MONTHLY("每月重复"), YEARLY("每年重复") }

enum class EventPrimaryAction(val label: String) {
    NONE(""), COMPLETE("标记完成"), RESTORE("恢复为进行中"), NEXT_MONTH("进入下一月"), NEXT_YEAR("进入下一年"),
}
