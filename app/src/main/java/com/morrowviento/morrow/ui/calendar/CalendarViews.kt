package com.morrowviento.morrow.ui.calendar

/**
 * 日历的两种视图与行组件：月格、当日事件区、年视图卡片与月列表。
 *
 * 几何与动画留在 CalendarScreen；这里只按 state 画内容，便于单独调整排版而不碰动画。
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.morrowviento.morrow.ui.component.PageChrome
import com.morrowviento.morrow.domain.model.RepeatType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.domain.time.LunarCalendar
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassTile
import com.morrowviento.morrow.ui.glass.glassAccent
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import java.time.LocalDate

// ---------------------------------------------------------------- 月视图

@Composable
internal fun MonthView(
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
internal fun DayCell(cell: CalendarCell, modifier: Modifier, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val accent = glassAccent()
    val dimmed = !cell.inMonth
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val textAlpha = when {
        dimmed -> 0.3f
        cell.isToday || cell.isSelected -> 1f
        else -> 0.88f
    }

    // 42 个格子不放 GlassPress（每个都带 shader 光斑会明显掉帧），但状态指示仍自己画：
    // 用 `clickable(indication = null)` 关掉 Material ripple，按下只把格子底色加深一档。
    // ripple 在方格里也是矩形，和整页的圆角语言冲突。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 选中态不再用半透明底色，而是底下垫一整块玻璃（见下面的 GlassTile）；
    // 只有「按下但未选中」这一种情况还需要自己画底。
    val cellAlpha = if (!cell.isSelected && pressed) 0.10f else 0f

    Box(
        modifier = modifier
            .heightIn(min = 30.dp * fontScale + 8.dp)
            .aspectRatio(1f)
            // 手势范围要盖满整格：`padding` 若排在 `clickable` 之前，格与格之间那 1dp 缝
            // 就不属于任何格子，点上去等于点在空气上（旧版会穿透到首页卡片）。
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(1.dp)
            .background(if (cellAlpha > 0f) primary.copy(alpha = cellAlpha) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        // 选中的那天是一块**真的玻璃**：一次材质采样（整月只会同时存在一块），
        // 于是数字浮在折射过的背景上，和整页的材质语言一致。
        // 注意它必须垫在文字之前：玻璃的采样层是整页背景，压在文字上会把数字糊掉。
        if (cell.isSelected) {
            GlassTile(
                modifier = Modifier.matchParentSize().padding(2.dp),
                cornerRadius = 11.dp,
                tint = glassAccent().copy(alpha = 0.20f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 今天不再用「整格描边圆」：那个圆的直径盖住了农历行和底部的事件点，
            // 圈底必然压到点。改成只包住日期数字的淡色圆角底 —— 一眼能看出今天，
            // 又不会碰到下面的点。
            val todayChip = if (cell.isToday) {
                Modifier
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            } else {
                Modifier
            }
            Text(
                text = cell.date.dayOfMonth.toString(),
                modifier = todayChip,
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (cell.isToday) FontWeight.SemiBold else FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    platformStyle = CalendarFontMetrics,
                    lineHeightStyle = CalendarLineHeight,
                    color = if (cell.isToday) accent else primary.copy(alpha = textAlpha),
                ),
            )
            Text(
                text = cell.lunarLabel,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp,
                    platformStyle = CalendarFontMetrics,
                    lineHeightStyle = CalendarLineHeight,
                    fontWeight = if (cell.isFestival) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (cell.isFestival) {
                        primary.copy(alpha = if (dimmed) 0.35f else 0.85f)
                    } else {
                        secondary.copy(alpha = if (dimmed) 0.25f else 0.7f)
                    },
                ),
            )
            // 事件点：固定占底部一条，最多 4 点。用强调色而不是正文色，和今天的圆角底同源。
            Box(Modifier.height(6.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                    repeat(cell.eventCount.coerceAtMost(4)) {
                        Box(
                            Modifier
                                .size(2.5.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = if (dimmed) 0.22f else 0.75f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SelectedDaySection(
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
                contentPadding = PaddingValues(start = PageChrome.HeaderHorizontal, end = PageChrome.HeaderHorizontal, top = 2.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 同样带上日期：这一天若同时被「重复 + 持续」的事件命中，
                // 光用 id 也有撞 key 的风险，撞了就是整个进程被杀。
                items(state.selectedEntries, key = { "${it.date}-${it.id}" }) { entry ->
                    CalendarEntryRow(entry) { onOpenEvent(entry.id) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 年视图

@Composable
internal fun YearView(state: CalendarUiState, actions: CalendarActions) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PageChrome.HeaderHorizontal, end = PageChrome.HeaderHorizontal, top = 6.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.monthSummaries, key = { it.month.toString() }) { summary ->
            MonthCard(summary, Modifier.fillMaxWidth()) { actions.openMonth(summary.month) }
        }
    }
}

@Composable
internal fun MonthCard(summary: MonthSummary, modifier: Modifier, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val accent = glassAccent()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    GlassPanel(modifier = modifier, onClick = onClick) {
        // 年视图是「预览」不是「完整日历」：每行两个月，板块要紧凑、只给一瞥。
        // 格子高度、内边距、字号都往下压，让一屏能多露几行摘要。
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${summary.month.monthValue}月", style = TextStyle(fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = primary))
                Spacer(Modifier.weight(1f))
                Text("${summary.eventCount} 次", style = TextStyle(fontSize = 9.5.sp, color = secondary))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                CalendarPresentation.WEEKDAY_LABELS.forEach { label ->
                    Text(label, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 7.5.sp, color = secondary))
                }
            }
            Spacer(Modifier.height(2.dp))
            for (row in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cell = summary.cells[row * 7 + col]
                        Box(Modifier.weight(1f).height(16.dp * fontScale), contentAlignment = Alignment.Center) {
                            if (cell.inMonth) {
                                // 年视图格子只有 16dp：今天的圆半径 7dp，底边正好压住 3dp 的事件点。
                                // 这里不再画圆，改用强调色 + 加重字重标记今天 —— 小尺寸下反而更清楚。
                                Text(cell.date.dayOfMonth.toString(), style = TextStyle(
                                    fontSize = 9.sp, lineHeight = 11.sp,
                                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
                                    fontFeatureSettings = "tnum", platformStyle = CalendarFontMetrics,
                                    lineHeightStyle = CalendarLineHeight,
                                    color = if (cell.isToday) accent else primary,
                                ))
                                if (cell.eventCount > 0) Canvas(Modifier.align(Alignment.BottomCenter).size(2.5.dp)) {
                                    drawCircle(accent)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (summary.sampleEntries.isEmpty()) {
                Text("本月暂无事件", style = TextStyle(fontSize = 9.sp, color = secondary))
            } else summary.sampleEntries.forEach { entry ->
                Text("${entry.date.dayOfMonth}日 · ${entry.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 9.sp, color = secondary))
            }
            if (summary.eventCount > summary.sampleEntries.size) {
                Text("另 ${summary.eventCount - summary.sampleEntries.size} 次事件", style = TextStyle(fontSize = 9.sp, color = secondary))
            }
        }
    }
}

// ---------------------------------------------------------------- 列表视图

@Composable
internal fun MonthListView(state: CalendarUiState, onOpenEvent: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PageChrome.HeaderHorizontal, end = PageChrome.HeaderHorizontal, top = 6.dp, bottom = 120.dp),
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
            // key 必须带上日期：每天 / 每周的事件（以及跨多天的持续事件）在同一个月里
            // 会产生多条条目，只用事件 id 会撞 key——Compose 直接抛 IllegalArgumentException，
            // 把进程杀掉。真机上的「进列表就闪退」就是这个。
            items(group.entries, key = { "e-${group.month}-${it.date}-${it.id}" }) { entry ->
                CalendarEntryRow(entry) { onOpenEvent(entry.id) }
            }
        }
    }
}

@Composable
internal fun MonthGroupHeader(group: MonthGroup) {
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
            text = "${group.entries.size} 次事件",
            style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
        )
    }
}

/** 日历条目行：左侧日期与农历，右侧标题与分类。 */
@Composable
internal fun CalendarEntryRow(entry: CalendarEntry, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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
    // repeatLabel 已经是「每周」「每年·农历」这种完整短语，不再拼 RepeatType.label
    if (entry.repeatType != RepeatType.NONE && entry.repeatLabel.isNotBlank()) parts += entry.repeatLabel
    entry.spanLabel?.let { parts += it }
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
internal fun CenterHint(title: String, detail: String?) {
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
