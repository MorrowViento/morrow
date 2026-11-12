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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import com.morrowviento.morrow.data.settings.GLASS_FROST_DEFAULT
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.LocalGlassDimensions
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import kotlin.math.pow

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
 * 只作用于**玻璃面板内侧**（见 [glassVeil]）：让面板更实、文字更清晰。
 */
val LocalReduceTransparency = staticCompositionLocalOf { false }

/**
 * 当前玻璃厚度（0 = 通透，1 = 厚重）。
 *
 * 单一来源：所有 [GlassPanel] / [GlassCirclePanel] 都读它，因此设置里拖动滑块，
 * 首页卡片、底栏、日历、弹层同时变厚变薄，不需要逐个传参。
 */
val LocalGlassFrost = staticCompositionLocalOf { GLASS_FROST_DEFAULT }

/**
 * 厚度 → 库的材质枚举。
 *
 * **只用到三档，而且 `RegularLarge` 永远不用**：翻 `NavigationGlassMaterial` 的源码可以看到
 * `RegularMedium(5.dp, 24.dp, .6f, .6f, .25f)` 与 `RegularLarge(5.dp, 24.dp, .6f, .6f, .25f)`
 * 五个构造参数完全一致，`fills()` 也共用同一个分支；在原生材质通道里两者只差
 * `luminanceRollOff`（0.65 与 0.4），落在「模糊之后的亮度调色」上，肉眼分辨不出。
 * 上一版把「标准」和「厚重」分别映射到这两个值，所以调档看不出变化。
 *
 * 枚举只负责「面层调色 + 内阴影」这一层；真正让厚度可见的是
 * [frostRadiusOf]（模糊半径）与 [veilAlphaOf]（内侧色纱）。
 */
internal fun materialOf(frost: Float): NavigationGlassMaterial = when {
    frost < 0.12f -> NavigationGlassMaterial.Clear
    frost < 0.55f -> NavigationGlassMaterial.RegularSmall
    else -> NavigationGlassMaterial.RegularMedium
}

/**
 * 厚度 → 模糊半径（2dp → 24dp）。
 *
 * 这是库真正开放给调用方的连续光学参数（`GlassSurface(frostRadius = ...)` →
 * `BackdropEffectScope.blur(px)`），也是「磨砂程度」的唯一来源：
 * 0 附近背景细节清晰可辨，1 附近背后内容被彻底糊开。
 *
 * 上限压在 24dp：再大在 3x 屏上就是 72px 的高斯，逐帧重绘的代价明显高于观感收益。
 */
internal fun frostRadiusOf(frost: Float): Dp = (2f + 22f * frost.coerceIn(0f, 1f)).dp

/**
 * 厚度 → 面板内侧色纱的不透明度（0 → 0.78）。
 *
 * 为什么会话里非得自己盖这层纱：库的面层填充（`faceFill`）在深色下是
 * `Color.White.copy(alpha = 0f)` / 0.1 级别，纯靠它做不出「厚重」的实心感；
 * iOS 的「降低透明度」同样是在材质之上再压一层不透明色。
 *
 * 用 1.15 次幂而不是线性：滑块前半段对应「轻微发雾」的细腻区间，
 * 后半段才快速走向实心，符合人眼对透明度的感知（Weber–Fechner）。
 */
internal fun veilAlphaOf(frost: Float): Float {
    val t = frost.coerceIn(0f, 1f)
    return 0.78f * t.pow(1.15f)
}

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
 *   柔和的同色纱（见 [glassVeil]）。
 * @param frost 玻璃厚度 0~1，透传给每一个玻璃面板。
 */
@Composable
fun GlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    reduceMotion: Boolean = false,
    frost: Float = GLASS_FROST_DEFAULT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassDark provides darkTheme,
        LocalReduceMotion provides reduceMotion,
        LocalReduceTransparency provides reduceTransparency,
        LocalGlassFrost provides frost.coerceIn(0f, 1f),
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
 * **只用 Glasmio 自己的色板**（`GlassColors`）：上一版是蓝紫粉三段渐变，
 * 和液态玻璃的冷白高光放在一起会互相打，所以整页换回库的默认配色——
 * 深色 `groupedBackground`（#000）+ 顶部一团 `elevatedBackground`（#1C1C1E），
 * 浅色 `groupedBackground`（#F2F2F7）+ 顶部一团白，也就是 iOS 分组列表那套底。
 *
 * 为什么不是纯色：玻璃要采样到亮度差才有「透过去」的层次，纯色底会让面板看起来
 * 像一张半透明色纸。这团光是同系的中性色、不是装饰性彩色，所以仍然算「默认配色」。
 *
 * 渐变只在这里定义一份：首页把它放进 `sampledContent`，日历 / 设置等**整页覆盖**
 * 视图必须再画一遍同款当自己的不透明底。否则覆盖层是透明的，下层首页文字会穿出来，
 * 且覆盖层上的玻璃采样到的颜色与周围底色对不上。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val colors = GlasmioTheme.colors
    val base = colors.groupedBackground
    // 光只能是「底色 → 卡片色」之间的一半：等于卡片色的话，顶部那张卡会和背景糊在一起
    val glow = lerp(base, colors.elevatedBackground, 0.6f)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(base)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow, Color.Transparent),
                        // 光源放在顶部偏上：卡片从亮处往暗处排，采样才有变化
                        center = Offset(size.width * 0.5f, size.height * 0.06f),
                        radius = size.maxDimension * 0.9f,
                    ),
                )
            },
    )
}

/**
 * 「减弱透明」的落点：盖在玻璃**内侧**的一层柔和不透明色纱。
 *
 * 玻璃材质（模糊 / 折射 / 高光）由 Glasmio 正常绘制，色纱只把透过面板看到的
 * 背景压低，面板因此更实、文字对比度更高；面板之外的底色仍在，不会变成纯黑 / 纯白块。
 *
 * 纱的颜色取 `elevatedBackground`（深色 #1C1C1E / 浅色 #FFFFFF），
 * 正是 iOS 分组列表卡片的底色，所以「厚重」档看起来就是 iOS 的标准卡片。
 *
 * 强度由厚度连续决定（见 [veilAlphaOf]），不再是一个布尔。
 */
@Composable
private fun glassVeil(): Color? {
    val base = veilAlphaOf(LocalGlassFrost.current)
    // 无障碍「降低透明度」打开时，至少到 0.72 的实度
    val alpha = if (LocalReduceTransparency.current) maxOf(base, 0.72f) else base
    if (alpha <= 0.005f) return null
    return GlasmioTheme.colors.elevatedBackground.copy(alpha = alpha)
}

/** Glasmio 主题配色（Composable 取值器，需在组合内调用）。 */
@Composable
fun glassColors() = GlasmioTheme.colors

/**
 * 玻璃上的主文字色。
 *
 * 直接用库的 `GlassColors.content`（深色白 / 浅色近黑），不再自己写死：
 * 之前硬编码的 `#1C1C1E` 在跟随系统浅色但用户强制浅色时会偏灰。
 */
@Composable
fun glassTextPrimary(): Color = GlasmioTheme.colors.content

/** 玻璃上的次级文字色（分类、日期、说明文字）。 */
@Composable
fun glassTextSecondary(): Color = GlasmioTheme.colors.secondaryContent

/** 玻璃上的三级文字色（脚注、计数）。 */
@Composable
fun glassTextTertiary(): Color = GlasmioTheme.colors.tertiaryContent

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
        material = materialOf(LocalGlassFrost.current),
        // 连续的模糊半径：厚度滑块唯一能真正改变光学的地方
        frostRadius = frostRadiusOf(LocalGlassFrost.current),
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
        material = materialOf(LocalGlassFrost.current),
        frostRadius = frostRadiusOf(LocalGlassFrost.current),
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
