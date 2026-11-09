package com.jiuxina.ying.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost

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

/** Glasmio 主题配色（groupedBackground 等），供业务页取采样背景色。 */
val glassColors
    get() = GlasmioTheme.colors

/** Glasmio 字体排版（headlineLarge 等）。 */
val glassTypography
    get() = GlasmioTheme.typography

/** 玻璃背景宿主：sampledContent 提供被采样背景，overlayContent 承载玻璃控件。 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    sampledContent: @Composable () -> Unit,
    overlayContent: @Composable () -> Unit,
) {
    GlasmioBackdropHost(
        modifier = modifier,
        sampledContent = sampledContent,
        overlayContent = overlayContent,
    )
}
