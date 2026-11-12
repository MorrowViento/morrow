package com.morrowviento.morrow.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.domain.time.CalendarMath
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 滚轮的三个尺寸常量，高亮条与行高必须共用同一份，否则选中行会和玻璃条错开。 */
private val WheelRowHeight = 34.dp
private const val WheelVisibleRows = 5

/**
 * 单列滚轮。选中项固定在中间，滑动停止后回写选中下标。
 *
 * 说明：对齐依赖 `itemHeight` 与实际行高一致，改字号时要同步改 `itemHeight`；
 * 高亮玻璃条由 [WheelDateTimePicker] 统一绘制，本组件只负责滚动与渲染。
 *
 * **高度必须由本组件自己定死**：滚轮放在表单的 `verticalScroll` 里，
 * 纵向约束是 infinity，LazyColumn 拿到无限高约束会直接抛
 * `IllegalStateException: Vertically scrollable component was measured with an
 * infinity maximum height constraints`（点 + 号必崩的根因）。
 *
 * [state] 由调用方传入：整组滚轮共用外层那一份，外层才能知道「现在有没有人在滑」，
 * 用来点亮中间那条玻璃的镜面高光。
 */
@Composable
fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemHeight: Dp = WheelRowHeight,
    visibleRows: Int = WheelVisibleRows,
) {
    if (values.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, values.lastIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()

    // 关键：下方 `LaunchedEffect(state)` 只在首次组合启动一次，若直接捕获 `values` / `onSelect`，
    // 拿到的永远是**第一帧**的列表与回调。
    // 而回调用的是第一帧的日期/时间（新建表单即「今天 09:00」），于是：
    // 滚了「时」→ 回调把时设成新值但分取第一帧的值；再滚「分」→ 回调又把时写回第一帧的 09，
    // 表现就是「滚哪里都自己调回 2026.11.11 09:00」。
    // rememberUpdatedState 让 collector 每次读到最新的一份。
    val latestValues by rememberUpdatedState(values)
    val latestOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != safeIndex) {
            state.scrollToItem(safeIndex)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .collect { (scrolling, index) ->
                if (!scrolling && index in latestValues.indices) latestOnSelect(index)
            }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = modifier.height(itemHeight * visibleRows),
        // 上下各留 (rows-1)/2 行：第 0 项正好落在视口正中，
        // `firstVisibleItemIndex` 因此就是「中间那一行」。
        contentPadding = PaddingValues(vertical = itemHeight * ((visibleRows - 1) / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(values) { index, value ->
            val selected = index == state.firstVisibleItemIndex
            // 选中与否不再切换字号（字号一变就要重新排版，滚动时会跳），
            // 改成只改缩放与浓淡，走一条临界阻尼弹簧——滚动过程中数字是「浮」过去的。
            val emphasis by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 700f, visibilityThreshold = 0.01f),
                label = "wheelRow",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .graphicsLayer {
                        val scale = 0.90f + 0.10f * emphasis
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.62f + 0.38f * emphasis
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        // 数字没有下伸部，字体自带的 descent 会把字形往上顶一截，
                        // 加上 Trim.Both 之后「数字行居中」才是真居中（图 2 那个偏高的观感）
                        lineHeight = 20.sp,
                        color = if (selected) primary else secondary.copy(alpha = 0.72f),
                    ),
                )
            }
        }
    }
}

/**
 * 年/月/日 + 时/分滚轮。全天事件隐藏时间列。
 *
 * 日的可选范围随年月变化（闰年 2 月 29 日）；外部传入的日期越界时自动夹取。
 *
 * 布局要点（之前「数字不在暗框里居中」的成因）：高亮条必须和**滚轮那一行**共用同一个
 * Box。之前它居中在整个 Column（滚轮 + 下方「年 月 日」单位行）里，单位行占了约 14dp，
 * 高亮条因此整体下沉半行，数字看起来就偏上了。
 */
@Composable
fun WheelDateTimePicker(
    date: LocalDate,
    time: LocalTime,
    isAllDay: Boolean,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 年份不再是「去年..今年+20」：与农历年表 / 日历页共用 1900–2100，
    // 纪念类事件（出生年、历史日期）才选得到。见 CalendarMath.SELECTABLE_YEARS。
    val years = CalendarMath.SELECTABLE_YEARS
    val months = (1..12).toList()
    val maxDay = YearMonth.of(date.year, date.monthValue).lengthOfMonth()
    val days = (1..maxDay).toList()
    val hours = (0..23).toList()
    val minutes = (0..59).toList()
    val safeDay = date.dayOfMonth.coerceAtMost(maxDay)

    // 五个滚轮的状态由这里持有：既能给每个滚轮注入同一份，也能汇总出「有没有人在滑」
    val yearState = rememberLazyListState(CalendarMath.yearIndex(date.year))
    val monthState = rememberLazyListState((date.monthValue - 1).coerceAtLeast(0))
    val dayState = rememberLazyListState((safeDay - 1).coerceAtLeast(0))
    val hourState = rememberLazyListState(time.hour)
    val minuteState = rememberLazyListState(time.minute)
    val scrolling = yearState.isScrollInProgress || monthState.isScrollInProgress ||
        dayState.isScrollInProgress || hourState.isScrollInProgress || minuteState.isScrollInProgress
    val activity by animateFloatAsState(
        targetValue = if (scrolling) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
        label = "wheelActivity",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 高亮条与滚轮同处一个 Box：Box 高度 = 滚轮高度，条居中 ⇒ 正好压住中间那一行
        Box(Modifier.fillMaxWidth()) {
            WheelGlassBand(
                activity = activity,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(WheelRowHeight),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WheelColumn(
                    values = years.map { "$it" },
                    selectedIndex = CalendarMath.yearIndex(date.year),
                    state = yearState,
                    onSelect = { index ->
                        val year = CalendarMath.yearAt(index)
                        // 滚轮首次组合时 snapshotFlow 也会发一次当前下标；若旧数据年份在
                        // 1900–2100 之外，这一下会把事件悄悄改写成边界年，所以只在真的变了才回写。
                        if (year != date.year) {
                            onDateChange(CalendarMath.dateClamped(year, date.monthValue, safeDay))
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                )
                WheelColumn(
                    values = months.map { "$it" },
                    selectedIndex = date.monthValue - 1,
                    state = monthState,
                    onSelect = { index ->
                        val month = months.getOrNull(index) ?: date.monthValue
                        onDateChange(CalendarMath.dateClamped(date.year, month, safeDay))
                    },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    values = days.map { "$it" },
                    selectedIndex = safeDay - 1,
                    state = dayState,
                    onSelect = { index ->
                        val day = days.getOrNull(index) ?: safeDay
                        onDateChange(CalendarMath.dateClamped(date.year, date.monthValue, day))
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!isAllDay) {
                    WheelColumn(
                        values = hours.map { "%02d".format(it) },
                        selectedIndex = time.hour,
                        state = hourState,
                        onSelect = { index ->
                            onTimeChange(time.withHour(hours.getOrNull(index) ?: time.hour))
                        },
                        modifier = Modifier.weight(0.9f),
                    )
                    WheelColumn(
                        values = minutes.map { "%02d".format(it) },
                        selectedIndex = time.minute,
                        state = minuteState,
                        onSelect = { index ->
                            onTimeChange(time.withMinute(minutes.getOrNull(index) ?: time.minute))
                        },
                        modifier = Modifier.weight(0.9f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UnitLabel("年", Modifier.weight(1.3f))
            UnitLabel("月", Modifier.weight(1f))
            UnitLabel("日", Modifier.weight(1f))
            if (!isAllDay) {
                UnitLabel("时", Modifier.weight(0.9f))
                UnitLabel("分", Modifier.weight(0.9f))
            }
        }
    }
}

/**
 * 滚轮中间的选中玻璃条。
 *
 * 不是一块纯色：自上而下四层——极淡的面、顶部镜面高光、底部内阴影、发丝描边，
 * 让数字看得出「压在一块玻璃下面」而不是「有一行被涂灰了」。
 * [activity]（有人正在滑）会把镜面与内阴影一起提亮再落回，就是那一下「流体」的感觉。
 *
 * 没有用 `GlassSurface`：那会去采样本层之外的 backdrop（弹层的跨窗口背景），
 * 折射出来的是这块条背后的**整个应用界面**，与眼前的数字毫无关系，反而更假。
 */
@Composable
private fun WheelGlassBand(activity: Float, modifier: Modifier = Modifier) {
    val ink = glassTextPrimary()
    val dark = LocalGlassDark.current
    val shape = RoundedCornerShape(11.dp)
    // 镜面在深色底上要更亮才看得见；浅色底上用白色只会糊成一片，所以减半
    val specular = if (dark) 0.16f + 0.16f * activity else 0.42f + 0.18f * activity
    Box(
        modifier = modifier
            .clip(shape)
            .background(ink.copy(alpha = 0.07f + 0.02f * activity))
            .drawBehind {
                val radius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
                // 顶部镜面：从中段开始透明，边缘那圈亮光是玻璃的"折射边"
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = specular),
                            Color.White.copy(alpha = specular * 0.25f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.72f,
                    ),
                    cornerRadius = radius,
                )
                // 底部内阴影：下边缘比上边缘暗，玻璃才有厚度
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f + 0.04f * activity)),
                        startY = size.height * 0.45f,
                        endY = size.height,
                    ),
                    cornerRadius = radius,
                )
                // 发丝描边：不加的话边缘会糊在底色里，看起来像一块脏印
                drawRoundRect(
                    color = Color.White.copy(alpha = if (dark) 0.10f else 0.55f),
                    cornerRadius = radius,
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            },
    )
}

@Composable
private fun UnitLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 10.sp, color = glassTextSecondary().copy(alpha = 0.7f)),
    )
}
