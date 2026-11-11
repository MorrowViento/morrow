package com.jiuxina.ying.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.domain.time.LunarCalendar
import com.jiuxina.ying.ui.glass.GlassCirclePanel
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历页（M2d）：月视图 / 年视图 / 按月分组列表。
 *
 * 性能取舍：整月格子只套**一个** GlassPanel，格子内部用纯文字与浅色块区分状态。
 * 42 个独立玻璃面在低端机上会明显掉帧，而玻璃材质的表现力在「一块大面板」上并不损失。
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    actions: CalendarActions,
    onOpenEvent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CalendarHeader(state, actions, onDismiss)
        when {
            state.error != null -> CenterHint("读取事件失败", state.error)
            state.isLoading -> CenterHint("载入中…", null)
            state.view == CalendarView.MONTH -> MonthView(state, actions, onOpenEvent)
            state.view == CalendarView.YEAR -> YearView(state, actions)
            else -> MonthListView(state, onOpenEvent)
        }
    }
}

// ---------------------------------------------------------------- 头部

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    actions: CalendarActions,
    onDismiss: () -> Unit,
) {
    val isMonthView = state.view == CalendarView.MONTH
    val today = state.today
    val onTodayPeriod = if (isMonthView) {
        state.month == YearMonth.from(today)
    } else {
        state.month.year == today.year
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavButton("‹") {
            if (isMonthView) actions.shiftMonth(-1) else actions.shiftYear(-1)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isMonthView) {
                    "${state.month.year}年${state.month.monthValue}月"
                } else {
                    "${state.month.year}年"
                },
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
            )
            Text(
                text = if (isMonthView) {
                    "本月 ${state.monthEventCount} 个事件"
                } else {
                    "全年 ${state.yearEventCount} 个事件"
                },
                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
            )
        }
        NavButton("›") {
            if (isMonthView) actions.shiftMonth(1) else actions.shiftYear(1)
        }
        Spacer(Modifier.width(8.dp))
        NavButton("✕", onClick = onDismiss)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewSegmented(state.view, actions.setView)
        Spacer(Modifier.weight(1f))
        if (!onTodayPeriod) {
            Text(
                text = "回到今天",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = actions.goToToday)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(fontSize = 12.sp, color = glassTextPrimary()),
            )
        }
    }
}

@Composable
private fun NavButton(glyph: String, onClick: () -> Unit) {
    GlassCirclePanel(modifier = Modifier.size(30.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
            )
        }
    }
}

@Composable
private fun ViewSegmented(current: CalendarView, onSelect: (CalendarView) -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    GlassPanel {
        Row(Modifier.padding(2.dp)) {
            CalendarView.entries.forEach { view ->
                val active = view == current
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) primary.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelect(view) }
                        .padding(horizontal = 13.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = view.label,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) primary else secondary,
                        ),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 月视图

@Composable
private fun MonthView(
    state: CalendarUiState,
    actions: CalendarActions,
    onOpenEvent: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            CalendarPresentation.WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                )
            }
        }
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                for (row in 0 until 6) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val cell = state.cells.getOrNull(row * 7 + col) ?: continue
                            DayCell(
                                cell = cell,
                                modifier = Modifier.weight(1f),
                                onClick = { actions.selectDay(cell.date) },
                            )
                        }
                    }
                }
            }
        }
        SelectedDaySection(
            state = state,
            onOpenEvent = onOpenEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DayCell(cell: CalendarCell, modifier: Modifier, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val dimmed = !cell.inMonth
    val textAlpha = when {
        dimmed -> 0.3f
        cell.isToday || cell.isSelected -> 1f
        else -> 0.88f
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (cell.isSelected) primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (cell.isToday) {
            Canvas(Modifier.matchParentSize().padding(6.dp)) {
                drawCircle(color = primary.copy(alpha = 0.4f), style = Stroke(width = 1.2.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (cell.isToday || cell.isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = primary.copy(alpha = textAlpha),
                ),
            )
            Text(
                text = cell.lunarLabel,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontSize = 8.5.sp,
                    fontWeight = if (cell.isFestival) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (cell.isFestival) {
                        primary.copy(alpha = if (dimmed) 0.35f else 0.85f)
                    } else {
                        secondary.copy(alpha = if (dimmed) 0.25f else 0.7f)
                    },
                ),
            )
            Box(Modifier.height(4.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(cell.eventCount.coerceAtMost(3)) {
                        Box(
                            Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = if (dimmed) 0.25f else 0.6f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDaySection(
    state: CalendarUiState,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.let { "${it.monthValue}月${it.dayOfMonth}日 ${weekdayName(it)}" } ?: "选择一个日期",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = selected?.let { lunarLine(it) }.orEmpty(),
                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
            )
        }
        if (state.selectedEntries.isEmpty()) {
            Text(
                text = if (selected == null) "点上方日期查看当天事件" else "这一天没有事件",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.selectedEntries, key = { it.id }) { entry ->
                    CalendarEntryRow(entry) { onOpenEvent(entry.id) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 年视图

@Composable
private fun YearView(state: CalendarUiState, actions: CalendarActions) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        if (state.yearEventCount == 0) {
            Text(
                text = "这一年还没有事件",
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
            )
        }
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (col in 0 until 3) {
                    val summary = state.monthSummaries.getOrNull(row * 3 + col) ?: continue
                    MonthCard(
                        summary = summary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 5.dp),
                        onClick = { actions.openMonth(summary.month) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCard(summary: MonthSummary, modifier: Modifier, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    GlassPanel(
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${summary.month.monthValue}月",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (summary.eventCount == 0) "—" else "${summary.eventCount}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (summary.eventCount == 0) secondary.copy(alpha = 0.5f) else primary,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            summary.sampleTitles.take(2).forEach { title ->
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 9.5.sp, color = secondary),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 列表视图

@Composable
private fun MonthListView(state: CalendarUiState, onOpenEvent: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.monthGroups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "这一年还没有事件",
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                    style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                )
            }
        }
        state.monthGroups.forEach { group ->
            item(key = "m-${group.month}") {
                MonthGroupHeader(group)
            }
            items(group.entries, key = { "e-${group.month}-${it.id}" }) { entry ->
                CalendarEntryRow(entry) { onOpenEvent(entry.id) }
            }
        }
    }
}

@Composable
private fun MonthGroupHeader(group: MonthGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${group.month.year}年${group.month.monthValue}月",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${group.entries.size} 个事件",
            style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
        )
    }
}

/** 日历条目行：左侧日期与农历，右侧标题与分类。 */
@Composable
private fun CalendarEntryRow(entry: CalendarEntry, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = entry.date.dayOfMonth.toString(),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
                Text(
                    text = weekdayName(entry.date),
                    style = TextStyle(fontSize = 9.sp, color = secondary),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (entry.isCompleted) primary.copy(alpha = 0.55f) else primary,
                    ),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entryMeta(entry),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, color = secondary),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = deltaLabel(entry),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary.copy(alpha = if (entry.dayDelta == 0) 1f else 0.7f),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- 文案

private fun entryMeta(entry: CalendarEntry): String {
    val lunar = LunarCalendar.cellLabel(entry.date)
    val parts = mutableListOf(entry.category)
    lunar?.let { parts += "农历$it" }
    if (entry.isAllDay) parts += "全天"
    if (entry.repeatsYearly) parts += "每年"
    return parts.joinToString(" · ")
}

private fun deltaLabel(entry: CalendarEntry): String = when {
    entry.dayDelta == 0 -> "今天"
    entry.dayDelta > 0 -> "${entry.dayDelta} 天后"
    else -> "已过 ${-entry.dayDelta} 天"
}

private fun lunarLine(date: LocalDate): String {
    val lunar = LunarCalendar.fromSolar(date) ?: return ""
    val festival = LunarCalendar.festival(lunar)
    return if (festival != null) "农历${lunar.monthLabel}${lunar.dayLabel} · $festival"
    else "农历${lunar.monthLabel}${lunar.dayLabel}"
}

fun weekdayName(date: LocalDate): String =
    when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

@Composable
private fun CenterHint(title: String, detail: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = TextStyle(fontSize = 14.sp, color = glassTextPrimary()))
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()))
        }
    }
}
