package com.jiuxina.ying.ui.calendar

import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历页的回调集合。
 *
 * 与 [com.jiuxina.ying.ui.event.EventActions] 同样的理由：整体 remember 一次，
 * 避免每次重组都新建一批 lambda 触发子项重组。
 */
data class CalendarActions(
    val setView: (CalendarView) -> Unit,
    val shiftMonth: (Long) -> Unit,
    val shiftYear: (Long) -> Unit,
    val selectDay: (LocalDate) -> Unit,
    /** 年视图点某个月：切回月视图并定位到该月。 */
    val openMonth: (YearMonth) -> Unit,
    val goToToday: () -> Unit,
)
