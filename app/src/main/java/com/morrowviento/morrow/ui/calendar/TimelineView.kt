package com.morrowviento.morrow.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.LocalAccentPalette
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 时间块视图：一天 24 小时的纵向时间轴。
 *
 * 一整块 GlassPanel 包住轴（而不是每个块一个玻璃面）：24 个块各开一片模糊，
 * 低端机会直接掉帧，而玻璃的表现力在一块大面板上并不损失——月视图也是同一取舍。
 *
 * 块之间用「列」并排表示重叠（见 [TimelineLayout.assignLanes]），
 * 全天与跨天的事挂在轴上方，不占轴的宽度。
 */
@Composable
internal fun TimelineView(
    state: CalendarUiState,
    onOpenEvent: (String) -> Unit,
) {
    val day = state.timeline ?: return
    val hourHeight = 56.dp
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    // 换到另一天时滚到第一个事件附近：不然每次都从 00:00 开始，
    // 用户要手动往下拖过一整夜才看到上午的安排
    LaunchedEffect(day.date) {
        val pixels = with(density) { (hourHeight * (day.scrollToMinute / 60f)).roundToPx() }
        scroll.scrollTo(pixels)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 96.dp),
    ) {
        GlassPanel(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(top = 10.dp, bottom = 14.dp),
            ) {
                if (day.allDay.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        day.allDay.forEach { item ->
                            AllDayRow(item, onClick = { onOpenEvent(item.id) })
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (day.isEmpty) {
                    Text(
                        text = "这一天没有安排",
                        style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                        modifier = Modifier.padding(start = 58.dp, top = 8.dp, bottom = 12.dp),
                    )
                }

                Row(Modifier.fillMaxWidth()) {
                    HourGutter(hourHeight)
                    TimelineTrack(day, state.today, hourHeight, onOpenEvent)
                }
            }
        }
    }
}

/** 左侧整点刻度列。 */
@Composable
private fun HourGutter(hourHeight: Dp) {
    val secondary = glassTextSecondary()
    Column(Modifier.width(46.dp)) {
        for (hour in 0 until 24) {
            Box(
                modifier = Modifier
                    .height(hourHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = if (hour < 10) "0$hour" else "$hour",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontFeatureSettings = "tnum",
                        color = secondary,
                    ),
                    modifier = Modifier.padding(end = 8.dp, top = 0.dp).offset(y = (-6).dp),
                )
            }
        }
    }
}

/**
 * 时间轴本体：整点横线 + 事件块 + 当前时刻线。
 *
 * 用 [BoxWithConstraints] 拿到可用宽度，块按 `lane / laneCount` 手动定位——
 * 用 `Row + weight` 排的话，不同重叠簇的列会让块宽度在簇之间跳变。
 */
@Composable
private fun TimelineTrack(
    day: TimelineDay,
    today: java.time.LocalDate,
    hourHeight: Dp,
    onOpenEvent: (String) -> Unit,
) {
    val line = glassTextPrimary().copy(alpha = 0.08f)
    val accent = Color(LocalAccentPalette.current.argb(LocalGlassDark.current))
    val isToday = day.date == today

    BoxWithConstraints(Modifier.fillMaxWidth().height(hourHeight * 24)) {
        // 整点横线：画在轴上而不是用 Divider 组件，24 条 Divider 会有自己的测量开销
        Canvas(Modifier.fillMaxSize()) {
            val step = size.height / 24f
            for (hour in 0..24) {
                val y = step * hour
                drawLine(
                    color = line,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }

        val laneGap = 5.dp
        day.blocks.forEach { block ->
            val columnWidth = maxWidth / block.laneCount
            val top = hourHeight * (block.startMinute / 60f)
            val height = (hourHeight * ((block.endMinute - block.startMinute) / 60f))
                .coerceAtLeast(20.dp)
            TimelineBlockView(
                block = block,
                accent = accent,
                modifier = Modifier
                    .offset(x = columnWidth * block.lane, y = top)
                    .width((columnWidth - laneGap).coerceAtLeast(24.dp))
                    .height(height)
                    .padding(vertical = 1.dp),
                onClick = { onOpenEvent(block.id) },
            )
        }

        if (isToday) {
            NowLine(hourHeight)
        }
    }
}

@Composable
private fun NowLine(hourHeight: Dp) {
    // 每 30 秒推进一次：这条线只用来「看一眼现在走到哪」，
    // 每秒重组整条轴不值得（与首页每秒刷新是两回事，那边有秒级倒计时要显示）
    var minute by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            minute = LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
    val danger = Color(0xFFE65B64)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(0, (hourHeight.toPx() * (minute / 60f)).roundToInt())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(46.dp)
                .height(1.6.dp)
                .background(danger),
        )
        Box(
            Modifier
                .width(6.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(danger),
        )
    }
}

@Composable
private fun TimelineBlockView(
    block: TimelineBlock,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val dark = LocalGlassDark.current
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val tint = if (block.isCompleted) {
        glassColors().elevatedBackground.copy(alpha = 0.5f)
    } else {
        accent.copy(alpha = if (dark) 0.34f else 0.20f)
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .clip(shape)
            .clickableBlock(onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column {
            Text(
                text = block.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (block.isCompleted) secondary else primary,
                ),
            )
            Text(
                text = clockRange(block.startMinute, block.endMinute),
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum", color = secondary),
            )
        }
    }
}

@Composable
private fun AllDayRow(item: TimelineAllDay, onClick: () -> Unit) {
    val dark = LocalGlassDark.current
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(glassColors().elevatedBackground.copy(alpha = if (dark) 0.5f else 0.6f))
            .clickableBlock(onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "全天",
            style = TextStyle(fontSize = 10.sp, color = secondary),
            modifier = Modifier.width(30.dp),
        )
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (item.isCompleted) secondary else primary,
            ),
            modifier = Modifier.weight(1f),
        )
        item.dayIndexLabel?.let {
            Text(text = it, style = TextStyle(fontSize = 10.sp, color = secondary))
        }
    }
}

/** 块与全天条的点击；零涟漪，块本身就是有色底的，再叠一层水波只会糊。 */
private fun Modifier.clickableBlock(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

private fun clockRange(startMinute: Int, endMinute: Int): String {
    fun clock(minute: Int): String {
        val safe = minute.coerceIn(0, 24 * 60)
        val hour = (safe / 60).coerceAtMost(24)
        val rest = safe % 60
        return if (hour == 24) "24:00" else (if (hour < 10) "0$hour" else "$hour") + ":" +
            (if (rest < 10) "0$rest" else "$rest")
    }
    return clock(startMinute) + " – " + clock(endMinute)
}
