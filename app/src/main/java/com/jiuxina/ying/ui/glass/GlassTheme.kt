package com.jiuxina.ying.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.core.GlassAppearance
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

/**
 * 当前渲染外观是否深色。
 *
 * 不能直接用 `isSystemInDarkTheme()`：用户在设置里可以强制浅色/深色。
 * [GlassTheme] 把最终决定放进这个 CompositionLocal，页面底色与文字色都读它，
 * 否则会出现「主题是浅色但底色仍按系统深色画」的分裂。
 */
val LocalGlassDark = staticCompositionLocalOf { false }

/** 用户是否要求减弱动效（无障碍「移除动画」）。页面切换据此跳过淡入淡出。 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** 用户是否要求减弱透明（无障碍「降低透明度」）。底色据此压平，玻璃本身不降级。 */
val LocalReduceTransparency = staticCompositionLocalOf { false }

/**
 * 主题入口。
 *
 * @param darkTheme 最终明暗。跟随系统时由调用方传 `isSystemInDarkTheme()`，
 *   设置里选了浅色/深色时直接传对应值（见 `AppSettings.isDark`）。
 * @param reduceTransparency 减弱透明：**不交给 Glasmio**。
 *   库内建的 `GlassAppearance(reduceTransparency = true)` 会让 `getUsesBackdrop()`
 *   返回 false，`GlassSurface` 直接放弃背景采样与折射，退化成一块纯黑 / 纯白面板——
 *   真机上「打开减弱透明后控件变成纯色块、玻璃效果全没了」就是这么来的。
 *   这里只压平被采样的底色（见 [GlassBackdrop]），玻璃材质完整保留，
 *   既降低背景干扰、提高文字对比度，又不会把控件变成色块。
 */
@Composable
fun GlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassDark provides darkTheme,
        LocalReduceMotion provides reduceMotion,
        LocalReduceTransparency provides reduceTransparency,
    ) {
        GlasmioTheme(
            isDark = darkTheme,
            appearance = GlassAppearance(),
        ) {
            content()
        }
    }
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
    val dark = LocalGlassDark.current
    val colors = if (dark) {
        listOf(Color(0xFF151A35), Color(0xFF2A1436), Color(0xFF3F1630))
    } else {
        listOf(Color(0xFF3B6FE0), Color(0xFF9B4BE8), Color(0xFFE84B8A))
    }
    Box(modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
        if (LocalReduceTransparency.current) {
            // 「减弱透明」的正确落点是**被采样的内容**，不是玻璃材质本身：
            // 压平高饱和渐变后，玻璃仍会折射/高光/模糊，只是背后不再有花哨色块穿过，
            // 面板上的文字对比度随之提高。刻意保留一层柔和渐变，避免又退化成纯黑/纯白。
            val quiet = if (dark) {
                listOf(Color(0xFF20263F), Color(0xFF2C2743))
            } else {
                listOf(Color(0xFFF3F4F9), Color(0xFFE7E9F3))
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(quiet.map { it.copy(alpha = 0.88f) })),
            )
        }
    }
}

/** Glasmio 主题配色（Composable 取值器，需在组合内调用）。 */
@Composable
fun glassColors() = GlasmioTheme.colors

/**
 * 玻璃上的主文字色。
 *
 * 跟随 [LocalGlassDark] 而不是系统深浅：用户强制浅色主题时，
 * 玻璃材质已经是浅色，再用白字会完全看不见。
 */
@Composable
fun glassTextPrimary(): Color =
    if (LocalGlassDark.current) Color.White else Color(0xFF1C1C1E)

/** 玻璃上的次级文字色（分类、日期、说明文字）。 */
@Composable
fun glassTextSecondary(): Color =
    if (LocalGlassDark.current) {
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
