package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.NavigationGlassUnionShape
import io.github.neoruaa.glasmio.glass.liquidContactShape
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 底栏的三个位置。
 *
 * 只有三个：主页、日历、搜索（预留）。新建不占位置——它是右侧那颗独立的圆球，
 * 和 iOS 26 把「搜索」从 tab 里拆出来单独一颗是同一个排布。
 */
enum class GlassTab(val label: String) {
    HOME("主页"),
    CALENDAR("日历"),
    SEARCH("搜索"),
}

/** 胶囊高度：Glasmio `NavigationBarSurfaceHeight`。 */
private val BarHeight = 62.dp

/** 右侧独立圆球直径：比胶囊略小 2dp，融合时胶囊才是「主体」。 */
private val BallDiameter = 60.dp

/**
 * 底栏槽高。
 *
 * 必须容得下**按下后**的东西：抬起的透镜是 `LensHeight + LensGrowth = 54 + 16 = 70dp`，
 * 比 62dp 的胶囊上下各凸 4dp。槽高取 70dp，透镜才不会被父容器裁掉一条边。
 */
private val SlotHeight = 70.dp

/** 常态胶囊右端到圆球左端的缝。 */
private val BarGap = 12.dp

/**
 * 两片玻璃开始「黏上」的间隙：Glasmio `BottomControlContact` 的 `contactDistance`。
 *
 * 直接作为 `liquidContactShape` 的 contactDistance 传进去，所以轮廓什么时候长出颈部、
 * 光学场什么时候连成一片，用的是同一个阈值——不会出现「轮廓有颈、材质还是空的」。
 */
private val ContactDistance = 4.dp

/**
 * 光学场并集半径：Glasmio `BottomControlContact.nativeSmoothness = 6.dp`。
 *
 * 这个值只影响 `NavigationGlassUnionShape` 的 compound shader 怎么把两个 capsule 的
 * 场平滑并起来，不参与裁剪。
 */
private val UnionSmoothness = 6.dp

/** 圆球最多能往左 / 右拖多远。往左要够得着胶囊（缝 12dp，拖 8dp 就贴上）。 */
private val BallDragLeft = 20.dp
private val BallDragRight = 8.dp

/**
 * 按住时圆球长大的量。
 *
 * 只做「按下去有一点涨」的手感，**不再靠它凑融合**——融合由 [liquidContactShape]
 * 按间隙连续生长，靠长大去制造接触会在松手时留下跳变。
 */
private val BallPressGrowth = 3.dp

/**
 * 常态暗色选中范围：内缩一圈的圆角块。
 *
 * 高度 48dp 对 62dp 栏高，上下各留 7dp；圆角 23dp（≈ 高的一半）——
 * 和 31dp 半径的胶囊是同一族连续圆角，看起来像「把胶囊等比例缩小了一号」。
 */
private val IndicatorHeight = 48.dp
private val IndicatorInsetX = 10.dp
private val IndicatorCorner = 23.dp

/**
 * 抬起透镜的高度：Glasmio `ExpandedNavigationItemHeight = 54.dp`。
 *
 * 和暗色块 48dp 不同——透镜是**整格宽**的一块胶囊，抬起时由
 * [nativeControlLensGeometry] 在宽高上各加 16dp，所以会向上、向下各凸出 8dp，
 * 越过 62dp 的胶囊边界，和示例图三那样「鼓出来一坨」。
 * 之前用 70dp 的正圆，永远只是贴在栏上的一个圆盘，鼓不出来。
 */
private val LensHeight = 54.dp

/** 透镜抬起时宽高各增长的量（库 `nativeControlLensGeometry` 里写死的 16dp）。 */
private val LensGrowth = 16.dp

/**
 * 透镜里那层「放大后的图标文字」的放大倍数。
 *
 * 库在 `GlasmioNavigationBar` 里给录制源单独套了 1.16 倍（`lensSourceTabContentScale`），
 * 所以按下时珠子里的 house / 日历看着会比图标本身大一圈，像被镜片顶起来。
 */
private const val LensSourceScale = 0.16f

/**
 * 库的 `liquidContactShape` 在求轮廓时会用到 `androidx.graphics.path` 的 native 实现
 * （`PathIterator` + `Path.combine`）。真机上没问题，但 Robolectric 的 JVM 里没有
 * 这个 .so，`StartupSmokeTest` 渲染首页时会抛 `UnsatisfiedLinkError` 让整个测试红掉。
 *
 * 这里只做**降级**：先尝试库的形状，第一次抛链接错误就永久退回 [PlainCapsulesShape]
 * （两条互不相交的独立轮廓，纯 Compose Path）。真机上永远走第一条分支，
 * 也就是库的原实现；降级只在没有 native 的环境里生效。
 */
private class ResilientContactShape(
    private val primary: Shape,
    private val fallback: Shape,
) : Shape {
    private var primaryUnavailable = false

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (!primaryUnavailable) {
            try {
                return primary.createOutline(size, layoutDirection, density)
            } catch (error: LinkageError) {
                primaryUnavailable = true
            }
        }
        return fallback.createOutline(size, layoutDirection, density)
    }
}

/** 降级轮廓：每个 capsule 各自一个圆角矩形，互不相连（不做颈部）。 */
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

/** 底栏厚度缩放：62dp 高的横条用满 16dp 高斯会把背后抹成一块实色板。 */
private const val BarFrostScale = 0.55f

/**
 * 库 `GlassControls.kt` 里的 `nativeControlLensGeometry`（0.1.3 中是 internal，无法直接调）。
 *
 * 逐行照搬：只改**测量出来的尺寸**（透镜实际长到多大），布局尺寸仍是静止时的大小，
 * 放大后的子项居中放置。也就是「视觉变大、占位不变」，所以透镜鼓出胶囊边界时
 * 既不会被父布局挤压，也不会推走旁边的 tab。
 */
private fun Modifier.nativeControlLensGeometry(
    style: NativeControlLensStyle,
    horizontalProgress: () -> Float,
    verticalProgress: () -> Float = horizontalProgress,
): Modifier = layout { measurable, constraints ->
    val restingWidth = constraints.maxWidth
    val restingHeight = constraints.maxHeight
    val horizontal = horizontalProgress()
    val vertical = verticalProgress()
    val width = when (style) {
        NativeControlLensStyle.SwitchThumb -> restingWidth * (1f + (58f / 37f - 1f) * horizontal)
        NativeControlLensStyle.SliderThumb -> restingWidth * (1f + .55f * horizontal)
        NativeControlLensStyle.SegmentedIndicator -> restingWidth + 24.dp.toPx() * horizontal
        NativeControlLensStyle.TabBarIndicator -> restingWidth + LensGrowth.toPx() * horizontal
    }.roundToInt().coerceAtLeast(1)
    val height = when (style) {
        NativeControlLensStyle.SwitchThumb -> restingHeight * (1f + (38.333333f / 24f - 1f) * vertical)
        NativeControlLensStyle.SliderThumb -> restingHeight * (1f + .55f * vertical)
        NativeControlLensStyle.SegmentedIndicator,
        NativeControlLensStyle.TabBarIndicator -> restingHeight + LensGrowth.toPx() * vertical
    }.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(restingWidth, restingHeight) {
        placeable.place((restingWidth - width) / 2, (restingHeight - height) / 2)
    }
}

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 这次的改动：几何与光学都交回库
 *
 * 上一版自己写了 [BarOutline] 画两条轮廓 + S 形颈部，再配 `NavigationGlassUnionShape`
 * 的 compound 场。问题在于**轮廓和光学场是两套几何**：拖到快贴上时轮廓已经画了颈，
 * 场却还按 smooth-min 的阈值判断，于是"半融合"；把圆球拖到胶囊上时轮廓退化，
 * 只剩光学场按 bounds 求值，屏幕上就留下一个圆圈的边——也就是"原地一圈"。
 *
 * 现在轮廓直接用库的 `liquidContactShape(first, second, contactDistance,
 * includeDisconnected = true)`：
 *
 * - 常态：`contactCapsulePath` 走 `union()`，两条互不相交的闭合轮廓，各自完整；
 * - 靠近到 `contactDistance`（4dp）以内：长出一条按曲线曲率求切的颈部，腰宽连续变化；
 * - 圆球压到胶囊上（containment）：库判定「没有相对的边」，直接返回并集，所以
 *   叠在一起时轮廓就是真正的合并，不会留内部圆环。
 *
 * 光学场仍然用 `NavigationGlassUnionShape`，但 `outline` 换成同一个 `LiquidContactShape`，
 * 于是裁剪与场描述的是同一块几何。
 *
 * ## 按下透镜（图三）
 *
 * 逐条对齐 `GlasmioNavigationBar` 的指示器：
 *
 * - 尺寸 = **整格宽 × 54dp**（不是圆）；
 * - [nativeControlLensGeometry] 在抬起时宽高各 +16dp → 上下各鼓出 8dp；
 * - `drawPlainBackdrop` + `nativeControlLens(TabBarIndicator, liftProgress, liftedContent)`
 *   把图标文字折进透镜；
 * - 录制源里的图标额外放大 1.16 倍（`LensSourceScale`），按下时珠子里是放大的图标；
 * - 可见行里被选中的那一格**淡出**（和库的 `hideSelectedItem` 同一个意思），
 *   否则透镜下面还压着一份原图，会出现重影。
 *
 * 三格图标仍然自绘：库的 `SfSymbol` 只有 House / Search，没有日历和加号。
 */
@Composable
fun GlassBottomNavigation(
    selected: GlassTab,
    onSelected: (GlassTab) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    createLabel: String = "新建",
    /** 壁纸 + 列表内容的合成采样层。null 时退回宿主壁纸层（不透明，仅作兜底）。 */
    backdrop: GlassContentBackdrop? = null,
) {
    val tabs = GlassTab.entries
    val lastIndex = tabs.lastIndex.toFloat()
    val scope = rememberCoroutineScope()
    val darkInk = glassTextPrimary()
    val isDark = LocalGlassDark.current
    val lensBackdrop = backdrop?.backdrop ?: LocalGlassBackdrop.current
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)

    // 三格 Row 的每格宽度（px）：拖动回调里要按它把位移换算成 tab 位置。
    val cellPx = remember { mutableFloatStateOf(0f) }
    val onSelectedState = rememberUpdatedState(onSelected)
    val selectedIndexState = rememberUpdatedState(selectedIndex)
    val tabsState = rememberUpdatedState(tabs)

    val drag = remember {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..lastIndex,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            lensStyle = NativeControlLensStyle.TabBarIndicator,
            onDragStarted = { position ->
                val step = cellPx.floatValue.coerceAtLeast(1f)
                val index = (position.x / step).toInt().coerceIn(0, tabs.size - 1)
                updateValue(index.toFloat())
            },
            onDragStopped = {
                val target = targetValue.roundToInt().coerceIn(0, tabs.size - 1)
                if (target != selectedIndexState.value) {
                    onSelectedState.value(tabsState.value[target])
                }
                animateToValue(target.toFloat())
            },
            onDragCancelled = {
                updateValue(selectedIndexState.value.toFloat())
            },
            onDrag = { _, amount ->
                val step = cellPx.floatValue.coerceAtLeast(1f)
                updateValue((targetValue + amount.x / step).coerceIn(0f, lastIndex))
            },
        )
    }
    LaunchedEffect(selectedIndex, drag) {
        if (drag.targetValue != selectedIndex.toFloat()) {
            drag.animateToValue(selectedIndex.toFloat(), animatePress = false)
        }
    }

    // 栏内图标文字的录制层：nativeControlLens 把这一层折进抬起的透镜。
    // 与库一样用 alpha(0f) 的副本，只有 backdrop 的 capture graphicsLayer 里
    // 才真正 drawContent，屏幕上不会多画一份。
    val labelsBackdrop = rememberLayerBackdrop()

    // 圆球拖动位移（px）。用 Animatable：松手能带弹簧回弹到 0。
    val ballDrag = remember { Animatable(0f) }
    val ballInteraction = remember { MutableInteractionSource() }
    val ballHeld by ballInteraction.collectIsPressedAsState()
    // 一旦开始拖动，clickable 的 press 会被手势取消；单独记一个 dragging，
    // 否则按住往左拖时圆球的高光反而会缩回去。
    var ballDragging by remember { mutableStateOf(false) }
    val ballPress by animateFloatAsState(
        targetValue = if (ballHeld || ballDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "createBallPress",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SlotHeight),
    ) {
        val density = LocalDensity.current
        val ballPx = with(density) { BallDiameter.toPx() }
        val barPx = with(density) { BarHeight.toPx() }
        val slotPx = with(density) { SlotHeight.toPx() }
        val lensPx = with(density) { LensHeight.toPx() }
        val gapPx = with(density) { BarGap.toPx() }
        val contactPx = with(density) { ContactDistance.toPx() }
        val smoothnessPx = with(density) { UnionSmoothness.toPx() }
        val insetPx = with(density) { IndicatorInsetX.toPx() }
        val leftLimitPx = with(density) { BallDragLeft.toPx() }
        val rightLimitPx = with(density) { BallDragRight.toPx() }
        val growthPx = with(density) { BallPressGrowth.toPx() }

        val pillWidth = maxWidth - BallDiameter - BarGap
        val pillPx = with(density) { pillWidth.toPx() }
        val cell = pillPx / tabs.size
        cellPx.floatValue = cell
        val indicatorPx = (cell - 2f * insetPx).coerceAtLeast(with(density) { 36.dp.toPx() })
        val indicatorWidth = with(density) { indicatorPx.toDp() }
        val indicatorInsetPx = (cell - indicatorPx) / 2f
        val lensTopPx = (slotPx - lensPx) / 2f

        // 量化成 4 档：避免按压动画每帧都让材质重新解析一遍配方。
        val growth = growthPx * ((ballPress * 3).roundToInt() / 3f)
        val grownBallPx = ballPx + growth
        // 圆球左边（px）：常态 = 胶囊右端 + 12dp 缝；按下长大会左右各涨一半。
        val ballLeft = pillPx + gapPx + ballDrag.value - growth / 2f
        val ballScale = grownBallPx / ballPx

        // 两个 capsule primitive 的几何（px），单位是底栏槽的坐标空间。
        val capsuleRect = Rect(0f, (slotPx - barPx) / 2f, pillPx, (slotPx + barPx) / 2f)
        val ballRect = Rect(
            ballLeft,
            (slotPx - grownBallPx) / 2f,
            ballLeft + grownBallPx,
            (slotPx + grownBallPx) / 2f,
        )
        val capsuleFirst = LiquidContactCapsule(Size(pillPx, barPx), capsuleRect)
        val capsuleSecond = LiquidContactCapsule(Size(grownBallPx, grownBallPx), ballRect)

        // 轮廓：**库自己的**液态接触形状。两片分开时是两条独立闭合轮廓，
        // 靠近时由同一个函数长出颈部，压在胶囊上时退化成并集——不再是自绘的 S 曲线。
        val contactShape: Shape = remember(capsuleFirst, capsuleSecond, contactPx) {
            ResilientContactShape(
                primary = liquidContactShape(
                    first = capsuleFirst,
                    second = capsuleSecond,
                    contactDistance = contactPx,
                    // 常态（未接触）也要返回形状：两块玻璃各自完整，而不是整条栏消失。
                    includeDisconnected = true,
                ) ?: Capsule(),
                fallback = PlainCapsulesShape(listOf(capsuleFirst, capsuleSecond)),
            )
        }

        val barShape: Shape = remember(contactShape, capsuleFirst, capsuleSecond, smoothnessPx, barPx) {
            NavigationGlassUnionShape(
                outline = contactShape,
                capsules = listOf(capsuleFirst, capsuleSecond),
                smoothness = smoothnessPx,
                // 光学按主控件（胶囊）标定，不把拆出去的圆球算进尺寸。
                minimumDimension = barPx,
            )
        }

        // 1) 玻璃：整条栏仍然只有一块 GlassSurface。
        //    轮廓与光学场共用同一组 capsule 与同一个 smoothness，所以「两片独立 /
        //    长颈融合 / 叠加合并」三种状态在裁剪和折射上是同一个答案。
        GlassPanel(
            modifier = Modifier.fillMaxSize(),
            shape = barShape,
            frostScale = BarFrostScale,
            backdrop = backdrop,
        ) {}

        // 2) 栏内图标文字的录制副本（不可见），供第 5 步的原生透镜折射。
        //    图标额外放大 LensSourceScale 倍：透镜里看到的是被镜片顶起来的图标。
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight)
                .alpha(0f)
                .layerBackdrop(labelsBackdrop)
                .drawWithContent {
                    if (drawContext.graphicsLayer === labelsBackdrop.graphicsLayer) drawContent()
                },
        ) {
            tabs.forEach { tab ->
                TabCell(
                    tab = tab,
                    selected = tab == selected,
                    modifier = Modifier.weight(1f),
                    sourceScale = { 1f + LensSourceScale * drag.pressProgress },
                )
            }
        }

        // 3) 常态暗色选中范围：内缩一圈的圆角块，随 value 滑动，抬起时淡出
        //    让位给透镜。用和胶囊同一个 ContinuousRoundedRectangle（连续圆角），
        //    23dp / 48dp 的比例与胶囊的 31dp / 62dp 一致。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(indicatorWidth)
                .height(IndicatorHeight)
                .graphicsLayer {
                    translationX = cellPx.floatValue * drag.value + indicatorInsetPx
                    alpha = 1f - drag.pressProgress
                }
                .background(
                    color = darkInk.copy(alpha = 0.10f),
                    shape = remember { ContinuousRoundedRectangle(IndicatorCorner) },
                ),
        )

        // 4) 三格：拖动手势由库的 DampedDragAnimation 统一接管（含点击定位）。
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight)
                .then(drag.modifier),
        ) {
            tabs.forEach { tab ->
                val cellSelected = tab == selected
                TabCell(
                    tab = tab,
                    selected = cellSelected,
                    modifier = Modifier.weight(1f),
                    // 被选中的那格在按下时淡出：透镜里已经有一份放大后的图标，
                    // 底下再压一份原图就成了重影（库的做法是 hideSelectedItem）。
                    contentAlpha = {
                        if (cellSelected) 1f - drag.pressProgress else 1f
                    },
                )
            }
        }

        // 5) 抬起的透镜：整格宽的胶囊 + 原生控制透镜。
        //
        //    占位始终是 `cell × 54dp`，抬起时由 nativeControlLensGeometry 把**画出来的**
        //    尺寸各加 16dp（上下各凸 8dp），所以镜片会鼓出胶囊，而不是贴在栏上的圆盘。
        //    库的写法是「外层管位置、内层管测量与绘制」，这里照抄这个结构：
        //    外层只负责跟手位移，内层负责变大与透镜效果。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        (cell * drag.value).roundToInt(),
                        lensTopPx.roundToInt(),
                    )
                }
                .width(with(density) { cell.toDp() })
                .height(LensHeight),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nativeControlLensGeometry(
                        style = NativeControlLensStyle.TabBarIndicator,
                        horizontalProgress = { drag.pressProgress },
                    )
                    .drawPlainBackdrop(
                        backdrop = lensBackdrop,
                        shape = { Capsule() },
                        effects = {
                            nativeControlLens(
                                style = NativeControlLensStyle.TabBarIndicator,
                                liftProgress = drag.pressProgress,
                                isDark = isDark,
                                liftedContent = labelsBackdrop,
                            )
                        },
                    ),
            )
        }

        // 6) 单独悬浮的「＋」：按住拖动改变圆球位置 → 液态吸附；点击新建。
        //    和圆球玻璃用同一个 scale / offset，字形才不会在按压放大时错位。
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(BallDiameter)
                .offset {
                    IntOffset(
                        (ballDrag.value - growth / 2f).roundToInt(),
                        0,
                    )
                }
                .graphicsLayer {
                    scaleX = ballScale
                    scaleY = ballScale
                }
                .drawBehind {
                    // 按压高光用径向渐变：实心圆会在玻璃球上留一圈硬边，
                    // 看起来像又叠了一块白盘（库的 PressHighlightRenderer 也是柔和的）。
                    if (ballPress > 0.001f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.30f * ballPress),
                                    Color.White.copy(alpha = 0f),
                                ),
                                center = center,
                                radius = size.minDimension / 2f,
                            ),
                        )
                    }
                }
                .clickable(
                    interactionSource = ballInteraction,
                    indication = null,
                    onClick = onCreate,
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { ballDragging = true },
                        onDragEnd = {
                            ballDragging = false
                            scope.launch { ballDrag.animateTo(0f, spring(0.8f, 420f)) }
                        },
                        onDragCancel = {
                            ballDragging = false
                            scope.launch { ballDrag.animateTo(0f, spring(0.8f, 420f)) }
                        },
                        onDrag = { change, amount ->
                            val next = (ballDrag.value + amount.x)
                                .coerceIn(-leftLimitPx, rightLimitPx)
                            scope.launch { ballDrag.snapTo(next) }
                            change.consume()
                        },
                    )
                }
                .semantics { contentDescription = createLabel },
            contentAlignment = Alignment.Center,
        ) {
            PlusGlyph(darkInk, glyphSize = 26.dp)
        }
    }
}

/**
 * 一格：图标 + 小字。点击与拖动都交给外层 [DampedDragAnimation]，这里只负责画。
 *
 * @param contentAlpha 画到屏幕上的不透明度（按下时选中的那格淡出，避免和透镜重影）。
 * @param sourceScale 这一个格子的放大倍数，只作用于录制给透镜的那份副本。
 */
@Composable
private fun RowScope.TabCell(
    tab: GlassTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    contentAlpha: () -> Float = { 1f },
    sourceScale: () -> Float = { 1f },
) {
    // 常态选中除了那块暗色范围，还靠颜色区分；未选中用主文字色而不是次级灰，
    // 否则整条栏看起来是「三个禁用按钮」。
    val ink = if (selected) glassAccent() else glassTextPrimary()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                alpha = contentAlpha()
                val scale = sourceScale()
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = tab.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabGlyph(tab = tab, color = ink, glyphSize = 23.dp, selected = selected)
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
            ),
        )
    }
}

/** 三格的图标：都是自绘线条，不依赖字体字形。 */
@Composable
private fun TabGlyph(tab: GlassTab, color: Color, glyphSize: Dp, selected: Boolean) {
    Canvas(Modifier.size(glyphSize)) {
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
private fun PlusGlyph(color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 2.1.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.1f), Offset(c, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.1f, c), Offset(size.width * 0.9f, c), stroke, StrokeCap.Round)
    }
}
