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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
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
 * 厚度 → 模糊半径（0.5dp → 25.5dp）。
 *
 * 这是库真正开放给调用方的连续光学参数（`GlassSurface(frostRadius = ...)` →
 * `BackdropEffectScope.blur(px)`），也是「磨砂程度」的唯一来源：
 * 0 附近背景细节清晰可辨（只剩边缘折射），1 附近背后内容被彻底糊开。
 *
 * **不再按厚度切换材质枚举**。上一版把 0.45 以上映射到 `RegularMedium`，而
 * `MediumLargeDark.fills` 是三层 `#1A1A1A`（`Luminosity` 0.7 / 0.9 /
 * **`Lighten` 1.0**）：`Lighten` 会把每个比 26/255 暗的像素抬到 26/255，
 * 于是深色下整块面板被压成一片均匀的 #1A1A1A——**这才是「怎么看都不透明」的真凶**。
 * 浅色下对应的是 `White 0.7 Lighten`，同样接近实心白。
 * 现在全部走 `material = null`（库的默认「按尺寸自动挑面层」），它没有任何
 * `fills`，采样到的背景会原样透上来；厚度只由模糊半径和 [veilAlphaOf] 决定。
 *
 * 上限压在 25.5dp：再大在 3x 屏上就是 76px 的高斯，逐帧重绘的代价高于观感收益。
 */
internal fun frostRadiusOf(frost: Float): Dp = (0.5f + 25f * frost.coerceIn(0f, 1f)).dp

/**
 * 厚度 → 面板内侧色纱的不透明度（0 → 0.26）。
 *
 * 色纱只负责「奶白感」，**绝不能负责透明度**：上一版峰值 0.72、默认档就有 0.40，
 * 相当于每张卡片上又盖了一层四成不透明的实色，透过去的东西自然全被糊死。
 *
 * 现在峰值压到 0.26，且用 2 次幂（前段极轻、后段才明显）：
 * 四个档位落在 0 / 0.03 / 0.10 / 0.23，任何一档都留得住背后的轮廓。
 * 「降低透明度」这个无障碍开关仍单独走 0.72（见 [glassVeil]），那是用户的明确诉求。
 */
internal fun veilAlphaOf(frost: Float): Float {
    val t = frost.coerceIn(0f, 1f)
    return 0.26f * t * t
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
 * **为什么必须是一张「有内容的背景」**：玻璃透不透，肉眼判断的唯一依据是
 * 「背后的东西有没有被糊开、有没有被边缘折射弯」。上一版这里只有一个近乎纯色的
 * 深色渐变，采样出来的每个像素都一样——那么无论透明度是 0 还是 1、模糊是 0dp 还是
 * 24dp，面板看起来都是同一块平板，唯一还能动的是材质自己画的内阴影与边缘光，
 * 于是就有了「调厚度只看到边缘发光变了」的反馈。
 *
 * 所以这里改成一幅**丝缎质感的背景**（和 Glasmio 示例里那张壁纸同一路数，但仍是
 * 中性色，没有紫蓝）：
 *
 * 1. 底色：库的 `groupedBackground`；
 * 2. 三团大色斑：暖象牙（左上）、冷钢灰（右侧）、淡天青（左下）——负责「透过去有颜色」；
 * 3. 顶部一团柔光：负责标题区的亮度，和上一版一致；
 * 4. 两道路径光带（一条宽的柔光 + 一条发丝级高光）：负责「透过去有边缘」。
 *    高光带是整幅背景里唯一的高频细节，模糊半径一小一大，它就从「一道锐利的光」
 *    变成「一片发白」——**厚度的差异就是靠它被看见的**。
 *
 * 渐变一律淡到「同色 alpha=0」而不是 `Color.Transparent`：Compose 的渐变按非预乘
 * 插值，淡向黑色透明会在色斑边缘留一圈脏灰。
 *
 * 绘制走 `drawWithCache`：brush 只在尺寸变化时重建，滚动时零分配。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val colors = GlasmioTheme.colors
    val dark = LocalGlassDark.current
    val base = colors.groupedBackground
    // 顶部柔光：只能是「底色 → 卡片色」之间的一半，等于卡片色的话顶部卡片会和背景糊在一起
    val glow = lerp(base, colors.elevatedBackground, 0.6f)

    val warm = if (dark) Color(0xFF2A2016) else Color(0xFFFFDCB0)
    val cool = if (dark) Color(0xFF141C26) else Color(0xFFC6D6E8)
    val sky = if (dark) Color(0xFF0C1724) else Color(0xFFA8C4E6)
    val sheen = Color.White.copy(alpha = if (dark) 0.14f else 0.55f)
    val shade = (if (dark) Color.Black else Color(0xFF6E6558)).copy(alpha = if (dark) 0.55f else 0.32f)

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val blobs = listOf(
                    // 暖光在左上，冷光在右，天青压在底部：和示例壁纸同一套冷暖关系
                    Brush.radialGradient(
                        listOf(warm, warm.copy(alpha = 0f)),
                        center = Offset(w * 0.10f, h * 0.08f),
                        radius = h * 0.78f,
                    ),
                    Brush.radialGradient(
                        listOf(cool, cool.copy(alpha = 0f)),
                        center = Offset(w * 1.04f, h * 0.40f),
                        radius = h * 0.72f,
                    ),
                    Brush.radialGradient(
                        listOf(sky, sky.copy(alpha = 0f)),
                        center = Offset(w * 0.22f, h * 1.00f),
                        radius = h * 0.85f,
                    ),
                    Brush.radialGradient(
                        listOf(glow, glow.copy(alpha = 0f)),
                        center = Offset(w * 0.5f, h * 0.06f),
                        radius = size.maxDimension * 0.9f,
                    ),
                )
                // 光带画在「3 倍屏幕大小、以屏幕为中心」的一块矩形上（旋转后仍能盖满四角），
                // 所以屏幕上的 0..1 只落在这块矩形的中间三分之一：
                // bandY(p) = (p + 1) / 3。写死 0..1 的话光带会整条跑到屏幕外。
                fun bandY(p: Float): Float = (p + 1f) / 3f
                // 宽光带：一条柔和的亮面 + 一条相邻的暗面，形成「布料折了一下」的体积
                val broad = Brush.verticalGradient(
                    bandY(0.00f) to sheen.copy(alpha = 0f),
                    bandY(0.26f) to sheen.copy(alpha = 0f),
                    bandY(0.33f) to sheen.copy(alpha = sheen.alpha * 0.62f),
                    bandY(0.40f) to sheen.copy(alpha = 0f),
                    bandY(0.56f) to shade.copy(alpha = shade.alpha * 0.75f),
                    bandY(0.63f) to shade.copy(alpha = 0f),
                    bandY(0.80f) to sheen.copy(alpha = sheen.alpha * 0.30f),
                    bandY(0.88f) to sheen.copy(alpha = 0f),
                )
                // 发丝高光：屏幕上只有一两个像素宽，模糊一开就整条化掉——
                // 「通透」和「厚重」的差别，一半是靠它被看清 / 被糊掉
                val hairline = Brush.verticalGradient(
                    bandY(0.00f) to sheen.copy(alpha = 0f),
                    bandY(0.462f) to sheen.copy(alpha = 0f),
                    bandY(0.4655f) to sheen,
                    bandY(0.469f) to sheen.copy(alpha = 0f),
                    bandY(0.700f) to sheen.copy(alpha = 0f),
                    bandY(0.7030f) to sheen.copy(alpha = sheen.alpha * 0.8f),
                    bandY(0.7060f) to sheen.copy(alpha = 0f),
                )
                val bandLeft = Offset(-w, -h)
                val bandSize = Size(w * 3f, h * 3f)
                onDrawBehind {
                    drawRect(base)
                    blobs.forEach { drawRect(it) }
                    // 倾斜 18°：光带斜穿屏幕，不会被卡片整块盖住而失去参照
                    rotate(-18f, pivot = Offset(w * 0.5f, h * 0.5f)) {
                        drawRect(broad, topLeft = bandLeft, size = bandSize)
                        drawRect(hairline, topLeft = bandLeft, size = bandSize)
                    }
                }
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
 * 正是 iOS 分组列表卡片的底色。
 *
 * 强度由厚度连续决定（见 [veilAlphaOf]），峰值只有 0.26；
 * 无障碍「降低透明度」打开时才抬到 0.72。
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

/** 强调色（选中态、主操作）。 */
@Composable
fun glassAccent(): Color = GlasmioTheme.colors.accent

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
 * @param tint 面板内侧色纱的显式颜色（强调色按钮、选中格用）。为 null 时走 [glassVeil]。
 * @param prominent 强调材质（Glasmio 的 `GlassEmphasis.Prominent`，iOS 的 tinted glass）。
 *   用于底栏「＋」这类主操作：按下时的高光由材质自己绘制，比内侧叠一层色块更干净。
 * @param accent 强调材质的底色（配合 [prominent] 使用）。
 * @param onClick 非空即成为玻璃按钮：**按压光学直接由 Glasmio 材质绘制**
 *   （`GlassSurface` 内部是 `clickable(indication = null)` + 按压高光采样，
 *   因此天然没有 Material 的矩形 ripple）。点击后不会有任何 Material 指示器。
 *
 * 这里**刻意不传 `material`**：留空即库的默认「按控件尺寸自动挑面层」，它没有任何
 * `fills`，采样到的背景原样透上来。传 `RegularMedium` / `RegularLarge` 反而会拿到
 * 一组接近不透明的填充（深色下是三层 #1A1A1A，其中一层还是 `Lighten`），
 * 面板会变成一块均匀的实色板——上一版「完全不透明」就是这么来的。
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
        // 连续的模糊半径：厚度档位真正改变光学的地方
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
 * 小尺寸玻璃贴片（日历里被选中的那一天这类「格里的一块玻璃」）。
 *
 * 和 [GlassPanel] 的区别只有形状：圆角由调用方给，因为格子是方的、
 * 要用比 `regularCornerRadius`（26dp）小得多的角，否则 46dp 的格子会被磨成胶囊。
 *
 * 性能：网格里只会同时存在一块（选中的那天），一次额外的材质采样可以接受；
 * 42 个格子全都套玻璃则不行——那是上一版明确记过的坑。
 */
@Composable
fun GlassTile(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val veil = glassVeil()
    val shape = ContinuousRoundedRectangle(cornerRadius)
    GlassSurface(
        modifier = modifier,
        shape = shape,
        frostRadius = frostRadiusOf(LocalGlassFrost.current),
        contentAlignment = Alignment.Center,
    ) {
        if (veil != null) {
            Box(Modifier.matchParentSize().clip(shape).background(veil))
        }
        if (tint != null) {
            Box(Modifier.matchParentSize().clip(shape).background(tint))
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
