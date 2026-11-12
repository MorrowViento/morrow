package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.scaffold.GlasmioOverscrollHost
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl

/**
 * 分段控件（Glasmio `GlasmioSegmentedControl`）。
 *
 * 一条玻璃轨道 + 一块会滑动/拉伸的指示器（`positionAnimation` 走同一条弹簧），
 * 按 iOS 的规则处理文字压暗与禁用态。**指示器是真正的透镜材质**
 * （库内 `NativeControlLensStyle.SegmentedIndicator`）：按下时它几乎完全透明，
 * 边缘把身后的图标折射、挤歪——这就是示例里 Icon Segmented Control 的那一下。
 *
 * @param itemContent 非空时接管每一格的绘制（图标版：只画图标不画文字），
 *   文字标签仍会作为无障碍描述传给读屏。
 * @param deferSelection 点下去先只做按压反馈、抬手才回调选中（库用于「拖动选择」场景，
 *   默认 false 即点即选）。
 */
@Composable
fun <T> GlassSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    enabled: Boolean = true,
    deferSelection: Boolean = false,
    itemContent: (@Composable BoxScope.(T, Boolean) -> Unit)? = null,
) {
    GlasmioSegmentedControl(
        items = items,
        selected = selected,
        onSelected = onSelected,
        modifier = modifier,
        height = height,
        enabled = enabled,
        deferSelection = deferSelection,
        itemContent = itemContent,
    )
}

/**
 * 页面级回弹（Glasmio `GlasmioOverscrollHost`）。
 *
 * Android 默认的拉伸是「整块内容往边缘拽」，iOS 是「内容先被拉出一段、松手弹回」。
 * 库把后者的实现（含短内容 / 空列表 / 加载态的回弹）封装好了，直接把页面主体包进去即可；
 * 它通过 `LocalOverscrollFactory` 接管内部的滚动容器，不需要改 LazyColumn。
 */
@Composable
fun GlassOverscrollHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlasmioOverscrollHost(modifier = modifier, content = content)
}
