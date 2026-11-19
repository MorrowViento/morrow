package com.morrowviento.morrow.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 倒数日事件。保留 ying 原事件字段，新增业务类型与已处理周期，旧事件默认按待办兼容。
 *
 * 4.2.0 起重复规则从「每月 / 每年」两个固定档扩成完整的 DAILY/WEEKLY/MONTHLY/YEARLY，
 * 并加上间隔、周几、结束日期、农历年循环与跨多天的持续区间。新增字段全部带默认值，
 * 旧数据反序列化后即等价于「不重复 / 单日」，不需要额外的补丁层。
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
    /**
     * 重复锚点的「月」。
     *
     * 公历年循环时是公历月；[repeatOnLunar] 为 true 时是**农历月**（1..12）。
     */
    val repeatMonth: Int? = null,
    /** 重复锚点的「日」。农历循环时是农历日（1..30）。 */
    val repeatDay: Int? = null,
    /** 每隔几个周期发生一次（每天/每 2 周/每 3 个月…），最小 1。 */
    val repeatInterval: Int = 1,
    /**
     * 每周重复时选中的星期（ISO 1=周一 … 7=周日），升序去重。
     *
     * 空集表示「跟锚点同一天」。存成集合而不是位掩码：可读、可直接进 JSON、
     * 也不需要在三处（Room / 备份 / 撤销）各维护一份位运算。
     */
    val repeatWeekdays: Set<Int> = emptySet(),
    /** 年循环按农历（生日、忌日这类只认农历的日子）。 */
    val repeatOnLunar: Boolean = false,
    /** 重复结束日期（含当天）。null 表示永不结束。 */
    val repeatUntil: LocalDate? = null,
    /**
     * 持续事件（放假、出差、旅行）的结束日期（含当天）。
     *
     * 为 null 或等于起始日即单日事件。重复事件的每次发生都按同样的跨度平移，
     * 因此这里存的是**绝对结束日**，只有起点由重复规则推进。
     */
    val endDate: LocalDate? = null,
    /**
     * 持续事件结束日的具体时刻；null 表示全天事件，或非全天但结束时刻沿用开始时刻。
     *
     * 只对「多天 + 非全天」的事件有意义：9/1 14:00 → 9/5 18:00 这类安排里，
     * 结束日的 18:00 与开始日的 14:00 分开存。提醒与倒计时仍按第一天，这里只服务展示。
     */
    val endTime: LocalTime? = null,
    val createdAt: java.time.LocalDateTime,
    val kind: EventKind = EventKind.TODO,
    val lastHandledOccurrence: java.time.LocalDate? = null,
    /**
     * 多维标记：一条事件可挂多个标签（见 [EventTags]），与单值的 [category] 是两个维度。
     * 已归一化（去重、限长、限个数），因此可以直接比较与原样落库。
     */
    val tags: List<String> = emptyList(),
    /**
     * 归档时刻；非 null 即已归档。
     *
     * **归档不是完成**：[isCompleted] 表示「这件事办到了」，会留在首页「已完成」区、
     * 在日历上仍然出现；归档表示「我不想再在清单里看到它」（放弃了、过期了、暂时搁置），
     * 因此首页、日历、小部件、提醒全部把它排除，也不会进入「已完成」的计数，
     * 只能从归档箱里找回。两者可以同时存在（完成后归档就是「收进抽屉」）。
     */
    val archivedAt: java.time.LocalDateTime? = null,
    /**
     * 预计时长（分钟）。0 表示未指定，时间块视图按 [DEFAULT_BLOCK_MINUTES] 画。
     * 只影响时间块的高度，不改变提醒与倒计时语义。
     */
    val durationMinutes: Int = 0,
    /**
     * 子任务清单（有序、可勾选）。存法与提醒一致：事件表一列 JSON，见 [EventChecklist]。
     *
     * 子任务**不改变**事件自身的完成语义：[isCompleted] 仍由「标记完成」动作决定。
     * 全部子任务勾完不会自动把事件置为已完成——那会让「编辑一次标题」把完成态重置掉
     * （`EventFormState.toEvent` 里 `isCompleted` 仍沿用 existing）。
     */
    val checklist: List<ChecklistItem> = emptyList(),
    /**
     * 「第 N 天」里程碑（见 [EventMilestones]）。只在 [kind] 为 [EventKind.ANNIVERSARY] 时生效。
     *
     * 与 [reminders] 是两个维度：提醒跟着重复规则走，里程碑的锚点是**绝对**的
     * （第 100 天一辈子只有一次），因此不参与重复推导。
     */
    val milestones: List<Int> = emptyList(),
) {
    val isArchived: Boolean get() = archivedAt != null

    /**
     * 真正会排提醒的里程碑：非纪念日一律空。
     *
     * 存的时候不按类型过滤（用户可能把纪念日改回待办又改回来，里程碑不该在这一步被抹掉），
     * 但**排期与展示只看这个**——待办带着「第 100 天」是没有意义的。
     */
    val activeMilestones: List<Int>
        get() = if (kind == EventKind.ANNIVERSARY) milestones else emptyList()

    /** 从第一天数到今天过了几天（当天为 0）。纪念日卡片上的「已经 137 天」用它。 */
    fun elapsedDays(today: java.time.LocalDate = java.time.LocalDate.now()): Long =
        java.time.temporal.ChronoUnit.DAYS.between(dateOnly, today)

    /**
     * 下一个还没到的里程碑（今天当天算「到」）及其公历日期；全部走完则为 null。
     *
     * 用「日期不早于今天」而不是「剩余天数 > 0」：第 100 天当天打开应用，
     * 应该看到「今天 · 第 100 天」，而不是直接跳到第 300 天。
     */
    fun nextMilestone(today: java.time.LocalDate = java.time.LocalDate.now()):
        Pair<Int, java.time.LocalDate>? = activeMilestones
        .asSequence()
        .map { it to dateOnly.plusDays(it.toLong()) }
        .firstOrNull { (_, at) -> !at.isBefore(today) }

    /** 已勾选的子任务数。 */
    val checklistDone: Int get() = checklist.count { it.done }

    /** 子任务完成度（0..1）；空清单为 0。 */
    val checklistProgress: Float get() = EventChecklist.progress(checklist)

    val hasChecklist: Boolean get() = checklist.isNotEmpty()

    /** 时间块视图里这个事件占多高：未指定时长时用一个格的默认值。 */
    val blockMinutes: Int get() = if (durationMinutes > 0) durationMinutes else DEFAULT_BLOCK_MINUTES
    val primaryAction: EventPrimaryAction
        get() = when {
            kind == EventKind.TODO && isCompleted -> EventPrimaryAction.RESTORE
            repeatType == RepeatType.YEARLY -> EventPrimaryAction.NEXT_YEAR
            repeatType == RepeatType.MONTHLY -> EventPrimaryAction.NEXT_MONTH
            repeatType == RepeatType.DAILY || repeatType == RepeatType.WEEKLY ->
                EventPrimaryAction.NEXT_OCCURRENCE
            kind == EventKind.TODO -> EventPrimaryAction.COMPLETE
            else -> EventPrimaryAction.NONE
        }
    /** 仅日期部分（等价 Dart 侧 `dateOnly`）。 */
    val dateOnly: java.time.LocalDate get() = targetDate.toLocalDate()

    /** 持续天数：单日事件为 0，跨 N 天为 N（起止都含）。 */
    val spanDays: Int
        get() = endDate?.let { java.time.temporal.ChronoUnit.DAYS.between(dateOnly, it).toInt() }
            ?.coerceAtLeast(0)
            ?: 0

    val isMultiDay: Boolean get() = spanDays > 0

    /** 区间内的最后一天；单日事件就是起始日。 */
    val lastDay: java.time.LocalDate get() = dateOnly.plusDays(spanDays.toLong())

    /**
     * 提醒锚点：全天事件取当天 09:00（与原实现一致），否则取目标时刻。
     * 持续事件仍按**第一天**提醒——用户要的是「出发前提醒」，不是「结束前提醒」。
     */
    val reminderAnchor: java.time.LocalDateTime
        get() = if (isAllDay) targetDate.toLocalDate().atTime(9, 0) else targetDate

    val repeatsYearly: Boolean get() = repeatType == RepeatType.YEARLY

    /** 今天是否落在某次发生的区间内（持续事件中间的日子也算）。 */
    fun covers(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
        com.morrowviento.morrow.domain.time.RepeatRule.coversOn(this, today)

    /** 与今天相差的日历天数（未来为正）。持续事件按**开头**算。 */
    fun dayDelta(today: java.time.LocalDate = java.time.LocalDate.now()): Int =
        com.morrowviento.morrow.domain.time.CalendarMath.calendarDayDelta(today, dateOnly)

    /**
     * 是否显示为「已经 N 天」（auto 模式下过期即累加）。
     *
     * 持续事件进行中不算「已经过去」：9 月 1 日到 9 月 5 日的出差，9 月 3 日打开应用
     * 应该看到「进行中 · 还有 2 天」，而不是「已经过了 2 天」。
     */
    fun isCountingUp(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
        direction == CountDirection.COUNTUP ||
            (direction == CountDirection.AUTO && dayDelta(today) < 0 && !covers(today))

    fun displayDays(today: java.time.LocalDate = java.time.LocalDate.now()): Int =
        kotlin.math.abs(dayDelta(today))

    companion object {
        const val DEFAULT_CATEGORY = "生活"
        /** 间隔上限：再大也没有实际意义，且能挡住手改备份写进 100000 拖慢排期。 */
        const val MAX_REPEAT_INTERVAL = 99
        /** 未指定时长时，时间块按一小时画。 */
        const val DEFAULT_BLOCK_MINUTES = 60
        /** 时长上限一天：跨天的安排应该用「持续」而不是把块画到 30 小时。 */
        const val MAX_DURATION_MINUTES = 24 * 60
    }
}

enum class CountDirection { AUTO, COUNTDOWN, COUNTUP }

enum class EventKind(val label: String) { TODO("待办"), ANNIVERSARY("纪念日") }

enum class RepeatType(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年"),
}

enum class EventPrimaryAction(val label: String) {
    NONE(""),
    COMPLETE("标记完成"),
    RESTORE("恢复为进行中"),
    NEXT_MONTH("进入下一月"),
    NEXT_YEAR("进入下一年"),
    /** 每天 / 每周重复：把本次标成已处理，卡片跳到下一次。 */
    NEXT_OCCURRENCE("下一次"),
}
