package com.morrowviento.morrow.ui.event

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 表单纯逻辑单测：校验、落库字段映射、提醒上限、日期夹取。 */
class EventFormStateTest {

    private fun form(title: String = "生日") = EventFormState(
        title = title,
        date = LocalDate.of(2026, 12, 25),
        time = LocalTime.of(8, 30),
    )

    @Test
    fun blankTitle_isRejected() {
        assertEquals("请输入标题", form("   ").validate().title)
        assertNull(form("生日").validate().title)
        assertTrue(form("生日").validate().isValid)
        assertNull(form("   ").toEvent())
    }

    @Test
    fun validForm_mapsToEventFields() {
        val event = form("  春节回家  ").copy(note = " 记得买票 ").toEvent()
        requireNotNull(event)
        assertEquals("春节回家", event.title)
        assertEquals("记得买票", event.note)
        assertEquals(LocalDate.of(2026, 12, 25), event.targetDate.toLocalDate())
        assertEquals(LocalTime.of(8, 30), event.targetDate.toLocalTime())
        assertEquals(CountdownEvent.DEFAULT_CATEGORY, event.category)
        assertEquals(RepeatType.NONE, event.repeatType)
        assertNull(event.repeatMonth)
    }

    @Test
    fun allDayTarget_isMidnight_andReminderAnchorIsNine() {
        val event = requireNotNull(form().copy(isAllDay = true).toEvent())
        assertEquals(LocalTime.MIDNIGHT, event.targetDate.toLocalTime())
        assertEquals(LocalTime.of(9, 0), event.reminderAnchor.toLocalTime())
    }

    @Test
    fun yearlyRepeat_recordsMonthAndDay() {
        val event = requireNotNull(form().copy(repeatType = RepeatType.YEARLY).toEvent())
        assertEquals(RepeatType.YEARLY, event.repeatType)
        assertEquals(12, event.repeatMonth)
        assertEquals(25, event.repeatDay)
    }

    @Test
    fun reminders_areCappedDedupedAndSorted() {
        var state = form()
        listOf(0, 10, 30, 60, 180, 1440, 2880).forEach { state = state.addReminder(it) }

        assertEquals(EventReminder.MAX_COUNT, state.reminders.size)
        assertEquals(listOf(180, 60, 30, 10, 0), state.reminders.map { it.minutesBefore })

        val duplicated = state.addReminder(180)
        assertEquals(state.reminders, duplicated.reminders)

        assertEquals(4, state.removeReminder(180).reminders.size)
        // 第 6 个（2880）在满额时被丢弃，腾出位置后可以再加入
        assertEquals(5, state.addReminder(2880).reminders.size)
    }

    @Test
    fun toggleReminder_addsAndRemoves() {
        val added = form().toggleReminder(60)
        assertEquals(listOf(60), added.reminders.map { it.minutesBefore })
        assertTrue(added.toggleReminder(60).reminders.isEmpty())
    }

    @Test
    fun safeDate_clampsDayToMonthLength() {
        assertEquals(LocalDate.of(2026, 2, 28), EventFormState.safeDate(2026, 2, 31))
        assertEquals(LocalDate.of(2024, 2, 29), EventFormState.safeDate(2024, 2, 31))
        assertEquals(LocalDate.of(2026, 4, 30), EventFormState.safeDate(2026, 4, 31))
        assertEquals(LocalDate.of(2026, 1, 31), form().copy(date = LocalDate.of(2026, 1, 31)).withDate(LocalDate.of(2026, 1, 31)).date)
    }

    @Test
    fun quickDates_coverTodayTomorrowWeekEndOfMonthAndYear() {
        val today = LocalDate.of(2026, 11, 11)
        val dates = EventFormState.quickDates(today).toMap()
        assertEquals(today, dates["今天"])
        assertEquals(LocalDate.of(2026, 11, 12), dates["明天"])
        assertEquals(LocalDate.of(2026, 11, 18), dates["一周后"])
        assertEquals(LocalDate.of(2026, 11, 30), dates["月底"])
        assertEquals(LocalDate.of(2026, 12, 31), dates["年底"])
    }

    @Test
    fun fromEvent_roundTripsEditableFields() {
        val event = requireNotNull(
            form("旅行").copy(category = "旅行", icon = "plane", repeatType = RepeatType.YEARLY).addReminder(1440).toEvent(),
        )
        val state = EventFormState.fromEvent(event)
        assertEquals(event.id, state.id)
        assertEquals("旅行", state.title)
        assertEquals("旅行", state.category)
        assertEquals("plane", state.icon)
        assertEquals(RepeatType.YEARLY, state.repeatType)
        assertEquals(listOf(1440), state.reminders.map { it.minutesBefore })
        assertEquals(event.targetDate.toLocalDate(), state.date)
    }

    @Test
    fun newEvent_defaultsToTodayAndCurrentMinute() {
        val now = LocalDateTime.of(2026, 11, 12, 7, 48, 33, 500_000_000)
        val state = EventFormState.newEvent(now = now)

        assertEquals(LocalDate.of(2026, 11, 12), state.date)
        // 秒/纳秒截断：滚轮只有时/分，留着秒数会让卡片上的剩余时间对不上表盘
        assertEquals(LocalTime.of(7, 48), state.time)
        assertEquals(LocalDateTime.of(2026, 11, 12, 7, 48), state.target)
    }

    @Test
    fun newEvent_keepsPickedDateButUsesCurrentTime() {
        val state = EventFormState.newEvent(
            date = LocalDate.of(2026, 12, 25),
            now = LocalDateTime.of(2026, 11, 12, 23, 59, 59),
        )
        assertEquals(LocalDate.of(2026, 12, 25), state.date)
        assertEquals(LocalTime.of(23, 59), state.time)
    }

    @Test
    fun edit_mergesLatestCompletionPinAndCycleCursor() {
        val original = requireNotNull(form().copy(repeatType = RepeatType.MONTHLY).toEvent()).copy(
            isCompleted = true, isPinned = true, lastHandledOccurrence = LocalDate.of(2027, 1, 25),
        )
        val edited = requireNotNull(EventFormState.fromEvent(original).copy(title = "改名").toEvent(original))
        assertTrue(edited.isCompleted)
        assertTrue(edited.isPinned)
        assertEquals(original.lastHandledOccurrence, edited.lastHandledOccurrence)
        assertEquals(original.createdAt, edited.createdAt)
        val changedDate = requireNotNull(EventFormState.fromEvent(original).copy(date = original.dateOnly.plusDays(1)).toEvent(original))
        assertNull(changedDate.lastHandledOccurrence)
        val anniversary = requireNotNull(EventFormState.fromEvent(original).copy(
            kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY,
        ).toEvent(original))
        assertEquals(false, anniversary.isCompleted)
    }

    @Test
    fun monthlyAndNewDefault_mapExplicitly() {
        val event = requireNotNull(form().copy(repeatType = RepeatType.MONTHLY).toEvent())
        assertNull(event.repeatMonth)
        assertEquals(25, event.repeatDay)
        assertEquals(com.morrowviento.morrow.domain.model.EventKind.TODO, event.kind)
        assertEquals(RepeatType.MONTHLY, EventFormState.fromEvent(event).repeatType)
        assertEquals(RepeatType.NONE, EventFormState.newEvent().repeatType)
    }

    @Test
    fun withCategoryAlias_followsRenameChain() {
        val base = form().copy(category = "工作")

        assertEquals("生活", base.withCategoryAlias(mapOf("工作" to "生活")).category)
        // A→B→C：链式改名也要解到终点
        assertEquals("杂事", base.withCategoryAlias(mapOf("工作" to "生活", "生活" to "杂事")).category)
        // 与自己无关的改名不动
        assertEquals("工作", base.withCategoryAlias(mapOf("旅行" to "出行")).category)
        assertEquals(base, base.withCategoryAlias(emptyMap()))
    }

    @Test
    fun lunarYearly_savesLunarMonthAndDayNotSolar() {
        // 2026-02-17 是农历正月初一；公历月日是 2/17，若按公历存进去就会「每年 2 月 17 日」
        val event = requireNotNull(
            form().copy(
                date = LocalDate.of(2026, 2, 17),
                repeatType = RepeatType.YEARLY,
                repeatOnLunar = true,
            ).toEvent(),
        )
        assertEquals(true, event.repeatOnLunar)
        assertEquals(1, event.repeatMonth)
        assertEquals(1, event.repeatDay)
        // 表单往返保留农历开关
        assertEquals(true, EventFormState.fromEvent(event).repeatOnLunar)
    }

    @Test
    fun solarYearly_ignoresLunarFlagOfOtherTypes() {
        // 只有年循环能用农历；月/周/日重复带上这个标记会被归一掉
        val monthly = requireNotNull(
            form().copy(repeatType = RepeatType.MONTHLY, repeatOnLunar = true).toEvent(),
        )
        assertEquals(false, monthly.repeatOnLunar)
    }

    @Test
    fun multiDayEndDate_isNormalizedAndFollowsStartDate() {
        val base = form().copy(date = LocalDate.of(2026, 9, 1))
        // 结束日等于开始日 = 单日，归一成 null
        assertNull(requireNotNull(base.copy(endDate = LocalDate.of(2026, 9, 1)).toEvent()).endDate)

        val ranged = requireNotNull(base.copy(endDate = LocalDate.of(2026, 9, 5)).toEvent())
        assertEquals(LocalDate.of(2026, 9, 5), ranged.endDate)
        assertEquals(4, ranged.spanDays)

        // 开始日推到结束日之后：结束日作废，不会出现负跨度
        assertNull(base.copy(endDate = LocalDate.of(2026, 9, 5)).withDate(LocalDate.of(2026, 9, 10)).endDate)
    }

    @Test
    fun weeklyWithoutWeekdays_usesAnchorDayAndKeepsInterval() {
        val event = requireNotNull(
            form().copy(
                date = LocalDate.of(2026, 11, 18), // 周三
                repeatType = RepeatType.WEEKLY,
                repeatInterval = 2,
            ).toEvent(),
        )
        assertEquals(2, event.repeatInterval)
        assertEquals(emptySet<Int>(), event.repeatWeekdays)
        // 不选星期时日历上仍落在周三（由 RepeatRule 用锚点兜底）
        assertEquals(
            java.time.DayOfWeek.WEDNESDAY,
            com.morrowviento.morrow.domain.time.RepeatRule
                .futureOccurrences(event, LocalDate.of(2026, 11, 19))
                .first().dayOfWeek,
        )
    }
}
