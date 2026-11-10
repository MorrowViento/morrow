package com.jiuxina.ying.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop

/**
 * Glasmio API 隔离层（重构计划决策 D3）。
 *
 * 规则：业务代码不得直接 import `io.github.neoruaa.glasmio.*`，
 * 一律通过本目录的封装调用。Glasmio 版本升级（0.1.2 → 新版本）时，
 * 适配工作仅限 `ui/glass/` 目录内完成。
 */

/** 主题入口。内部使用 GlasmioTheme（浅色/深色自动外观）。 */
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    GlasmioTheme { content() }
}

/** Glasmio 主题配色（Composable 取值器，需在组合内调用）。 */
@Composable
fun glassColors() = GlasmioTheme.colors

/**
 * 玻璃背景宿主（纯色背景版）：把背景色交给 Glasmio 采样，
 * 其上的玻璃控件通过 overlayContent 绘制。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    sampledColor: Color,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    GlasmioBackdropHost(
        sampledColor = sampledColor,
        modifier = modifier,
        overlayContent = overlayContent,
    )
}

/**
 * 玻璃背景宿主（自定义内容版）：sampledContent 提供被采样的页面内容，
 * overlayContent 承载其上的玻璃控件。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    sampledContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    GlasmioBackdropHost(
        modifier = modifier,
        sampledContent = sampledContent,
        overlayContent = overlayContent,
    )
}
