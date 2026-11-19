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
    val repeatType: RepeatType = RepeatType.NONE,
    /** 每隔几个周期（每天 / 每 2 周 / 每 3 个月 / 每 2 年）。 */
    val repeatInterval: Int = 1,
    /** 每周重复选中的星期（ISO 1=周一 … 7=周日）。 */
    val repeatWeekdays: Set<Int> = emptySet(),
    /** 年循环按农历（生日、忌日）。 */
    val repeatOnLunar: Boolean = false,
    /** 重复结束日期；null 表示永不结束。 */
    val repeatUntil: LocalDate? = null,
    /** 持续事件结束日期（含当天）；null 或等于开始日即单日。 */
    val endDate: LocalDate? = null,
    /** 持续事件结束日的具体时刻；null 表示全天事件或沿用开始时刻。 */
    val endTime: LocalTime? = null,
    val kind: com.morrowviento.morrow.domain.model.EventKind = com.morrowviento.morrow.domain.model.EventKind.TODO,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /** 多维标记（标签）的编辑态；保存时统一走 `EventTags.normalize`。 */
    val tags: List<String> = emptyList(),
    /** 预计时长（分钟），0 = 未指定。 */
    val durationMinutes: Int = 0,
    /** 子任务清单（有序、可勾选）。保存时统一走 `EventChecklist.normalize`。 */
    val checklist: List<com.morrowviento.morrow.domain.model.ChecklistItem> = emptyList(),
) {
    /** 落库用的目标时刻。 */
    val target: LocalDateTime
        get() = LocalDateTime.of(date, if (isAllDay) LocalTime.MIDNIGHT else time)

    fun validate(): EventFormErrors =
        EventFormErrors(title = if (title.isBlank()) "请输入标题" else null)

    /** 校验通过才返回事件，否则 null（调用方负责提示错误）。 */
    fun toEvent(existing: CountdownEvent? = null): CountdownEvent? {
        if (!validate().isValid) return null
        // 锚点没变就不重算月日：用户只改了标题时，农历生日不能被「按今天的公历」重写一遍
        val sameAnchor = existing != null &&
            existing.targetDate == target &&
            existing.repeatType == repeatType &&
            existing.repeatInterval == repeatInterval &&
            existing.repeatWeekdays == repeatWeekdays &&
            existing.repeatOnLunar == repeatOnLunar
        val lunarAnchor = if (repeatType == RepeatType.YEARLY && repeatOnLunar) {
            com.morrowviento.morrow.domain.time.LunarCalendar.fromSolar(date)
        } else {
            null
        }
        val safeEndDate = endDate?.takeIf { it > date }
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
            repeatType = repeatType,
            repeatMonth = when {
                sameAnchor -> existing.repeatMonth
                repeatType == RepeatType.YEARLY ->
                    if (repeatOnLunar) lunarAnchor?.month ?: date.monthValue else date.monthValue
                else -> null
            },
            repeatDay = when {
                sameAnchor -> existing.repeatDay
                repeatType == RepeatType.YEARLY && repeatOnLunar ->
                    lunarAnchor?.day ?: date.dayOfMonth
                repeatType != RepeatType.NONE -> date.dayOfMonth
                else -> null
            },
            repeatInterval = repeatInterval.coerceIn(1, CountdownEvent.MAX_REPEAT_INTERVAL),
            repeatWeekdays = if (repeatType == RepeatType.WEEKLY) repeatWeekdays.filter { it in 1..7 }.toSortedSet() else emptySet(),
            repeatOnLunar = repeatType == RepeatType.YEARLY && repeatOnLunar,
            repeatUntil = if (repeatType == RepeatType.NONE) null else repeatUntil,
            // 结束日不早于开始日；等于开始日即单日，归一成 null，避免「持续 1 天」这种冗余状态
            endDate = safeEndDate,
            // 结束时刻只在「多天 + 非全天」时有意义；全天事件整条是全天、单日没有结束日
            endTime = if (isAllDay || safeEndDate == null) null else endTime,
            createdAt = existing?.createdAt ?: createdAt,
            kind = kind,
            isCompleted = kind == com.morrowviento.morrow.domain.model.EventKind.TODO && existing?.isCompleted == true,
            isPinned = existing?.isPinned ?: false,
            lastHandledOccurrence = if (sameAnchor) existing.lastHandledOccurrence else null,
            tags = com.morrowviento.morrow.domain.model.EventTags.normalize(tags),
            // 编辑不该顺手把事件从归档箱里捞出来：归档状态只由「归档 / 恢复」动作改变
            archivedAt = existing?.archivedAt,
            durationMinutes = if (isAllDay) 0 else durationMinutes.coerceIn(0, CountdownEvent.MAX_DURATION_MINUTES),
            checklist = com.morrowviento.morrow.domain.model.EventChecklist.normalize(checklist),
        )
    }

    /** 切换全天：切回非全天时保留用户原先选的时分。切到全天时结束时刻一并清掉。 */
    fun withAllDay(value: Boolean): EventFormState =
        copy(isAllDay = value, endTime = if (value) null else endTime)

    /** 改月份/年份时把日夹到该月最大天数（1 月 31 日 → 2 月 28/29 日）。 */
    fun withDate(value: LocalDate): EventFormState {
        val safeDay = value.dayOfMonth.coerceAtMost(YearMonth.from(value).lengthOfMonth())
        val next = value.withDayOfMonth(safeDay)
        // 开始日挪到结束日之后时，结束日自动作废（否则会出现负跨度）
        return copy(date = next, endDate = endDate?.takeIf { it > next })
    }

    /** 切换重复档位。切到「不重复」时清掉结束条件，避免隐藏状态跟着事件一起落库。 */
    fun withRepeatType(type: RepeatType): EventFormState = copy(
        repeatType = type,
        repeatUntil = if (type == RepeatType.NONE) null else repeatUntil,
        repeatOnLunar = if (type == RepeatType.YEARLY) repeatOnLunar else false,
        repeatWeekdays = if (type == RepeatType.WEEKLY) repeatWeekdays else emptySet(),
    )

    /** 每周重复：点一下选中/取消某个星期；取消到空表示「跟开始日同一天」。 */
    fun toggleWeekday(iso: Int): EventFormState {
        if (iso !in 1..7) return this
        val next = if (iso in repeatWeekdays) repeatWeekdays - iso else repeatWeekdays + iso
        return copy(repeatWeekdays = next.toSortedSet())
    }

    /** 间隔加减，夹在 1..[CountdownEvent.MAX_REPEAT_INTERVAL]。 */
    fun shiftInterval(delta: Int): EventFormState = copy(
        repeatInterval = (repeatInterval + delta).coerceIn(1, CountdownEvent.MAX_REPEAT_INTERVAL),
    )

    fun withRepeatUntil(value: LocalDate?): EventFormState = copy(repeatUntil = value)

    fun withLunarYearly(enabled: Boolean): EventFormState = copy(repeatOnLunar = enabled)

    /** 持续事件的结束日；传 null 表示单日。早于开始日的值直接忽略。 */
    fun withEndDate(value: LocalDate?): EventFormState {
        val safe = value?.takeIf { it > date }
        // 回到单日时结束时刻一并清掉，避免留下没有结束日的「孤儿时间」
        return copy(endDate = safe, endTime = if (safe == null) null else endTime)
    }

    /** 持续事件结束日的具体时刻；null 表示沿用开始时刻。 */
    fun withEndTime(value: LocalTime?): EventFormState = copy(endTime = value)

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

    /** 加一个标签；已存在（忽略大小写）或已满时原样返回，由调用方决定要不要提示。 */
    fun addTag(raw: String): EventFormState {
        val cleaned = com.morrowviento.morrow.domain.model.EventTags.clean(raw)
        if (cleaned.isEmpty()) return this
        if (tags.any { it.equals(cleaned, ignoreCase = true) }) return this
        if (tags.size >= com.morrowviento.morrow.domain.model.EventTags.MAX_COUNT) return this
        return copy(tags = tags + cleaned)
    }

    fun removeTag(tag: String): EventFormState = copy(tags = tags.filterNot { it == tag })

    fun toggleTag(tag: String): EventFormState =
        if (tags.any { it == tag }) removeTag(tag) else addTag(tag)

    /** 加一条子任务；空标题、重复标题、超过上限时原样返回（由调用方决定是否提示）。 */
    fun addChecklistItem(raw: String): EventFormState {
        val title = com.morrowviento.morrow.domain.model.EventChecklist.clean(raw)
        if (title.isEmpty()) return this
        if (checklist.size >= com.morrowviento.morrow.domain.model.EventChecklist.MAX_COUNT) return this
        if (checklist.any { it.title.equals(title, ignoreCase = true) }) return this
        val item = com.morrowviento.morrow.domain.model.ChecklistItem(
            id = UUID.randomUUID().toString(),
            title = title,
        )
        return copy(checklist = checklist + item)
    }

    fun removeChecklistItem(id: String): EventFormState =
        copy(checklist = checklist.filterNot { it.id == id })

    /**
     * 把自然语言解析结果合进表单。
     *
     * 合并原则：**只有解析到的字段才覆盖**，没解析到的一律保留用户手填的值——
     * 「明天下午三点 开会」不该顺手把用户已经选好的分类、提醒、子任务清掉。
     *
     * 三个细节：
     * - 标题只在还空着时填：用户先写了标题再补一句时间，不能被解析出的残词顶掉；
     * - 全天判定放这里而不是解析器里：解析器不知道表单原本是不是全天，
     *   「没说时刻」只在**解析到了日期**时才该翻成全天；
     * - 改了重复档位要把不相关的隐藏字段清掉（周几 / 农历），否则会带着
     *   上次选的星期落库，变成一条「每年重复但带着周几」的怪事件。
     */
    fun applyParsed(parsed: com.morrowviento.morrow.domain.time.ParsedEventInput): EventFormState {
        if (!parsed.matched) return this
        val nextRepeat = parsed.repeatType.takeIf { it != RepeatType.NONE } ?: repeatType
        val nextWeekdays = when {
            nextRepeat != RepeatType.WEEKLY -> emptySet()
            parsed.repeatWeekdays.isNotEmpty() -> parsed.repeatWeekdays
            else -> repeatWeekdays
        }
        val merged = copy(
            title = if (title.isBlank()) parsed.title else title,
            time = parsed.time ?: time,
            isAllDay = when {
                parsed.time != null -> false
                parsed.date != null -> parsed.isAllDay
                else -> isAllDay
            },
            repeatType = nextRepeat,
            repeatWeekdays = nextWeekdays,
            repeatOnLunar = if (nextRepeat == RepeatType.YEARLY) repeatOnLunar else false,
            repeatUntil = if (nextRepeat == RepeatType.NONE) null else repeatUntil,
            durationMinutes = if (parsed.durationMinutes > 0) {
                parsed.durationMinutes.coerceIn(0, CountdownEvent.MAX_DURATION_MINUTES)
            } else {
                durationMinutes
            },
        )
        // 走 withDate 而不是 copy：日历夹日（1 月 31 日 → 2 月 28 日）与
        // 「开始日越过结束日就作废」这两条归一化都在它里面
        return parsed.date?.let { merged.withDate(it) } ?: merged
    }

    /** 勾选/取消勾选某一项。只动 done，不重排——清单顺序由用户决定。 */
    fun toggleChecklistItem(id: String): EventFormState =
        copy(checklist = checklist.map { if (it.id == id) it.copy(done = !it.done) else it })

    /** 时长选择：点同一个值取消（回到「不指定」）。 */
    fun withDuration(minutes: Int): EventFormState = copy(
        durationMinutes = if (durationMinutes == minutes) 0 else minutes.coerceIn(0, CountdownEvent.MAX_DURATION_MINUTES),
    )

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
            repeatType = event.repeatType,
            repeatInterval = event.repeatInterval,
            repeatWeekdays = event.repeatWeekdays,
            repeatOnLunar = event.repeatOnLunar,
            repeatUntil = event.repeatUntil,
            endDate = event.endDate,
            endTime = event.endTime,
            kind = event.kind,
            createdAt = event.createdAt,
            tags = event.tags,
            durationMinutes = event.durationMinutes,
            checklist = event.checklist,
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
