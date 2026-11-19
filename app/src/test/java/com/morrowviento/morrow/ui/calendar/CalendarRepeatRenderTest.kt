package com.morrowviento.morrow.ui.calendar

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 日历四个视图对「重复事件」的渲染回归。
 *
 * 曾经的真实闪退：列表视图的 LazyColumn key 是 `事件 id`，而每天 / 每周的事件
 * 在同一个月里会产生多条条目（同 id）→ Compose 抛 `Key ... was already used` 杀进程。
 * 所以这里必须真的把四个视图组合出来，光算 state 是抓不到的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarRepeatRenderTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 19)

    private fun event(
        repeat: RepeatType,
        allDay: Boolean,
        weekdays: Set<Int> = emptySet(),
        duration: Int = 0,
        endDate: LocalDate? = null,
        id: String = "c-${repeat.name}-$allDay-$weekdays-$duration-$endDate",
    ) = CountdownEvent(
        id = id,
        title = "晨跑",
        category = "健康",
        targetDate = LocalDateTime.of(2026, 11, 2, 7, 30),
        createdAt = LocalDateTime.of(2026, 10, 1, 9, 0),
        isAllDay = allDay,
        repeatType = repeat,
        repeatWeekdays = weekdays,
        durationMinutes = duration,
        endDate = endDate,
    )

    private fun state(view: CalendarView, vararg events: CountdownEvent): CalendarUiState {
        val nav = CalendarNav.initial(today).copy(
            view = view,
            month = YearMonth.of(2026, 11),
            selected = today,
        )
        return CalendarPresentation.strip(events.toList(), nav, today).current
    }

    private fun actions() = CalendarActions(
        setView = {},
        shiftMonth = {},
        shiftYear = {},
        selectDay = {},
        openMonth = {},
        goToToday = {},
    )

    private fun render(view: CalendarView, vararg events: CountdownEvent) {
        val ui = state(view, *events)
        compose.setContent {
            GlassTheme(darkTheme = true) {
                GlassBackdropHost(backgroundColor = Color(0xFF141414)) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        when (view) {
                            CalendarView.MONTH -> MonthView(ui, actions()) {}
                            CalendarView.YEAR -> YearView(ui, actions())
                            CalendarView.LIST -> MonthListView(ui) {}
                            CalendarView.TIMELINE -> TimelineView(ui, onOpenEvent = {}, onCreateRange = { _, _, _ -> }, onResizeEvent = { _, _, _ -> })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun dailyTimedEventRendersInCalendarListView() =
        render(CalendarView.LIST, event(RepeatType.DAILY, allDay = false, duration = 30))

    @Test
    fun weeklyTimedEventRendersInCalendarListView() =
        render(
            CalendarView.LIST,
            event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(1, 3, 5)),
        )

    @Test
    fun severalRepeatingEventsRenderInCalendarListView() = render(
        CalendarView.LIST,
        event(RepeatType.DAILY, allDay = false),
        event(RepeatType.DAILY, allDay = true),
        event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(2, 4)),
        event(RepeatType.MONTHLY, allDay = false),
        event(RepeatType.NONE, allDay = false, duration = 60),
    )

    @Test
    fun repeatingEventRendersInMonthView() =
        render(CalendarView.MONTH, event(RepeatType.DAILY, allDay = false))

    @Test
    fun repeatingEventRendersInYearView() =
        render(CalendarView.YEAR, event(RepeatType.DAILY, allDay = false))

    @Test
    fun repeatingTimedEventRendersInTimelineView() = render(
        CalendarView.TIMELINE,
        event(RepeatType.DAILY, allDay = false, duration = 45),
        event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(4), duration = 90),
    )
}
