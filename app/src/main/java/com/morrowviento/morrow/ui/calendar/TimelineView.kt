package com.morrowviento.morrow.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.LocalAccentPalette
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** 一小时的高度。15 分钟那一格就是它的四分之一（14dp）。 */
private val HourHeight = 56.dp

/** 指针落点（像素）→ 分钟。 */
private fun Dp.pixelsToMinutes(pixels: Float, density: Float): Int {
    val heightPx = this.value * density
    if (heightPx <= 0f) return 0
    return ((pixels / heightPx) * 60f).roundToInt()
}

/**
 * 时间块视图：一天 24 小时的纵向时间轴，15 分钟一格。
 *
 * 一整块 GlassPanel 包住轴（而不是每个块一个玻璃面）：24 个块各开一片模糊，
 * 低端机会直接掉帧，而玻璃的表现力在一块大面板上并不损失——月视图也是同一取舍。
 *
 * 块之间用「列」并排表示重叠（见 [TimelineLayout.assignLanes]），
 * 全天与跨天的事挂在轴上方，不占轴的宽度。
 *
 * 两种手势，各自占一块区域、互不抢：
 * - **长按空白后拖**：拖出 15 分钟对齐的区间直接新建；只长按不拖就是一格。
 *   刻意要求「先长按」——普通纵向拖拽必须留给滚动，否则时间轴拖不动。
 * - **拖块的底边**：改时长，15 分钟为步长，松手才落库（拖动过程只做本地预览）。
 */
@Composable
internal fun TimelineView(
    state: CalendarUiState,
    onOpenEvent: (String) -> Unit,
    /** (日期, 开始分钟, 结束分钟)：拖拽/点按空白新建。 */
    onCreateRange: (LocalDate, Int, Int) -> Unit = { _, _, _ -> },
    /** (事件 id, 开始分钟, 结束分钟)：拖底边改时长。 */
    onResizeEvent: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    val day = state.timeline ?: return
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    // 换到另一天时滚到第一个事件附近：不然每次都从 00:00 开始，
    // 用户要手动往下拖过一整夜才看到上午的安排
    LaunchedEffect(day.date) {
        val pixels = with(density) { (HourHeight * (day.scrollToMinute / 60f)).roundToPx() }
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
                    HourGutter()
                    TimelineTrack(
                        day = day,
                        today = state.today,
                        onOpenEvent = onOpenEvent,
                        onCreateRange = onCreateRange,
                        onResizeEvent = onResizeEvent,
                    )
                }
            }
        }
    }
}

/** 左侧整点刻度列。 */
@Composable
private fun HourGutter() {
    val secondary = glassTextSecondary()
    Column(Modifier.width(46.dp)) {
        for (hour in 0 until 24) {
            Box(
                modifier = Modifier
                    .height(HourHeight)
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
 * 时间轴本体：15 分钟网格 + 事件块 + 当前时刻线。
 *
 * 用 [BoxWithConstraints] 拿到可用宽度，块按 `lane / laneCount` 手动定位——
 * 用 `Row + weight` 排的话，不同重叠簇的列会让块宽度在簇之间跳变。
 */
@Composable
private fun TimelineTrack(
    day: TimelineDay,
    today: LocalDate,
    onOpenEvent: (String) -> Unit,
    onCreateRange: (LocalDate, Int, Int) -> Unit,
    onResizeEvent: (String, Int, Int) -> Unit,
) {
    val line = glassTextPrimary().copy(alpha = 0.08f)
    val accent = Color(LocalAccentPalette.current.argb(LocalGlassDark.current))
    val isToday = day.date == today
    val density = LocalDensity.current.density

    /** 长按拖拽中的落点区间（分钟）；null 表示没在拖。 */
    var draft by remember(day.date) { mutableStateOf<IntRange?>(null) }
    /** 正在拖底边的事件 id 与临时结束分钟。 */
    var resizingId by remember(day.date) { mutableStateOf<String?>(null) }
    var resizingEnd by remember(day.date) { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxWidth().height(HourHeight * 24)) {
        // 15 分钟网格：整点稍重、其余很淡。96 条线一次画完，比 96 个 Divider 便宜得多。
        Canvas(Modifier.fillMaxSize()) {
            val step = size.height / (24 * 4).toFloat()
            for (quarter in 0..(24 * 4)) {
                val y = step * quarter
                val isHour = quarter % 4 == 0
                drawLine(
                    color = if (isHour) line else line.copy(alpha = line.alpha * 0.42f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }

        // 空白处的「点 / 长按拖」创建层。放在块**下面**：点块时由块先吃掉事件，
        // 这一层用 requireUnconsumed 的用法保证不会在点块时又新建一条。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(day.date, HourHeight) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val minute = TimelineLayout.snapClamped(
                                HourHeight.pixelsToMinutes(offset.y, density),
                            )
                            draft = minute..minute
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val minute = TimelineLayout.snapClamped(
                                HourHeight.pixelsToMinutes(change.position.y, density),
                            )
                            val current = draft ?: return@detectDragGesturesAfterLongPress
                            draft = minOf(current.first, minute)..maxOf(current.first, minute)
                        },
                        onDragEnd = {
                            val range = draft
                            draft = null
                            if (range != null) {
                                // 只有一个格子的高度时按一格处理，不要造出零长度事件
                                val start = range.first
                                val end = (range.last + TimelineLayout.SNAP_MINUTES)
                                    .coerceAtMost(TimelineLayout.MINUTES_PER_DAY)
                                onCreateRange(day.date, start, end)
                            }
                        },
                        onDragCancel = { draft = null },
                    )
                },
        )

        // 拖拽预览：虚线框太花，改用淡填充 + 描边，和真实块同一套圆角
        draft?.let { range ->
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .offset(y = HourHeight * (range.first / 60f))
                    .fillMaxWidth()
                    .height(
                        (HourHeight * (((range.last - range.first) + TimelineLayout.SNAP_MINUTES) / 60f))
                            .coerceAtLeast(HourHeight / 4),
                    )
                    .clip(shape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.55f), shape),
            )
        }

        val laneGap = 5.dp
        day.blocks.forEach { block ->
            val columnWidth = maxWidth / block.laneCount
            val top = HourHeight * (block.startMinute / 60f)
            val endMinute = if (resizingId == block.id) resizingEnd else block.endMinute
            val height = (HourHeight * ((endMinute - block.startMinute) / 60f))
                .coerceAtLeast(HourHeight / 4)
            TimelineBlockView(
                block = block,
                accent = accent,
                endMinute = endMinute,
                resizing = resizingId == block.id,
                modifier = Modifier
                    .offset(x = columnWidth * block.lane, y = top)
                    .width((columnWidth - laneGap).coerceAtLeast(24.dp))
                    .height(height)
                    .padding(vertical = 1.dp),
                onClick = { onOpenEvent(block.id) },
                onResizeStart = {
                    resizingId = block.id
                    resizingEnd = block.endMinute
                },
                onResize = { deltaMinutes ->
                    val minEnd = block.startMinute + TimelineLayout.SNAP_MINUTES
                    resizingEnd = TimelineLayout.snapClamped(
                        (resizingEnd + deltaMinutes).coerceAtLeast(minEnd),
                    ).coerceAtLeast(minEnd)
                },
                onResizeEnd = {
                    val finalEnd = resizingEnd
                    resizingId = null
                    if (finalEnd != block.endMinute) {
                        onResizeEvent(block.id, block.startMinute, finalEnd)
                    }
                },
                onResizeCancel = { resizingId = null },
            )
        }

        if (isToday) {
            NowLine()
        }
    }
}

/**
 * 当前时刻线：横贯整条轴 + 左侧时刻标签。
 *
 * 旧实现只画 46dp 一小段和一个点，缩略看几乎看不见；这里改成整条实线，
 * 时刻直接用标签写出来——「现在」在时间轴上本来就是个需要被读到的位置。
 */
@Composable
private fun NowLine() {
    // 每 30 秒推进一次：这条线只用来「看一眼现在走到哪」，
    // 每秒重组整条轴不值得（与首页每秒刷新是两回事，那边有秒级倒计时要显示）
    var minute by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            minute = LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
    val accent = Color(LocalAccentPalette.current.argb(LocalGlassDark.current))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(0, (HourHeight.toPx() * (minute / 60f)).roundToInt())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = clockOf(minute),
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = Color.White,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.5.dp)
                .background(accent),
        )
    }
}

@Composable
private fun TimelineBlockView(
    block: TimelineBlock,
    accent: Color,
    endMinute: Int,
    resizing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Int) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
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
    val density = LocalDensity.current.density
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
                text = clockRange(block.startMinute, endMinute),
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum", color = secondary),
            )
        }

        // 底边拖拽把手：只有在这里按下才进「改时长」，块的其余部分仍然是「打开详情」。
        // 视觉上只是一条更亮的横杠，不额外加阴影或图标，免得 30 分钟的块里堆满装饰。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(block.id, block.startMinute, block.endMinute) {
                    detectDragGestures(
                        onDragStart = { onResizeStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            val delta = ((amount.y / density) / HourHeight.value * 60f)
                                .roundToInt()
                            if (delta != 0) onResize(delta)
                        },
                        onDragEnd = { onResizeEnd() },
                        onDragCancel = { onResizeCancel() },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .width(22.dp)
                    .height(if (resizing) 3.dp else 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (resizing) accent.copy(alpha = 0.9f) else primary.copy(alpha = 0.28f),
                    ),
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

private fun clockOf(minute: Int): String {
    val safe = minute.coerceIn(0, 24 * 60)
    val hour = (safe / 60).coerceAtMost(24)
    val rest = safe % 60
    return (if (hour < 10) "0$hour" else "$hour") + ":" + (if (rest < 10) "0$rest" else "$rest")
}

private fun clockRange(startMinute: Int, endMinute: Int): String =
    clockOf(startMinute) + " – " + clockOf(endMinute)
