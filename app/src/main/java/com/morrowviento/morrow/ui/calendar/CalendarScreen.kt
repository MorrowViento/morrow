package com.morrowviento.morrow.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassMotion
import com.morrowviento.morrow.ui.glass.LocalReduceMotion
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassSegmentedControl
import com.morrowviento.morrow.ui.glass.GlassTextButton
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import java.time.YearMonth
import kotlin.math.abs
/**
 * 日历页（M2d）：月视图 / 年视图 / 按月分组列表。
 *
 * 性能取舍：整月格子只套**一个** GlassPanel，格子内部用纯文字与浅色块区分状态。
 * 42 个独立玻璃面在低端机上会明显掉帧，而玻璃材质的表现力在「一块大面板」上并不损失。
 *
 * 与首页在同一个活动内容槽中互斥绘制；背景由全窗口宿主提供，
 * 不在安全区内重绘壁纸，避免渐变原点和图片裁剪比例与系统栏不一致。
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    actions: CalendarActions,
    onOpenEvent: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 还能不能往左 / 往右翻；到农历年表边界时为 false，滑动只被判为无效。 */
    canPrev: Boolean = false,
    canNext: Boolean = false,
) {
    val transition = updateTransition(state, label = "calendarSnapshot")
    Box(Modifier.fillMaxSize()) {
        // 页面留白仍消费触摸；日期格子与实际控件在其上正常处理手势。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { } },
        )

        Column(Modifier.fillMaxSize()) {
            CalendarHeader(state, transition, actions, onDismiss, canPrev, canNext)
            when {
                state.error != null -> CenterHint("读取事件失败", state.error)
                state.isLoading -> CenterHint("载入中…", null)
                else -> SwipePager(
                    modifier = Modifier.weight(1f),
                    canPrev = canPrev,
                    canNext = canNext,
                    onPrev = {
                        if (state.view == CalendarView.MONTH) actions.shiftMonth(-1) else actions.shiftYear(-1)
                    },
                    onNext = {
                        if (state.view == CalendarView.MONTH) actions.shiftMonth(1) else actions.shiftYear(1)
                    },
                ) {
                    CalendarAnimatedContent(transition, Modifier.fillMaxSize().clipToBounds()) { snapshot ->
                        // 退出页只保留旧快照绘制；过期格子/事件点击不能再次导航。
                        val active = calendarPageKey(snapshot) == calendarPageKey(state)
                        val pageActions = actions.copy(
                            selectDay = { if (active) actions.selectDay(it) },
                            openMonth = { if (active) actions.openMonth(it) },
                        )
                        CalendarPage(snapshot, pageActions) { if (active) onOpenEvent(it) }
                    }
                }
            }
        }
    }
}

internal data class CalendarPageKey(val view: CalendarView, val period: Long)

internal fun calendarPageKey(state: CalendarUiState): CalendarPageKey = CalendarPageKey(
    state.view,
    if (state.view == CalendarView.MONTH) state.month.year * 12L + state.month.monthValue - 1 else state.month.year.toLong(),
)

internal fun calendarTransitionDirection(from: CalendarUiState, to: CalendarUiState): Int =
    if (from.view != to.view) 0 else calendarPageKey(to).period.compareTo(calendarPageKey(from).period)

/** 标签和日期页共用完整状态快照及同一 Transition，不维护独立位移/邻页状态。 */
@Composable
internal fun CalendarAnimatedContent(
    transition: Transition<CalendarUiState>,
    modifier: Modifier = Modifier,
    slide: Boolean = true,
    reduceMotion: Boolean = LocalReduceMotion.current,
    content: @Composable (CalendarUiState) -> Unit,
) {
    transition.AnimatedContent(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
        contentKey = ::calendarPageKey,
        transitionSpec = {
            val direction = calendarTransitionDirection(initialState, targetState)
            val transform: ContentTransform = when {
                reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                slide && direction != 0 -> {
                    val towards = if (direction > 0) {
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    (slideIntoContainer(towards, GlassMotion.navigationSlide) + fadeIn(GlassMotion.enterFade)) togetherWith
                        (slideOutOfContainer(towards, GlassMotion.navigationSlide) + fadeOut(GlassMotion.enterFade))
                }
                else -> fadeIn(GlassMotion.enterFade) togetherWith fadeOut(GlassMotion.enterFade)
            }
            // 不插值页面高度；日期格和标题字体不跟着容器尺寸缩放。
            transform.using(null)
        },
    ) { snapshot -> content(snapshot) }
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
 * 左右滑动只判定导航意图；松手与箭头更新同一个 CalendarNav，
 * 页面位移由完整快照的 Transition 接管，不维护另一份拖动位置或同步构建邻页。
 * 判定仍为甩动速度优先、位移其次，动画不锁住下一次手势。
 *
 * 回调必须经 [rememberUpdatedState] 取：手势协程只启动一次且一直存活，
 * 直接捕获 `onPrev` / `onNext` 会永远用第一帧那份闭包（滚轮就是这么错的）。
 */
@Composable
private fun SwipePager(
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val latestOnPrev by rememberUpdatedState(onPrev)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestCanPrev by rememberUpdatedState(canPrev)
    val latestCanNext by rememberUpdatedState(canNext)
    val density = LocalDensity.current
    val flingVelocity = with(density) { 600.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dragged = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragged += dragAmount
                        tracker.addPosition(change.uptimeMillis, change.position)
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        val width = size.width.toFloat()
                        when (swipeDirection(dragged, velocity, width, flingVelocity)) {
                            1 -> if (latestCanNext) latestOnNext()
                            -1 -> if (latestCanPrev) latestOnPrev()
                        }
                    },
                )
            },
    ) {
        content()
    }
}

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

/**
 * 视图切换的项：常量，不必在每次重组时重新 map 一遍。
 *
 * `GlassSegmentedControl` 会把 items 当作参数比较，每帧换一个新 List
 * （内容相同但引用不同）会让它一直认为「选项变了」。
 */
private val CalendarViewItems: List<Pair<CalendarView, String>> =
    CalendarView.entries.map { it to it.label }

/** 当前浏览的周期是否已经包含今天（是则「今天」按钮置灰）。 */
private fun onTodayPeriod(state: CalendarUiState): Boolean =
    if (state.view == CalendarView.MONTH) {
        state.month == YearMonth.from(state.today)
    } else {
        state.month.year == state.today.year
    }

// ---------------------------------------------------------------- 头部

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    transition: Transition<CalendarUiState>,
    actions: CalendarActions,
    onDismiss: () -> Unit,
    canPrev: Boolean,
    canNext: Boolean,
) {
    val isMonthView = state.view == CalendarView.MONTH
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavButton("‹", "上一${if (isMonthView) "月" else "年"}", enabled = canPrev) {
            if (isMonthView) actions.shiftMonth(-1) else actions.shiftYear(-1)
        }
        CalendarAnimatedContent(transition, Modifier.weight(1f), slide = false) { snapshot ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val monthView = snapshot.view == CalendarView.MONTH
                Text(
                    text = if (monthView) "${snapshot.month.year}年${snapshot.month.monthValue}月" else "${snapshot.month.year}年",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum", platformStyle = CalendarFontMetrics,
                        lineHeightStyle = CalendarLineHeight, color = glassTextPrimary(),
                    ),
                )
                Text(
                    text = if (monthView) "本月 ${snapshot.monthEventCount} 次事件" else "全年 ${snapshot.yearEventCount} 次事件",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 11.sp, lineHeight = 15.sp, fontFeatureSettings = "tnum",
                        platformStyle = CalendarFontMetrics, lineHeightStyle = CalendarLineHeight, color = glassTextSecondary(),
                    ),
                )
            }
        }
        NavButton("›", "下一${if (isMonthView) "月" else "年"}", enabled = canNext) {
            if (isMonthView) actions.shiftMonth(1) else actions.shiftYear(1)
        }
        Spacer(Modifier.width(10.dp))
        NavButton("✕", "关闭日历", onClick = onDismiss)
    }

    // 视图切换：Glasmio 的分段控件（一条玻璃轨道 + 会滑动/拉伸的指示器）。
    // 底栏是常驻的，三格固定为 排序 / ＋ / 日历，视图切换只影响这一页。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassSegmentedControl(
            items = CalendarViewItems,
            selected = state.view,
            onSelected = { actions.setView(it) },
            modifier = Modifier.weight(1f),
        )
        GlassTextButton(
            text = "今天",
            enabled = !onTodayPeriod(state),
            onClick = actions.goToToday,
        )
    }
}

/** 头部圆钮：玻璃材质自己的按压光学。 */
@Composable
private fun NavButton(glyph: String, description: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    GlassCirclePanel(
        modifier = Modifier
            .size(34.dp)
            .semantics { description?.let { contentDescription = it } },
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = glyph,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primary),
        )
    }
}

internal val CalendarFontMetrics = PlatformTextStyle(includeFontPadding = false)
internal val CalendarLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
