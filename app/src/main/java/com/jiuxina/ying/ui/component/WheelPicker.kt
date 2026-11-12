package com.jiuxina.ying.ui.component

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.domain.time.CalendarMath
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 单列滚轮。选中项固定在中间，滑动停止后回写选中下标。
 *
 * 说明：对齐依赖 `itemHeight` 与实际行高一致，改字号时要同步改 `itemHeight`；
 * 高亮条由 [WheelDateTimePicker] 统一绘制，本组件只负责滚动与渲染。
 *
 * **高度必须由本组件自己定死**：滚轮放在表单的 `verticalScroll` 里，
 * 纵向约束是 infinity，LazyColumn 拿到无限高约束会直接抛
 * `IllegalStateException: Vertically scrollable component was measured with an
 * infinity maximum height constraints`（点 + 号必崩的根因）。
 */
@Composable
fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 34.dp,
    visibleRows: Int = 5,
) {
    if (values.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, values.lastIndex)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
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
        contentPadding = PaddingValues(vertical = itemHeight * (visibleRows / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(values) { index, value ->
            val selected = index == state.firstVisibleItemIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = if (selected) 17.sp else 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) primary else secondary.copy(alpha = 0.55f),
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
    val primary = glassTextPrimary()
    // 年份不再是「去年..今年+20」：与农历年表 / 日历页共用 1900–2100，
    // 纪念类事件（出生年、历史日期）才选得到。见 CalendarMath.SELECTABLE_YEARS。
    val years = CalendarMath.SELECTABLE_YEARS
    val months = (1..12).toList()
    val maxDay = YearMonth.of(date.year, date.monthValue).lengthOfMonth()
    val days = (1..maxDay).toList()
    val hours = (0..23).toList()
    val minutes = (0..59).toList()
    val safeDay = date.dayOfMonth.coerceAtMost(maxDay)

    Box(modifier = modifier.fillMaxWidth()) {
        // 中间高亮条：只做视觉定位，不参与手势
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(primary.copy(alpha = 0.07f)),
        )
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WheelColumn(
                    values = years.map { "$it" },
                    selectedIndex = CalendarMath.yearIndex(date.year),
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
                    onSelect = { index ->
                        val month = months.getOrNull(index) ?: date.monthValue
                        onDateChange(CalendarMath.dateClamped(date.year, month, safeDay))
                    },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    values = days.map { "$it" },
                    selectedIndex = safeDay - 1,
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
                        onSelect = { index ->
                            onTimeChange(time.withHour(hours.getOrNull(index) ?: time.hour))
                        },
                        modifier = Modifier.weight(0.9f),
                    )
                    WheelColumn(
                        values = minutes.map { "%02d".format(it) },
                        selectedIndex = time.minute,
                        onSelect = { index ->
                            onTimeChange(time.withMinute(minutes.getOrNull(index) ?: time.minute))
                        },
                        modifier = Modifier.weight(0.9f),
                    )
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
