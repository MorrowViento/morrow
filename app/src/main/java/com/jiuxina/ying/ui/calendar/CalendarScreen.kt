package com.jiuxina.ying.ui.calendar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.domain.time.LunarCalendar
import com.jiuxina.ying.ui.component.CreateFab
import com.jiuxina.ying.ui.glass.GlassBackdrop
import com.jiuxina.ying.ui.glass.GlassCirclePanel
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 日历页（M2d）：月视图 / 年视图 / 按月分组列表。
 *
 * 性能取舍：整月格子只套**一个** GlassPanel，格子内部用纯文字与浅色块区分状态。
 * 42 个独立玻璃面在低端机上会明显掉帧，而玻璃材质的表现力在「一块大面板」上并不损失。
 *
 * 这是**整页覆盖**而非浮层：必须自己铺一层不透明底色（[GlassBackdrop]），
 * 否则下层首页的标题与卡片会从缝隙里透出来形成"两页重叠"。
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    actions: CalendarActions,
    /** 带日期新建：用当前选中日，没选中就用今天。 */
    onCreate: (LocalDate) -> Unit,
    onOpenEvent: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 左侧相邻页；翻页动画期间一起渲染，已到边界为 null。 */
    prevPage: CalendarUiState? = null,
    /** 右侧相邻页；同上。 */
    nextPage: CalendarUiState? = null,
) {
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop()

        // 拦住触摸穿透。日历是整页覆盖，但格子之间留了 1dp 缝、面板四周也有留白，
        // 这些位置没有任何可点击节点，手指落下去会穿到下层首页的卡片上
        // （真机表现：点日期数字之间的空白，结果打开了某个首页事件）。这层什么都不做，只吃触摸。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { } },
        )

        Column(Modifier.fillMaxSize()) {
            CalendarHeader(state, actions, onDismiss)
            when {
                state.error != null -> CenterHint("读取事件失败", state.error)
                state.isLoading -> CenterHint("载入中…", null)
                else -> SwipePager(
                    modifier = Modifier.weight(1f),
                    canPrev = prevPage != null,
                    canNext = nextPage != null,
                    pageKey = state.view to state.month,
                    onPrev = {
                        if (state.view == CalendarView.MONTH) actions.shiftMonth(-1) else actions.shiftYear(-1)
                    },
                    onNext = {
                        if (state.view == CalendarView.MONTH) actions.shiftMonth(1) else actions.shiftYear(1)
                    },
                    prevPage = { prevPage?.let { CalendarPage(it, actions, onOpenEvent) } },
                    nextPage = { nextPage?.let { CalendarPage(it, actions, onOpenEvent) } },
                ) {
                    CalendarPage(state, actions, onOpenEvent)
                }
            }
        }
        CreateFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 26.dp),
            onClick = { onCreate(state.selected ?: state.today) },
        )
    }
}

// ---------------------------------------------------------------- 翻页手势

/**
 * 滑动翻页判定：+1 下一页、-1 上一页、0 回弹。
 *
 * 甩动的优先级高于位移——短促快滑通常位移不大，但意图明确；
 * 反过来慢慢拖过一大段再松手，速度早已衰减到 0，就该按位移判定。
 */
internal fun swipeDirection(
    dragged: Float,
    velocity: Float,
    width: Float,
    flingVelocity: Float,
): Int = when {
    abs(velocity) >= flingVelocity -> if (velocity < 0) 1 else -1
    width > 0f && abs(dragged) >= width * SWIPE_DISTANCE_RATIO -> if (dragged < 0) 1 else -1
    else -> 0
}

/** 位移判定阈值：屏宽的 18%，约等于「明显划了一下」而不是误触。 */
private const val SWIPE_DISTANCE_RATIO = 0.18f

/**
 * 左右滑动翻页：跟手位移，相邻页**同时**渲染在屏上。
 *
 * 前一版是「当前页滑出去 → 换数据 → 新页从另一侧滑进来」，中间有整整一屏空白
 * （真机反馈「两个界面之间有一段空白动画间隔」）。现在滑到哪、哪一页就已经在那里了。
 *
 * 换页不换位置：滑到位时当前页在 offset = ±width，新页的 translationX 恰好是 0，
 * 这时才切 nav；等新数据到位、正中那一页本身就是新页了，再把 offset 悄悄归零。
 * 归零前后画面完全静止，所以既没有空白帧，也不会看到两页错位。
 *
 * 只有当前页可点：翻页动画那两百毫秒里，正中那页是「相邻页」的副本，
 * 它的 nav 还没生效，点下去只会得到一个即将被丢弃的选中态。
 *
 * 回调必须经 [rememberUpdatedState] 取：手势协程只启动一次且一直存活，
 * 直接捕获 `onPrev` / `onNext` 会永远用第一帧那份闭包（滚轮就是这么错的）。
 */
@Composable
private fun SwipePager(
    canPrev: Boolean,
    canNext: Boolean,
    /** 换页后变化的值（视图 + 月份）；等它变了才把位移归零。 */
    pageKey: Any,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    prevPage: @Composable () -> Unit,
    nextPage: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestOnPrev by rememberUpdatedState(onPrev)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestCanPrev by rememberUpdatedState(canPrev)
    val latestCanNext by rememberUpdatedState(canNext)
    val latestKey by rememberUpdatedState(pageKey)
    var width by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    // 屏上要额外画哪一侧：0 不画，1 下一页，-1 上一页。
    // 把「每帧位移」收敛成「只在跨过中线时变化」，拖动过程中才不会重组整页内容。
    val side by remember {
        derivedStateOf {
            when {
                offset < -1f -> 1
                offset > 1f -> -1
                else -> 0
            }
        }
    }
    val flingVelocity = with(density) { 600.dp.toPx() }

    /** 把位移滑到 [target]：时长随剩余距离走，抖一下就松手时不会慢吞吞挪一整屏。 */
    suspend fun glide(target: Float) {
        val remaining = abs(target - offset)
        val ratio = remaining / width.coerceAtLeast(1f)
        val duration = (60f + 180f * ratio).toInt().coerceIn(60, 240)
        animate(
            initialValue = offset,
            targetValue = target,
            animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        ) { value, _ -> offset = value }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dragged = 0f
                    },
                    onDragCancel = { scope.launch { glide(0f) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (!busy) {
                            val raw = offset + dragAmount
                            val min = if (latestCanNext) -width else 0f
                            val max = if (latestCanPrev) width else 0f
                            // 到边界还继续拖时只给 1/4 位移：手感上「拽到头了」比硬停更好懂
                            offset = when {
                                raw < min -> min + (raw - min) * EDGE_RESISTANCE
                                raw > max -> max + (raw - max) * EDGE_RESISTANCE
                                else -> raw
                            }
                            dragged += dragAmount
                            tracker.addPosition(change.uptimeMillis, change.position)
                        }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        val direction = swipeDirection(dragged, velocity, width, flingVelocity)
                        val allowed = when (direction) {
                            1 -> latestCanNext
                            -1 -> latestCanPrev
                            else -> false
                        }
                        if (!allowed) {
                            scope.launch { glide(0f) }
                        } else {
                            busy = true
                            scope.launch {
                                glide(if (direction > 0) -width else width)
                                val from = latestKey
                                if (direction > 0) latestOnNext() else latestOnPrev()
                                // 等新一页的数据落位再归零：这期间画面静止在新页正中，
                                // 用户看不到任何等待，也不会闪一帧旧内容
                                withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) {
                                    snapshotFlow { latestKey }.first { it != from }
                                }
                                offset = 0f
                                busy = false
                            }
                        }
                    },
                )
            },
    ) {
        // 相邻页先画、当前页后画：万一两页有重叠，当前页在上
        if (side == 1) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = width + offset },
            ) {
                nextPage()
            }
        }
        if (side == -1) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = offset - width },
            ) {
                prevPage()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset },
        ) {
            content()
        }
    }
}

/** 拖到边界后的位移衰减系数。 */
private const val EDGE_RESISTANCE = 0.25f

/**
 * 等新页数据的上限。
 *
 * 正常 1~3 帧就到位；给上限是为了「翻页没改变 nav」这种意外（比如数据源被 clamp 回来）
 * 也能收尾，不会把 busy 永久挂住导致页面拖不动。
 */
private const val SETTLE_TIMEOUT_MILLIS = 300L

/** 三种视图共用的内容分派。 */
@Composable
private fun CalendarPage(
    state: CalendarUiState,
    actions: CalendarActions,
    onOpenEvent: (String) -> Unit,
) {
    when (state.view) {
        CalendarView.MONTH -> MonthView(state, actions, onOpenEvent)
        CalendarView.YEAR -> YearView(state, actions)
        CalendarView.LIST -> MonthListView(state, onOpenEvent)
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
            // 手势范围要盖满整格：`padding` 若排在 `clickable` 之前，格与格之间那 1dp 缝
            // 就不属于任何格子，点上去等于点在空气上（旧版会穿透到首页卡片）。
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(1.dp)
            .background(if (cell.isSelected) primary.copy(alpha = 0.14f) else Color.Transparent),
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
