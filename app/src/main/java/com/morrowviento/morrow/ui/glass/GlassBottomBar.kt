package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

/** 胶囊高度。示例栏是扁的，胶囊略小于右侧圆球直径。 */
private val BarHeight = 52.dp

/** 右侧「＋」圆球直径：常态单独悬浮的一整块玻璃。 */
private val BallDiameter = 56.dp

/** 点击抬起的玻璃珠直径：比胶囊上下各凸 4dp。 */
private val LensDiameter = 60.dp

/** 底栏槽高：三者取最大，珠子上下都不被裁。 */
private val SlotHeight = 60.dp

/**
 * 常态胶囊右端到圆球左端的缝。
 *
 * 12dp 是能明确看出「两片独立玻璃」的距离；4dp 及以内才进入液态接触。
 * 之前设 8dp 且把接触距离也设成 8dp，结果是库的 `liquidContactShape` 一直接触
 * 分支、整条栏渲染成一根长胶囊，＋ 看着就像画在栏末端的一个字符。
 */
private val BarGap = 12.dp

/**
 * 间隙小于这个值才长出收腰颈部。按住圆球光长大还不够（只少 3dp），
 * 必须真的往左拖 5dp 以上才吸附——和示例「按住拖动」的手感一致。
 */
private val ContactGap = 4.dp

/** 圆球最多能往左 / 右拖多远（夹住，避免两片深度重叠导致路径自交）。 */
private val BallDragLeft = 14.dp
private val BallDragRight = 8.dp

/** 按住时圆球长大的量。 */
private val BallPressGrowth = 6.dp

/**
 * 常态暗色选中范围：**内缩一圈的圆角方块**（示例图 1）。
 *
 * 高度 38dp 对 52dp 栏高，上下各留 7dp；宽度是一格减去左右各 10dp。
 * 之前用 60dp 的圆，比栏还高，看着就是「跟栏平齐的一整格色块」，
 * 而且和点击抬起的玻璃珠同形，常态/按下分不出层次。
 */
private val IndicatorHeight = 38.dp
private val IndicatorInsetX = 10.dp
private val IndicatorCorner = 14.dp

/** 融合态的腰部半高（从 6dp 的细颈长到 20dp）。 */
private val WaistMinHalf = 6.dp
private val WaistMaxHalf = 20.dp

/**
 * 腰部从细颈长到满粗所跨越的间隙变化量。
 *
 * 间隙从 [ContactGap] (4dp) 一路减到拖到底的 -5dp（12dp 缝 - 14dp 左拖 - 3dp 长大
 * 的一半），正好 9dp；设成 9dp 让 t 能走到 1，拖动全程腰部都在连续变粗。
 */
private val JoinSpan = 9.dp

/** 胶囊顶边从右端往左多少开始收成颈部。 */
private val NeckInset = 22.dp

/** 底栏厚度缩放：52dp 高的横条用满 16dp 高斯会把背后抹成一块实色板。 */
private const val BarFrostScale = 0.55f

private const val DEG = Math.PI.toFloat() / 180f

/**
 * 底栏轮廓：**一块玻璃、两条可分离的闭合轮廓**。
 *
 * 不直接用库的 `liquidContactShape`：它内部走 `PathIterator`（native 库），
 * 而且在真机上把常态两片合成了一整条长胶囊（＋ 因此没有「单独悬浮」）。
 * 这里用纯 Compose Path 自己解析：
 *
 * - `gap >= contactPx`：胶囊圆角矩形 + 圆球椭圆，两条互不重叠的闭合轮廓。
 *   Compose 的 clip / 玻璃面层按 NonZero 填充，得到的就是两块独立玻璃。
 * - `gap < contactPx`：改成一条连续轮廓——胶囊顶边经两段三次贝塞尔收成 S 形细腰，
 *   接圆球 225° 点、外弧扫 270°、下缘镜像、左端 180° 封口。腰半高随接近程度
 *   从 6dp（刚粘上的细丝）长到 20dp，所以吸附是连续长出来的而不是突然跳变。
 *
 * 整条栏始终只有一块 `GlassSurface`：共用一条轮廓才有连续的边缘高光，
 * 也不会出现两层 Clear 互相糊掉透明度、或在圆球左缘留一道假接缝。
 */
private class BarShape(
    private val capsule: Rect,
    private val ball: Rect,
    private val contactPx: Float,
    private val joinSpanPx: Float,
    private val neckInsetPx: Float,
    private val waistMinPx: Float,
    private val waistMaxPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val gap = ball.left - capsule.right
        if (gap >= contactPx) {
            path.addRoundRect(
                RoundRect(capsule, CornerRadius(capsule.height / 2f, capsule.height / 2f)),
            )
            path.addOval(ball)
            return Outline.Generic(path)
        }

        val t = ((contactPx - gap) / joinSpanPx).coerceIn(0f, 1f)
        val ease = t * t * (3f - 2f * t)
        val waistHalf = waistMinPx + (waistMaxPx - waistMinPx) * ease
        val cy = (capsule.center.y + ball.center.y) / 2f
        val r1 = capsule.height / 2f
        val r2 = ball.height / 2f
        val ballCx = ball.center.x
        val ballCy = ball.center.y

        val neckStart = capsule.right - neckInsetPx
        val waistX = (capsule.right + ball.left) / 2f
        val a = (waistX - neckStart) * 0.55f
        val upperJoin = Offset(
            ballCx + r2 * cos(225f * DEG),
            ballCy + r2 * sin(225f * DEG),
        )
        val lowerJoin = Offset(
            ballCx + r2 * cos(135f * DEG),
            ballCy + r2 * sin(135f * DEG),
        )
        val b = ((upperJoin.x - waistX) * 0.55f).coerceAtLeast(2f)
        val k = b * 0.75f

        path.moveTo(r1, capsule.top)
        path.lineTo(neckStart, capsule.top)
        path.cubicTo(neckStart + a, capsule.top, waistX - a, cy - waistHalf, waistX, cy - waistHalf)
        path.cubicTo(waistX + b, cy - waistHalf, upperJoin.x - k, upperJoin.y + k, upperJoin.x, upperJoin.y)
        path.arcToRad(Rect(ball.left, ball.top, ball.right, ball.bottom), 225f * DEG, 270f * DEG, false)
        path.cubicTo(lowerJoin.x - k, lowerJoin.y - k, waistX + b, cy + waistHalf, waistX, cy + waistHalf)
        path.cubicTo(waistX - a, cy + waistHalf, neckStart + a, capsule.bottom, neckStart, capsule.bottom)
        path.lineTo(r1, capsule.bottom)
        path.arcToRad(Rect(0f, capsule.top, r1 * 2f, capsule.bottom), 90f * DEG, 180f * DEG, false)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 从 Glasmio 源码移植的两套机制
 *
 * 1. **可拖动的选中指示器**（[DampedDragAnimation]）：库 `GlasmioNavigationBar` /
 *    `GlasmioTabBar` 用的是同一个类。`value` 是浮点 tab 位置（拖动中连续跟手），
 *    `pressProgress` 管抬起，`scaleX/scaleY` 是 UIKit 的 78/56 放大，三组弹簧管
 *    定位 / 吸附 / 回弹。`drag.modifier` 挂在三格 Row 上，按下定位、松手吸附到
 *    最近一格并提交 `onSelected`；外部切页用 `animateToValue(animatePress=false)`。
 * 2. **原生控制透镜**（`drawPlainBackdrop` + `nativeControlLens`）：这是让**栏内图标
 *    文字透进玻璃珠**的关键。库 `GlasmioTabBar` 会把一份不可见的标签 Row 录进
 *    `labelsBackdrop`，再把它作为 `liftedContent` 交给 lens；只采壁纸/页面内容的
 *    `GlassCirclePanel` 里根本没有图标，所以之前的珠子是一块空白的浅蓝圆盘。
 *    这里照同样的方式接：可见三格 + 一份 `alpha(0f).layerBackdrop(...)` 的副本。
 *
 * 常态选中是「暗色内缩圆角方块」（库在不用 backdrop 时给的是 `Black 10%`），
 * 抬起时淡出、由原生 lens 接手并把图标文字折进去。
 *
 * 右侧 ＋ 常态是独立悬浮的圆球，按住往左拖动时 [BarShape] 会长出 S 形收腰颈部
 * 与胶囊吸附；松手带弹簧弹回。三格的图标自己画（库的 `SfSymbol` 没有日历和加号）。
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
    val lifted by remember { derivedStateOf { drag.pressProgress > 0.01f } }

    // 栏内图标文字的录制层：nativeControlLens 把它折进抬起的玻璃珠。
    // 和库 GlasmioTabBar 一样，用 alpha(0f) 的副本，只有 backdrop 的 capture
    // graphicsLayer 里才真正 drawContent，屏幕上不会多画一份。
    val labelsBackdrop = rememberLayerBackdrop()

    // 圆球拖动位移（px）。用 Animatable：松手能带弹簧回弹到 0。
    val ballDrag = remember { Animatable(0f) }
    val ballInteraction = remember { MutableInteractionSource() }
    val ballHeld by ballInteraction.collectIsPressedAsState()
    // 一旦开始拖动，clickable 的 press 会被手势取消；单独记一个 dragging，
    // 否则按住往左拖时圆球的高光和放大反而会缩回去。
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
        val lensPx = with(density) { LensDiameter.toPx() }
        val gapPx = with(density) { BarGap.toPx() }
        val contactPx = with(density) { ContactGap.toPx() }
        val joinSpanPx = with(density) { JoinSpan.toPx() }
        val neckInsetPx = with(density) { NeckInset.toPx() }
        val waistMinPx = with(density) { WaistMinHalf.toPx() }
        val waistMaxPx = with(density) { WaistMaxHalf.toPx() }
        val insetPx = with(density) { IndicatorInsetX.toPx() }
        val leftLimitPx = with(density) { BallDragLeft.toPx() }
        val rightLimitPx = with(density) { BallDragRight.toPx() }
        val growthPx = with(density) { BallPressGrowth.toPx() }

        val pillWidth = maxWidth - BallDiameter - BarGap
        val pillPx = with(density) { pillWidth.toPx() }
        val cell = pillPx / tabs.size
        cellPx.floatValue = cell
        val lensInsetPx = ((cell - lensPx) / 2f).coerceAtLeast(0f)
        val indicatorPx = (cell - 2f * insetPx).coerceAtLeast(with(density) { 36.dp.toPx() })
        val indicatorWidth = with(density) { indicatorPx.toDp() }
        val indicatorInsetPx = (cell - indicatorPx) / 2f

        // 量化成 5 档：避免按压动画每帧重建整条轮廓的 Shape 对象。
        val growth = growthPx * ((ballPress * 4).roundToInt() / 4f)
        val grownBallPx = ballPx + growth
        val ballLeft = pillPx + gapPx + ballDrag.value - growth / 2f
        val capsule = Rect(0f, (slotPx - barPx) / 2f, pillPx, (slotPx + barPx) / 2f)
        val ball = Rect(
            ballLeft,
            (slotPx - grownBallPx) / 2f,
            ballLeft + grownBallPx,
            (slotPx + grownBallPx) / 2f,
        )
        val barShape: Shape = BarShape(
            capsule = capsule,
            ball = ball,
            contactPx = contactPx,
            joinSpanPx = joinSpanPx,
            neckInsetPx = neckInsetPx,
            waistMinPx = waistMinPx,
            waistMaxPx = waistMaxPx,
        )

        // 1) 整条底栏只有一块玻璃：常态两片各自完整，靠近时自动长颈部
        GlassPanel(
            modifier = Modifier.fillMaxSize(),
            shape = barShape,
            frostScale = BarFrostScale,
            backdrop = backdrop,
        ) {}

        // 2) 栏内图标文字的录制副本（不可见），供第 5 步的原生透镜折射
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
                TabCell(tab = tab, selected = tab == selected, modifier = Modifier.weight(1f))
            }
        }

        // 3) 常态暗色选中范围：内缩一圈的圆角方块，随 value 滑动
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(indicatorWidth)
                .height(IndicatorHeight)
                .graphicsLayer {
                    translationX = cellPx.floatValue * drag.value + indicatorInsetPx
                    scaleX = drag.scaleX
                    scaleY = drag.scaleY
                    alpha = 1f - drag.pressProgress
                }
                .drawBehind {
                    drawRoundRect(
                        color = darkInk.copy(alpha = 0.10f),
                        cornerRadius = CornerRadius(
                            IndicatorCorner.toPx(),
                            IndicatorCorner.toPx(),
                        ),
                    )
                },
        )

        // 4) 三格：拖动手势由库的 DampedDragAnimation 统一接管（含点击定位）
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight)
                .then(drag.modifier),
        ) {
            tabs.forEach { tab ->
                TabCell(
                    tab = tab,
                    selected = tab == selected,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 5) 抬起的玻璃珠：原生控制透镜。liftedContent = 图标文字录制层，
        //    所以珠子里面能看见被折弯放大的 house / 日历 / 搜索，而不是一块空白盘。
        if (lifted) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(LensDiameter)
                    .graphicsLayer {
                        translationX = cellPx.floatValue * drag.value + lensInsetPx
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        alpha = drag.pressProgress
                    }
                    .drawPlainBackdrop(
                        backdrop = lensBackdrop,
                        shape = { CircleShape },
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

        // 6) 单独悬浮的「＋」：按住拖动改变圆球位置 → 液态吸附；点击新建
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
                .drawBehind {
                    if (ballPress > 0.001f) {
                        drawCircle(Color.White.copy(alpha = 0.34f * ballPress))
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
            PlusGlyph(darkInk, glyphSize = 23.dp)
        }
    }
}

/** 一格：图标 + 小字。点击与拖动都交给外层 [DampedDragAnimation]，这里只负责画。 */
@Composable
private fun RowScope.TabCell(
    tab: GlassTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    // 常态选中除了那块暗色范围，还靠颜色区分；未选中用主文字色而不是次级灰，
    // 否则整条栏看起来是「三个禁用按钮」。
    val ink = if (selected) glassAccent() else glassTextPrimary()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = tab.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabGlyph(tab = tab, color = ink, glyphSize = 21.dp, selected = selected)
        Spacer(Modifier.height(3.dp))
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
        val stroke = (if (selected) 2.3f else 2.0f).dp.toPx()
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
