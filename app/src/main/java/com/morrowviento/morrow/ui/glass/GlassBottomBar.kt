package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.BottomNavigationContact
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.ui.GlasmioNavigationBar
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import io.github.neoruaa.glasmio.ui.NavigationBarSurfaceHeight

/**
 * 底栏的三个位置。
 *
 * 只有三个：主页、日历、搜索（预留）。新建不占位置——它是右侧那颗独立的悬浮圆钮，
 * 和 iOS 26 把「搜索」从 tab 里拆出来单独一颗是同一个排布。
 */
enum class GlassTab(val label: String) {
    HOME("主页"),
    CALENDAR("日历"),
    SEARCH("搜索"),
}

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * 结构完全照搬 Glasmio 示例里的「导航栏 + 悬浮搜索」：
 *
 * - **一条胶囊玻璃栏**：`GlasmioNavigationBar`，图标 + 10sp 小字，选中格上有一块
 *   会滑动/拉伸的透镜指示器（拖动时指示器跟着手指、松手回弹到最近一格）；
 * - **右侧一颗独立圆钮**：和栏同高（62dp）、单独一片玻璃，压在栏的右边；
 * - 两者装在 `BottomNavigationContact` 里，于是**共用同一份材质上下文**：液面高度、
 *   折射、内阴影按两颗控件的并集算，圆钮按下去时高光也会从栏的边缘连过去（iOS 的
 *   「同一块玻璃分成两片」）。材质由宿主绘制，所以这里的透明度是库的原版值，
 *   不再经过本项目的色纱——**这是「底栏不透明」的修法**：之前每个格子各自
 *   `GlassSurface` + 内侧色纱，叠起来就是一块实心板。
 *
 * 按压反馈不用自己写：`GlassSurface(onClick=)` 内部就是 `clickable(indication = null)`
 * 加材质高光采样，按下去是折射与光斑，不是 Material 的矩形涟漪。
 */
@Composable
fun GlassBottomNavigation(
    selected: GlassTab,
    onSelected: (GlassTab) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    createLabel: String = "新建",
) {
    val items = remember {
        listOf(
            // 库只提供 House / Clock / Search 这几个符号；日历用 Clock 表示「还剩多久」，
            // 文字标签已经把语义说清楚了。
            GlasmioTabItem(GlassTab.HOME, GlassTab.HOME.label, SfSymbol.House),
            GlasmioTabItem(GlassTab.CALENDAR, GlassTab.CALENDAR.label, SfSymbol.Clock),
            GlasmioTabItem(GlassTab.SEARCH, GlassTab.SEARCH.label, SfSymbol.Search),
        )
    }
    // 库的「分裂搜索」形变进度：底栏平时不收缩，所以恒为 0。
    // 用一个稳定的 State 而不是每次组合新建，避免内部 derivedState 反复重建。
    val noMorph = remember { mutableFloatStateOf(0f) }

    BottomNavigationContact(
        backdrop = LocalGlassBackdrop.current,
        modifier = modifier,
    ) {
        GlasmioNavigationBar(
            items = items,
            selectedKey = selected,
            onSelected = onSelected,
            // 搜索还是预留位：不展开任何东西，保留形变入口不传 null。
            onExpand = {},
            compactProgress = noMorph,
            compactSize = NavigationBarSurfaceHeight,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(BarGap))
        FloatingCreateButton(onClick = onCreate, label = createLabel)
    }
}

/** 悬浮圆钮与底栏之间的间距：和 iOS 26 一样留一条能看见底色的缝。 */
private val BarGap = 10.dp

/**
 * 右侧独立的圆形按钮（球面玻璃里的「＋」）。
 *
 * 关键是 `emphasis = Regular`：`GlassSurface` 只有在常规强调下才会把自己注册成
 * `BottomNavigationContact` 的成员、由宿主统一画材质；换成 `Prominent` 就退化成
 * 一片自己的玻璃，按下去那一下的光会断开。
 */
@Composable
private fun RowScope.FloatingCreateButton(onClick: () -> Unit, label: String) {
    val ink = glassTextPrimary()
    GlassSurface(
        modifier = Modifier
            .size(NavigationBarSurfaceHeight)
            .semantics { contentDescription = label },
        shape = CircleShape,
        emphasis = GlassEmphasis.Regular,
        onClick = onClick,
    ) {
        PlusGlyph(ink, glyphSize = 22.dp)
    }
}

/** 「＋」：两条等长线，比字体里的 + 更居中、笔画更稳。 */
@Composable
private fun PlusGlyph(color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 1.9.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.1f), Offset(c, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.1f, c), Offset(size.width * 0.9f, c), stroke, StrokeCap.Round)
    }
}
