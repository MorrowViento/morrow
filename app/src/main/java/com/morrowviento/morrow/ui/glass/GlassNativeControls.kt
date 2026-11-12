package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.scaffold.GlasmioOverscrollHost
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.ui.GlasmioSwitch

/**
 * 分段控件（Glasmio `GlasmioSegmentedControl`）。
 *
 * 之前日历头部的「月 / 年 / 列表」是三颗各自独立的玻璃按钮、选中态靠换材质：
 * 切换时没有指示器滑动，也没有按下时「指示块被拉过去」的粘连感。
 * 库的分段控件是一条玻璃轨道 + 一块会滑动/拉伸的指示器（`positionAnimation`
 * 走同一条弹簧），并且按 iOS 的规则处理文字压暗与禁用态。
 */
@Composable
fun <T> GlassSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    enabled: Boolean = true,
) {
    GlasmioSegmentedControl(
        items = items,
        selected = selected,
        onSelected = onSelected,
        modifier = modifier,
        height = height,
        enabled = enabled,
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
