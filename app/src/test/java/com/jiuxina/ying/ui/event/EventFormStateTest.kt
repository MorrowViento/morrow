package com.jiuxina.ying.ui.event

import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
        val event = requireNotNull(form().copy(repeatYearly = true).toEvent())
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
            form("旅行").copy(category = "旅行", icon = "plane", repeatYearly = true).addReminder(1440).toEvent(),
        )
        val state = EventFormState.fromEvent(event)
        assertEquals(event.id, state.id)
        assertEquals("旅行", state.title)
        assertEquals("旅行", state.category)
        assertEquals("plane", state.icon)
        assertTrue(state.repeatYearly)
        assertEquals(listOf(1440), state.reminders.map { it.minutesBefore })
        assertEquals(event.targetDate.toLocalDate(), state.date)
    }
}
