package com.jiuxina.ying.ui.event

import com.jiuxina.ying.domain.model.CountDirection
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** 表单校验结果。目前只有标题需要拦，留成结构体方便后续加规则。 */
data class EventFormErrors(val title: String? = null) {
    val isValid: Boolean get() = title == null
}

/**
 * 新建/编辑表单状态（纯数据，可单测）。
 *
 * 时间语义：全天事件把时刻归一到 00:00，提醒锚点由 `CountdownEvent.reminderAnchor` 统一处理（09:00）。
 */
data class EventFormState(
    val id: String? = null,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(9, 0),
    val isAllDay: Boolean = false,
    val category: String = CountdownEvent.DEFAULT_CATEGORY,
    val note: String = "",
    val icon: String = "",
    val direction: CountDirection = CountDirection.AUTO,
    val reminders: List<EventReminder> = emptyList(),
    val repeatYearly: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    /** 落库用的目标时刻。 */
    val target: LocalDateTime
        get() = LocalDateTime.of(date, if (isAllDay) LocalTime.MIDNIGHT else time)

    fun validate(): EventFormErrors =
        EventFormErrors(title = if (title.isBlank()) "请输入标题" else null)

    /** 校验通过才返回事件，否则 null（调用方负责提示错误）。 */
    fun toEvent(): CountdownEvent? {
        if (!validate().isValid) return null
        return CountdownEvent(
            id = id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            targetDate = target,
            category = category.ifBlank { CountdownEvent.DEFAULT_CATEGORY },
            note = note.trim(),
            icon = icon,
            direction = direction,
            reminders = EventReminder.normalize(reminders),
            isAllDay = isAllDay,
            repeatType = if (repeatYearly) RepeatType.YEARLY else RepeatType.NONE,
            repeatMonth = if (repeatYearly) date.monthValue else null,
            repeatDay = if (repeatYearly) date.dayOfMonth else null,
            createdAt = createdAt,
        )
    }

    /** 切换全天：切回非全天时保留用户原先选的时分。 */
    fun withAllDay(value: Boolean): EventFormState = copy(isAllDay = value)

    /** 改月份/年份时把日夹到该月最大天数（1 月 31 日 → 2 月 28/29 日）。 */
    fun withDate(value: LocalDate): EventFormState {
        val safeDay = value.dayOfMonth.coerceAtMost(YearMonth.from(value).lengthOfMonth())
        return copy(date = value.withDayOfMonth(safeDay))
    }

    fun addReminder(minutesBefore: Int): EventFormState {
        if (minutesBefore < 0) return this
        if (reminders.size >= EventReminder.MAX_COUNT) return this
        if (reminders.any { it.minutesBefore == minutesBefore }) return this
        return copy(
            reminders = EventReminder.normalize(
                reminders + EventReminder(id = UUID.randomUUID().toString(), minutesBefore = minutesBefore),
            ),
        )
    }

    fun removeReminder(minutesBefore: Int): EventFormState =
        copy(reminders = reminders.filterNot { it.minutesBefore == minutesBefore })

    fun toggleReminder(minutesBefore: Int): EventFormState =
        if (reminders.any { it.minutesBefore == minutesBefore }) {
            removeReminder(minutesBefore)
        } else {
            addReminder(minutesBefore)
        }

    companion object {
        /** 预置分类。M2b2 会把它换成可增删的存储，当前先用常量保证表单可用。 */
        val DEFAULT_CATEGORIES = listOf("生活", "生日", "纪念", "节日", "工作", "学习", "旅行")

        /** 提醒快捷选项（分钟）。 */
        val REMINDER_PRESETS = listOf(0, 10, 30, 60, 180, 720, 1440, 2880, 10080)

        fun fromEvent(event: CountdownEvent): EventFormState = EventFormState(
            id = event.id,
            title = event.title,
            date = event.targetDate.toLocalDate(),
            time = event.targetDate.toLocalTime(),
            isAllDay = event.isAllDay,
            category = event.category,
            note = event.note,
            icon = event.icon,
            direction = event.direction,
            reminders = event.reminders,
            repeatYearly = event.repeatsYearly,
            createdAt = event.createdAt,
        )

        /** 快捷日期：今天 / 明天 / 一周后 / 月底 / 年底。 */
        fun quickDates(today: LocalDate = LocalDate.now()): List<Pair<String, LocalDate>> = listOf(
            "今天" to today,
            "明天" to today.plusDays(1),
            "一周后" to today.plusWeeks(1),
            "月底" to today.with(TemporalAdjusters.lastDayOfMonth()),
            "年底" to LocalDate.of(today.year, 12, 31),
        )

        /**
         * 按年月构造日期，日超出该月天数时夹取（1 月 31 日切到 2 月 → 28/29 日）。
         * 滚轮换月时用，避免 `LocalDate.of` 抛 `DateTimeException`。
         */
        fun safeDate(year: Int, month: Int, day: Int): LocalDate =
            com.jiuxina.ying.domain.time.CalendarMath.dateClamped(year, month, day)
    }
}
