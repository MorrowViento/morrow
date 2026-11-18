package com.morrowviento.morrow.ui.glass

/**
 * 底栏的「零件」：融合玻璃、选中块、三格图标与加号。
 *
 * 与 [GlassBottomNavigation] 分开，是因为它们的输入全是 px 值与进度回调，
 * 不持有底栏的拖动/吸附状态——底栏本体只负责算几何、串状态。
 */
import com.morrowviento.morrow.ui.component.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import io.github.neoruaa.glasmio.glass.NavigationGlassUnionShape
import kotlin.math.roundToInt

/** 底栏厚度缩放：62dp 高的横条用满 16dp 高斯会把背后抹成一块实色板。 */
internal const val BarFrostScale = 0.55f

/**
 * 两条互不相交的独立轮廓（每个 capsule 一个圆角矩形，纯 Compose Path）。
 *
 * 融合半径 k = 0 时，smooth-min 的零等值线本来就是这个形状，所以常态直接用它：
 * 不必为 SDF 折线付任何计算，＋ 也是干净的一颗圆玻璃。它同时充当
 * [MergedCapsuleUnion] 返回 null（几何非法）时的兜底。
 */
private class PlainCapsulesShape(
    private val capsules: List<LiquidContactCapsule>,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        capsules.forEach { capsule ->
            val radius = minOf(capsule.bounds.width, capsule.bounds.height) / 2f
            path.addRoundRect(RoundRect(capsule.bounds, CornerRadius(radius, radius)))
        }
        return Outline.Generic(path)
    }
}

/**
 * 融合态的裁剪轮廓：**直接用 smooth-min 场的零等值线**。
 *
 * 这是把 Glasmio 的合并真正「移植」过来的关键一步。库在
 * `BottomControlContact.createSurface()` 里就是：
 *
 * ```
 * NavigationNativeUnion.create(first, second, smoothness)
 *     .let { LiquidContactShape(...).withNativeUnion(it) }   // 轮廓换成 native 并集路径
 *     .withRefractionField(...)                              // 光学用同一条 SDF
 * ```
 *
 * 也就是说**裁剪边界和折射场来自同一个 smooth-min 场**。之前这里用
 * `liquidContactShape`（另一套几何：分离时两个独立闭合轮廓、贴合时按曲率求切），
 * 与 `NavigationGlassUnionShape` 的场对不上，于是贴合处出现一条分明的界线，
 * 圆球压到胶囊上时还会把胶囊自己的右端圆弧整条留在圆球里面。
 *
 * 几何本身见 [MergedCapsuleUnion]（库的 `NavigationNativeUnion` 在 0.1.3 是 internal，
 * 无法直接引用，已按源码移植）。
 */
private class UnionOutlineShape(
    private val union: MergedCapsuleUnion,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(union.path)
}

@Composable
internal fun BottomBarUnionGlass(
    pillPx: Float,
    barPx: Float,
    slotPx: Float,
    restGapPx: Float,
    snapZonePx: Float,
    dragInPx: Float,
    ballPx: Float,
    grownBallPx: Float,
    smoothnessPx: Float,
    offset: () -> Float,
    backdrop: GlassContentBackdrop?,
    content: @Composable BoxScope.() -> Unit,
) {
    // 球心与分离态**同一条公式**：常态 = 胶囊右端 + 缝 + 半径，再叠上拖动位移。
    // 这是「球继续跟手」的全部秘密——不另算一个贴合位置，就不会出现球停在原地、
    // 加号继续往前走。
    //
    // 量化到整 2px：拖动时每帧都会换一个 Shape，融合态还要按新几何重算一遍 SDF 折线。
    // 2px 台阶在 3x 屏上约 0.67dp，人眼在「球已经压进胶囊」这段里看不出来，
    // 而重建次数直接砍半——这是融合态不掉帧的关键。字形那一层（第 4 步）不量化，
    // 仍然逐像素跟手，所以手指不会觉得有台阶。
    val ballCenter = ((pillPx + restGapPx + ballPx / 2f + offset()) / 2f).roundToInt() * 2f
    val ballLeft = ballCenter - grownBallPx / 2f
    val gap = ballLeft - pillPx

    // 融合进度：入场距离处为 0，压到最深处为 1。
    // 再走一道平方缓出——刚碰到时颈部长得慢（还看得出是两颗），压进去之后迅速合拢。
    val raw = ((snapZonePx - gap) / (snapZonePx + dragInPx)).coerceIn(0f, 1f)
    val eased = 1f - (1f - raw) * (1f - raw)
    // 量化到 1/32：k 每帧变一点点也会让材质重建，0.5dp 的台阶看不出来。
    val k = smoothnessPx * ((eased * 32f).roundToInt() / 32f)
    val capsuleRect = Rect(0f, (slotPx - barPx) / 2f, pillPx, (slotPx + barPx) / 2f)
    val ballRect = Rect(
        ballLeft,
        (slotPx - grownBallPx) / 2f,
        ballLeft + grownBallPx,
        (slotPx + grownBallPx) / 2f,
    )
    val first = remember(pillPx, barPx, slotPx) {
        LiquidContactCapsule(Size(pillPx, barPx), capsuleRect)
    }
    val second = remember(ballLeft, grownBallPx, slotPx) {
        LiquidContactCapsule(Size(grownBallPx, grownBallPx), ballRect)
    }

    // 只有**真的连通**时才需要 SDF 折线：smooth-min 的零等值线在 gap = k/2 处才合拢，
    // 更小的 k 下轮廓就是两条独立圆角矩形，用纯 Compose Path 画即可。
    val outline: Shape = if (k * 0.5f > 0f) {
        val union = remember(first, second, k) { MergedCapsuleUnion.create(first, second, k) }
        remember(union, first, second) {
            if (union != null) UnionOutlineShape(union) else PlainCapsulesShape(listOf(first, second))
        }
    } else {
        remember(first, second) { PlainCapsulesShape(listOf(first, second)) }
    }
    val shape: Shape = remember(outline, first, second, k, barPx) {
        NavigationGlassUnionShape(
            outline = outline,
            capsules = listOf(first, second),
            smoothness = k,
            // 光学按主控件（胶囊）标定，不把拆出去的圆球算进尺寸。
            minimumDimension = barPx,
        )
    }
    GlassPanel(
        modifier = Modifier.fillMaxSize(),
        shape = shape,
        frostScale = BarFrostScale,
        backdrop = backdrop,
        content = content,
    )
}

/**
 * 暗色选中块：内缩一圈、圆角等于半高的短胶囊。
 *
 * 位移与淡出都在 `graphicsLayer` 里读状态，只失效绘制阶段——拖动时不会重组整条底栏。
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.SelectionBlock(
    width: Dp,
    height: Dp,
    color: Color,
    translationX: () -> Float,
    alpha: () -> Float,
) {
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .width(width)
            .height(height)
            .graphicsLayer {
                this.translationX = translationX()
                this.alpha = alpha()
            }
            .drawBehind {
                // 标准圆弧的胶囊：圆角取半高，四周完全对称。
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            },
    )
}

/**
 * 一格：图标 + 小字。点击与拖动都交给外层 [DampedDragAnimation]，这里只负责画。
 *
 * @param press 按压进度（0…1）。选中项的强调色按它渐变到正文色，
 *   等价于库里的 `lerp(accent, content, pressProgress)`：
 *   按下时蓝色**逐渐变黑**，而不是整块消失。
 * @param sourceScale 这一个格子的放大倍数，只作用于录制给透镜的那份副本。
 */
@Composable
internal fun RowScope.TabCell(
    tab: GlassTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    press: () -> Float = { 0f },
    sourceScale: () -> Float = { 1f },
) {
    // 常态选中除了那块暗色范围，还靠颜色区分；未选中用主文字色而不是次级灰，
    // 否则整条栏看起来是「三个禁用按钮」。
    val ink = glassTextPrimary()
    val base = if (selected) glassAccent() else ink
    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                val scale = sourceScale()
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = tab.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            TabGlyph(tab = tab, color = base, glyphSize = 23.dp, selected = selected)
            if (selected) {
                // 正文色叠在强调色上，alpha = 按压进度：0 → 纯强调色，1 → 纯正文色，
                // 中间就是两者的混合。alpha 只在绘制阶段读，慢速拖动不会逐帧重组底栏。
                TabGlyph(
                    tab = tab,
                    color = ink,
                    glyphSize = 23.dp,
                    selected = selected,
                    modifier = Modifier.graphicsLayer { alpha = press() },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box {
            TabLabel(text = tab.label, color = base, selected = selected)
            if (selected) {
                TabLabel(
                    text = tab.label,
                    color = ink,
                    selected = selected,
                    modifier = Modifier.graphicsLayer { alpha = press() },
                )
            }
        }
    }
}

/** 一格的小字。两份副本字体与字重完全一致，叠在同一个 Box 里不会错位。 */
@Composable
private fun TabLabel(
    text: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        ),
    )
}

/** 三格的图标：都是自绘线条，不依赖字体字形。 */
@Composable
private fun TabGlyph(
    tab: GlassTab,
    color: Color,
    glyphSize: Dp,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = (if (selected) 2.4f else 2.1f).dp.toPx()
        when (tab) {
            GlassTab.HOME -> {
                drawLine(color, Offset(w * 0.50f, h * 0.13f), Offset(w * 0.91f, h * 0.47f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.13f), Offset(w * 0.09f, h * 0.47f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.17f, h * 0.40f), Offset(w * 0.17f, h * 0.89f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.83f, h * 0.40f), Offset(w * 0.83f, h * 0.89f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.17f, h * 0.89f), Offset(w * 0.83f, h * 0.89f), stroke, StrokeCap.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.39f, h * 0.60f),
                    size = Size(w * 0.22f, h * 0.29f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                )
            }
            GlassTab.CALENDAR -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.09f, h * 0.19f),
                    size = Size(w * 0.82f, h * 0.73f),
                    cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
                    style = Stroke(width = stroke),
                )
                drawLine(color, Offset(w * 0.09f, h * 0.40f), Offset(w * 0.91f, h * 0.40f), stroke * 0.9f)
                drawLine(color, Offset(w * 0.32f, h * 0.10f), Offset(w * 0.32f, h * 0.27f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, h * 0.10f), Offset(w * 0.68f, h * 0.27f), stroke, StrokeCap.Round)
                drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.36f, h * 0.65f))
                drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.63f, h * 0.65f))
            }
            GlassTab.SEARCH -> {
                drawCircle(
                    color = color,
                    radius = w * 0.31f,
                    center = Offset(w * 0.43f, h * 0.41f),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color,
                    Offset(w * 0.65f, h * 0.63f),
                    Offset(w * 0.89f, h * 0.88f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

/** 「＋」：两条等长线，比字体里的 + 更居中、笔画更稳。 */
@Composable
internal fun PlusGlyph(color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 2.2.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.1f), Offset(c, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.1f, c), Offset(size.width * 0.9f, c), stroke, StrokeCap.Round)
    }
}
