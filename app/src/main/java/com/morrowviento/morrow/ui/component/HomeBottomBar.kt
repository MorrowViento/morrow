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
import com.morrowviento.morrow.ui.glass.GlassContentBackdrop
import com.morrowviento.morrow.ui.glass.GlassTab

/**
 * 常驻底栏（首页 / 日历 / 设置之外的所有页面共用一条）。
 *
 * 真正的实现在 [GlassBottomNavigation]（`ui/glass`，Glasmio API 的隔离层）：
 * 一条矮胶囊玻璃栏 + 右侧独立悬浮的圆形「＋」，都采样壁纸与列表内容的合成层。
 * 常态选中是一块**内缩一圈的暗色圆角方块**（会滑动），按住/拖动时抬升成一颗
 * 跟随手指的玻璃珠（库的 `DampedDragAnimation`）；珠子由库原生控制透镜绘制，
 * 会把栏内图标文字折射进去。按住圆球往左拖，两块玻璃之间会长出 S 形收腰吸附。
 * 这里只做「本项目三个页面」到「三格 + 新建」的翻译，业务代码不碰 Glasmio 枚举。
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
    /** 搜索页打开时选中第三格。它和日历互斥，两个都为 false 就是首页。 */
    searchActive: Boolean = false,
    /** 壁纸 + 页面内容的合成采样层：底栏靠它透出背后的卡片边缘。 */
    backdrop: GlassContentBackdrop? = null,
) {
    GlassBottomNavigation(
        selected = when {
            searchActive -> GlassTab.SEARCH
            calendarActive -> GlassTab.CALENDAR
            else -> GlassTab.HOME
        },
        onSelected = { tab ->
            when (tab) {
                GlassTab.HOME -> onHome()
                GlassTab.CALENDAR -> onCalendar()
                GlassTab.SEARCH -> onSearch()
            }
        },
        onCreate = onCreate,
        modifier = modifier,
        backdrop = backdrop,
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
