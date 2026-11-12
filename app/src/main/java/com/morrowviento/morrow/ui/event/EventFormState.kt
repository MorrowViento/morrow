package com.morrowviento.morrow.ui.event

import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
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
 * 时间语义：新建时日期默认今天、时刻默认**此刻**（[newEvent]），不再固定 09:00——
 * 「现在建一条」绝大多数情况下就是「从现在开始算」，固定 9 点会让当天 9 点以后新建的事件
 * 一保存就是过去的时刻。全天事件把时刻归一到 00:00，提醒锚点由
 * `CountdownEvent.reminderAnchor` 统一处理（09:00）。
 */
data class EventFormState(
    val id: String? = null,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
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

    /**
     * 跟随分类改名 / 删除迁移：把选中的分类沿 [aliases] 解到最终名称（支持 A→B→C 链式改名）。
     *
     * 表单只持有分类**名字**。改名后不同步的话，chip 行会把旧名当成「孤儿分类」并排显示，
     * 用户不手动点一下新名就直接保存——旧名被写回事件里，于是新旧两个分类长期并存。
     * 删除同理：留着已删除的名字保存，只会把它原样复活。
     */
    fun withCategoryAlias(aliases: Map<String, String>): EventFormState {
        if (aliases.isEmpty()) return this
        var name = category.trim()
        repeat(aliases.size + 1) {
            val next = aliases[name] ?: return if (name == category) this else copy(category = name)
            name = next
        }
        return if (name == category) this else copy(category = name)
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

        /**
         * 新建表单：日期默认 [date]（今天，或日历里点的某天），时刻默认**此刻**。
         *
         * 秒按分钟截断：滚轮只有时/分两列，留着秒数会让「现在建的这条」比表盘上显示的时刻晚几十秒，
         * 保存后卡片上的剩余时间看起来对不上。
         */
        /**
         * 新建表单的初值。
         *
         * [date] 传 null 时**从 [now] 取日期**，不能各自 `LocalDate.now()` / `LocalDateTime.now()`
         * 调一次：跨零点（23:59:59.9 调第一次、00:00:00.1 调第二次）会得到「今天的日期 + 昨天的
         * 时分」，卡片上的倒计时立刻差一天。单测 `newEvent_defaultsToTodayAndCurrentMinute`
         * 在凌晨运行时就是这么失败的。
         */
        fun newEvent(
            date: LocalDate? = null,
            now: LocalDateTime = LocalDateTime.now(),
        ): EventFormState {
            val clock = now.withSecond(0).withNano(0)
            return EventFormState(
                date = date ?: clock.toLocalDate(),
                time = clock.toLocalTime(),
            )
        }

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
            com.morrowviento.morrow.domain.time.CalendarMath.dateClamped(year, month, day)
    }
}
