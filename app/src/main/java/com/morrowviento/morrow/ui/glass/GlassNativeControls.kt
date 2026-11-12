package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.scaffold.GlasmioOverscrollHost
import io.github.neoruaa.glasmio.ui.GlasmioAdaptiveSlider
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.ui.GlasmioSwitch

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
 * 系统样式的玻璃开关（Glasmio `GlasmioSwitch`）。
 *
 * 替换掉设置页自绘的「圆角轨道 + 小圆点 + tween(150)」：库的实现是 iOS 的
 * 胶囊轨道（开态用系统绿）、带按压缩放与透镜折射，关闭「减弱动效」时也会自己降级。
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlasmioSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * 连续滑块（Glasmio `GlasmioAdaptiveSlider`）。
 *
 * iOS 26 那种「按下去整条轨道展开成三段刻度、拖动时跟手、松手弹回胶囊」的滑块，
 * 数值是 0~1 的浮点，正好用来做连续调节。
 *
 * 用它而不是 Material3 的 `Slider`：后者的拇指、轨道、涟漪都是 Material 形状，
 * 和玻璃材质放一起会像两套系统拼起来；库的滑块自带透镜与按压高光。
 *
 * @param labels 必须**正好三段**（库内 `require`），对应 0 / 50 / 100% 三处刻度说明。
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    labels: List<String> = listOf("通透", "标准", "厚重"),
) {
    GlasmioAdaptiveSlider(
        value = value.coerceIn(0f, 1f),
        onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        labels = labels,
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
