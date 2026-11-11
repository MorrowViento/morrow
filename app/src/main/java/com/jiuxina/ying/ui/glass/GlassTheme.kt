package com.jiuxina.ying.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop

/**
 * Glasmio API 隔离层（重构计划决策 D3）。
 *
 * 规则：业务代码不得直接 import `io.github.neoruaa.glasmio.*`，
 * 一律通过本目录的封装调用。Glasmio 版本升级（0.1.2 → 新版本）时，
 * 适配工作仅限 `ui/glass/` 目录内完成。
 *
 * 本文件签名以 **v0.1.2 标签**源码为准（即 Maven Central 发布版），
 * 而非 main 分支——main 分支新增了 `sampledColor` 便捷重载，0.1.2 中不存在。
 */

/** 主题入口。内部使用 GlasmioTheme（浅色/深色自动外观）。 */
@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    GlasmioTheme { content() }
}

/**
 * 页面底色（同时充当玻璃的采样源）。
 *
 * 渐变只在这里定义一份：首页把它放进 `sampledContent`，日历等**整页覆盖**视图
 * 必须再画一遍同款渐变当自己的不透明底。否则覆盖层是透明的，下层首页文字会穿出来
 * （日历与列表重叠的成因），且覆盖层上的玻璃采样到的颜色与周围底色对不上。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val colors = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF151A35), Color(0xFF2A1436), Color(0xFF3F1630))
    } else {
        listOf(Color(0xFF3B6FE0), Color(0xFF9B4BE8), Color(0xFFE84B8A))
    }
    Box(modifier.fillMaxSize().background(Brush.linearGradient(colors)))
}

/** Glasmio 主题配色（Composable 取值器，需在组合内调用）。 */
@Composable
fun glassColors() = GlasmioTheme.colors

/**
 * 玻璃上的主文字色。
 *
 * GlasmioTheme 默认跟随系统外观，故用系统深色态判断即可与材质明暗保持一致；
 * 不去读 Glasmio 内部颜色字段，避免把未公开的 API 细节漏进业务层。
 */
@Composable
fun glassTextPrimary(): Color =
    if (isSystemInDarkTheme()) Color.White else Color(0xFF1C1C1E)

/** 玻璃上的次级文字色（分类、日期、说明文字）。 */
@Composable
fun glassTextSecondary(): Color =
    if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.62f)
    } else {
        Color(0xFF3C3C43).copy(alpha = 0.6f)
    }

/**
 * 玻璃背景宿主（自定义内容版，与 Glasmio 0.1.2 原始签名一一对应）。
 *
 * sampledContent 为被采样的底层内容，overlayContent 为其上的玻璃控件；
 * 玻璃控件**不得**放入 sampledContent，否则会自我采样形成递归。
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

/**
 * 玻璃背景宿主（纯色背景版）。
 *
 * Glasmio 0.1.2 无 sampledColor 便捷重载，故用纯色 Box 充当被采样内容。
 */
@Composable
fun GlassBackdropHost(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    GlasmioBackdropHost(
        modifier = modifier,
        sampledContent = { Box(Modifier.fillMaxSize().background(backgroundColor)) },
        overlayContent = overlayContent,
    )
}

/**
 * 玻璃面板：Glasmio 的核心材质控件（模糊 / 折射 / 高光）。
 *
 * backdrop 默认取 `LocalGlassBackdrop`，因此必须置于 [GlassBackdropHost]
 * 的 overlayContent 槽内使用。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** 圆形玻璃面板（悬浮按钮用）。 */
@Composable
fun GlassCirclePanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = CircleShape,
        contentAlignment = Alignment.Center,
        content = content,
    )
}
