package com.morrowviento.morrow.ui.glass

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.LocalGlassDimensions
import io.github.neoruaa.glasmio.glass.GlassEmphasis
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

/**
 * 用户是否要求减弱透明（无障碍「降低透明度」）。
 *
 * 只作用于**玻璃面板内侧**（见 [glassVeil]）：让面板更实、文字更清晰；
 * 页面底色的蓝紫粉渐变在任何模式下都原样保留。
 */
val LocalReduceTransparency = staticCompositionLocalOf { false }

/**
 * 主题入口。
 *
 * @param darkTheme 最终明暗。跟随系统时由调用方传 `isSystemInDarkTheme()`，
 *   设置里选了浅色/深色时直接传对应值（见 `AppSettings.isDark`）。
 * @param reduceTransparency 减弱透明：**不交给 Glasmio**。
 *   库内建的 `GlassAppearance(reduceTransparency = true)` 会让 `getUsesBackdrop()`
 *   与 `getUsesRefraction()` 一起返回 false，`GlassSurface` 放弃背景采样与折射，
 *   退化成一块纯黑 / 纯白面板——真机上「打开减弱透明后控件变成纯色块」就是这么来的。
 *   这里始终保持 `GlassAppearance()`（液态玻璃全开），只在玻璃面板内侧加一层
 *   柔和色纱（见 [glassVeil]）。**不动背景渐变**：真机上曾把「压平整个底色」
 *   当成减弱透明，结果浅色变整屏近白、深色变整屏近黑，颜色全丢了，那是错的。
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
    // 渐变永远画满，不受「减弱透明」影响：那是面板的事，不是背景的事。
    // 曾经在这里叠过一层 alpha 0.88 的中性色，真机上浅色直接整屏泛白、深色整屏发黑，
    // 等于把应用的配色抹掉——已废弃。
    Box(modifier.fillMaxSize().background(Brush.linearGradient(colors)))
}

/**
 * 「减弱透明」的落点：盖在玻璃**内侧**的一层柔和不透明色纱。
 *
 * 玻璃材质（模糊 / 折射 / 高光）由 Glasmio 正常绘制，色纱只把透过面板看到的
 * 背景压低到约 45%，面板因此更实、文字对比度更高；面板之外的彩色渐变、以及
 * 从色纱透出来的那部分颜色都还在，不会变成纯黑 / 纯白色块。
 *
 * 取带一点紫的浅色 / 深色而不是纯白 / 纯黑，是为了和蓝紫粉渐变同色系。
 */
@Composable
private fun glassVeil(): Color? {
    if (!LocalReduceTransparency.current) return null
    return if (LocalGlassDark.current) {
        Color(0xFF24213F).copy(alpha = 0.55f)
    } else {
        Color(0xFFF7F3FF).copy(alpha = 0.55f)
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
 *
 * @param capsule 整条胶囊形状（底栏这类横向长控件用）。默认的
 *   `ContinuousRoundedRectangle(26dp)` 在 64dp 高的底栏上会露出四个直边，不像 iOS 底栏。
 * @param tint 面板内侧色纱的显式颜色（强调色按钮用）。为 null 时走 [glassVeil]：
 *   只有「减弱透明」开启才加纱，否则保持纯玻璃。
 * @param prominent 强调材质（Glasmio 的 `GlassEmphasis.Prominent`，iOS 的 tinted glass）。
 *   用于底栏「＋」这类主操作：按下时的高光由材质自己绘制，比内侧叠一层色块更干净。
 * @param accent 强调材质的底色（配合 [prominent] 使用）。
 * @param onClick 非空即成为玻璃按钮：**按压光学直接由 Glasmio 材质绘制**
 *   （`GlassSurface` 内部是 `clickable(indication = null)` + 按压高光采样，
 *   因此天然没有 Material 的矩形 ripple）。点击后不会有任何 Material 指示器。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    capsule: Boolean = false,
    tint: Color? = null,
    prominent: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val veil = glassVeil()
    val shape = glassPanelShape(capsule)
    GlassSurface(
        modifier = modifier,
        shape = shape,
        emphasis = if (prominent) GlassEmphasis.Prominent else GlassEmphasis.Regular,
        navigationSurfaceColor = accent,
        onClick = onClick,
        enabled = enabled,
        contentAlignment = contentAlignment,
    ) {
        // 色纱是 content 的第一个子项，因此盖住玻璃绘制、又垫在真正的内容下面。
        // matchParentSize 是 BoxScope 的成员函数，不需要（也不能）import。
        if (veil != null) {
            Box(Modifier.matchParentSize().clip(shape).background(veil))
        }
        // 色纱之上再叠一层显式 wash（强调色 / 选中态）。两者互不覆盖：
        // 之前 `tint` 直接把 veil 顶掉，导致「减弱透明 + 选中格」反而更透。
        if (tint != null) {
            Box(Modifier.matchParentSize().clip(shape).background(tint))
        }
        content()
    }
}

/** 圆形玻璃面板（悬浮按钮用）。 */
@Composable
fun GlassCirclePanel(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    prominent: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val veil = glassVeil()
    GlassSurface(
        modifier = modifier,
        shape = CircleShape,
        emphasis = if (prominent) GlassEmphasis.Prominent else GlassEmphasis.Regular,
        navigationSurfaceColor = accent,
        onClick = onClick,
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        if (veil != null) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(veil))
        }
        if (tint != null) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(tint))
        }
        content()
    }
}

/**
 * 与 Glasmio 当前主题一致的形状。
 *
 * 「常规」必须读 `LocalGlassDimensions`：`GlassSurface` 的默认形状用的就是它，
 * 写死 26.dp 会在库改默认值时悄悄跑偏（色纱和材质就对不上了）。
 */
@Composable
fun glassPanelShape(capsule: Boolean = false): Shape =
    if (capsule) {
        Capsule()
    } else {
        ContinuousRoundedRectangle(LocalGlassDimensions.current.regularCornerRadius)
    }
