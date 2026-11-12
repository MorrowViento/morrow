package com.morrowviento.morrow.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morrowviento.morrow.ui.glass.GlassBottomNavigation
import com.morrowviento.morrow.ui.glass.GlassTab

/**
 * 常驻底栏（首页 / 日历 / 设置之外的所有页面共用一条）。
 *
 * 真正的实现在 [GlassBottomNavigation]（`ui/glass`，Glasmio API 的隔离层）：
 * 一条胶囊玻璃栏 + 右侧一颗独立悬浮圆钮，两者共用同一份材质。
 * 这里只做「本项目三个页面」到「三格 + 新建」的翻译，业务代码不用认识 Glasmio 的枚举。
 *
 * 排序不再是底栏的一格：它挪到了首页大标题右侧（见 `HomeHeader` 的排序胶囊），
 * 底栏腾出来的位置留给「搜索」（预留）。
 */
@Composable
fun HomeBottomBar(
    calendarActive: Boolean,
    onHome: () -> Unit,
    onCalendar: () -> Unit,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassBottomNavigation(
        selected = if (calendarActive) GlassTab.CALENDAR else GlassTab.HOME,
        onSelected = { tab ->
            when (tab) {
                GlassTab.HOME -> onHome()
                GlassTab.CALENDAR -> onCalendar()
                GlassTab.SEARCH -> onSearch()
            }
        },
        onCreate = onCreate,
        modifier = modifier,
    )
}

/** 排序：左右两支反向箭头（上/下），不依赖字体里有没有 ↕ 字形。 */
@Composable
fun SortGlyph(color: Color, glyphSize: Dp = 17.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        // 左：向上
        drawLine(color, Offset(w * 0.32f, h * 0.88f), Offset(w * 0.32f, h * 0.12f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.16f, h * 0.31f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.48f, h * 0.31f), stroke, StrokeCap.Round)
        // 右：向下
        drawLine(color, Offset(w * 0.68f, h * 0.12f), Offset(w * 0.68f, h * 0.88f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.88f), Offset(w * 0.52f, h * 0.69f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.88f), Offset(w * 0.84f, h * 0.69f), stroke, StrokeCap.Round)
    }
}
