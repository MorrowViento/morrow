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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
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

/** 胶囊高度。示例栏是扁的，胶囊略小于右侧圆球直径。 */
private val BarHeight = 52.dp

/** 右侧「＋」圆球直径：常态单独悬浮的一整块玻璃。 */
private val BallDiameter = 56.dp

/** 点击抬起的玻璃珠直径：比胶囊上下各凸 4dp。 */
private val LensDiameter = 60.dp

/** 底栏槽高：三者取最大，珠子上下都不被裁。 */
private val SlotHeight = 60.dp

/**
 * 常态胶囊右端到圆球左端的缝（Glasmio 示例里 Separate Navigation Item = ON 的间距）。
 *
 * 关键：**两片必须是两个独立的玻璃控件**。上一版把胶囊和圆球塞进同一个
 * `GlassSurface` 的两条轮廓里，库的原生材质（`NativeGlassCompletion`）取的是
 * 整个 shape 的 bounds 而不是逐条轮廓的 field，于是把两片之间的缝也画成玻璃，
 * 真机上又变回一根长胶囊，＋ 看着仍像嵌在栏末端。现在胶囊一块、圆球一块，
 * 各自有独立的边缘高光与投影，中间这条 12dp 的缝是真正的底色。
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
 *
 * 圆角 18dp ≈ 38dp 高的一半：和 26dp 半径的胶囊是同一族圆角，
 * 内缩一圈后看起来像「把胶囊等比例缩小了一号」，而不是一个方盒子贴在胶囊里。
 * 14dp 时四个角明显比栏体方，和胶囊打架。
 */
private val IndicatorHeight = 38.dp
private val IndicatorInsetX = 10.dp
private val IndicatorCorner = 18.dp

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

/** 液态颈部两端伸进两片玻璃的量：被玻璃盖住，屏幕上不会多出一条接缝。 */
private val NeckOverlap = 6.dp

/** 底栏厚度缩放：52dp 高的横条用满 16dp 高斯会把背后抹成一块实色板。 */
private const val BarFrostScale = 0.55f

/**
 * 两片独立玻璃之间的「液态颈部」（只在按住 ＋ 往左拖、两片快贴上时画）。
 *
 * 这是 Glasmio 示例里 Separate Navigation Item 的第二个特征：常态两片彻底分开，
 * 靠近时中间长出一截会变粗的 S 形连接。这里画在两块玻璃**下面**，两端分别伸进
 * 胶囊右缘和圆球左缘，被各自的玻璃盖住，屏幕上只露出缝里的一小截，
 * 因此不会给两片独立玻璃留下第三条接缝，也不需要再造一块 `GlassSurface`
 * （第三块 Clear 叠在另外两片上会把透明度糊掉）。
 *
 * 腰部半高从 6dp 长到 20dp，所以吸附是连续长出来的，不是突然跳变。
 */
private fun DrawScope.drawLiquidNeck(
    startX: Float,
    endX: Float,
    centerY: Float,
    endHalf: Float,
    waistHalf: Float,
    join: Float,
) {
    val span = (endX - startX).coerceAtLeast(1f)
    val mid = (startX + endX) / 2f
    val c = span * 0.42f
    val path = Path().apply {
        moveTo(startX, centerY - endHalf)
        cubicTo(startX + c, centerY - endHalf, mid - c, centerY - waistHalf, mid, centerY - waistHalf)
        cubicTo(mid + c, centerY - waistHalf, endX - c, centerY - endHalf, endX, centerY - endHalf)
        lineTo(endX, centerY + endHalf)
        cubicTo(endX - c, centerY + endHalf, mid + c, centerY + waistHalf, mid, centerY + waistHalf)
        cubicTo(mid - c, centerY + waistHalf, startX + c, centerY + endHalf, startX, centerY + endHalf)
        close()
    }
    drawPath(path, Color.White.copy(alpha = 0.16f * join))
    drawPath(
        path,
        Color.White.copy(alpha = 0.34f * join),
        style = Stroke(width = 1.dp.toPx()),
    )
}

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 从 Glasmio 源码移植的三套机制
 *
 * 1. **Separate Navigation Item**：示例设置里打开这个开关后，最右一项会从胶囊里
 *    拆出来变成一颗独立圆按钮，胶囊相应缩短、中间留出一条能看见底色的缝。
 *    这里对应的是「三格短胶囊 + 独立 ＋ 圆球」**两个独立的 `GlassSurface`**——
 *    不能像上一版那样塞进同一块玻璃的两条轮廓：库的原生材质按 shape 的 bounds
 *    求光学场，会把缝也填成玻璃，＋ 看着仍像画在长胶囊末端。
 * 2. **可拖动的选中指示器**（[DampedDragAnimation]）：库 `GlasmioNavigationBar` /
 *    `GlasmioTabBar` 用的是同一个类。`value` 是浮点 tab 位置（拖动中连续跟手），
 *    `pressProgress` 管抬起，`scaleX/scaleY` 是 UIKit 的 78/56 放大，三组弹簧管
 *    定位 / 吸附 / 回弹。`drag.modifier` 挂在三格 Row 上，按下定位、松手吸附到
 *    最近一格并提交 `onSelected`；外部切页用 `animateToValue(animatePress=false)`。
 * 3. **原生控制透镜**（`drawPlainBackdrop` + `nativeControlLens`）：这是让**栏内图标
 *    文字透进玻璃珠**的关键。库 `GlasmioTabBar` 会把一份不可见的标签 Row 录进
 *    `labelsBackdrop`，再把它作为 `liftedContent` 交给 lens；只采壁纸/页面内容的
 *    `GlassCirclePanel` 里根本没有图标，所以之前的珠子是一块空白的浅蓝圆盘。
 *    这里照同样的方式接：可见三格 + 一份 `alpha(0f).layerBackdrop(...)` 的副本。
 *
 * 常态选中是「暗色内缩圆角方块」（库在不用 backdrop 时给的是 `Black 10%`），
 * 抬起时淡出、由原生 lens 接手并把图标文字折进去。
 *
 * 按住 ＋ 往左拖，两片之间会由 [drawLiquidNeck] 长出 S 形收腰颈部（示例的液态
 * 吸附），松手带弹簧弹回分离态。三格图标自己画（库的 `SfSymbol` 没有日历和加号）。
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
        val neckOverlapPx = with(density) { NeckOverlap.toPx() }
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

        // 量化成 5 档：避免按压动画每帧都让两块玻璃重新求材质配方。
        val growth = growthPx * ((ballPress * 4).roundToInt() / 4f)
        val grownBallPx = ballPx + growth
        // 圆球左边（px）：常态 = 胶囊右端 + 12dp 缝；按下长大会左右各涨一半。
        val ballLeft = pillPx + gapPx + ballDrag.value - growth / 2f
        val gap = ballLeft - pillPx
        // 只有真的往左拖到缝 < 4dp，两片才开始长出液态颈部；0 → 1 连续变粗。
        val join = ((contactPx - gap) / joinSpanPx).coerceIn(0f, 1f)
        val ease = join * join * (3f - 2f * join)
        val waistHalf = waistMinPx + (waistMaxPx - waistMinPx) * ease
        val ballScale = grownBallPx / ballPx

        // 1) 拖动吸附：两片玻璃之间那截 S 形收腰（画在玻璃下面，两端被盖住）
        if (join > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawLiquidNeck(
                            startX = pillPx - neckOverlapPx,
                            endX = ballLeft + grownBallPx * 0.35f,
                            centerY = slotPx / 2f,
                            endHalf = minOf(barPx, grownBallPx) * 0.38f,
                            waistHalf = waistHalf,
                            join = join,
                        )
                    },
            )
        }

        // 2) 短胶囊：Separate Navigation Item 打开后的「主栏」，独立一整块玻璃。
        //    宽度不含 ＋，右端是完整的半圆，和圆球之间露出 12dp 的底色。
        GlassPanel(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight),
            cornerRadius = BarHeight / 2f,
            frostScale = BarFrostScale,
            backdrop = backdrop,
        ) {}

        // 3) 独立圆球：第二块玻璃，和胶囊共用采样层，但有自己的边缘高光与投影。
        //    这是「＋ 真的独立出来」的关键——不是同一个 Shape 里的第二条轮廓。
        GlassCirclePanel(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(BallDiameter)
                .offset { IntOffset((ballDrag.value - growth / 2f).roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = ballScale
                    scaleY = ballScale
                },
            frostScale = BarFrostScale,
            backdrop = backdrop,
        ) {}

        // 4) 栏内图标文字的录制副本（不可见），供第 7 步的原生透镜折射
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

        // 5) 常态暗色选中范围：内缩一圈的圆角块，随 value 滑动。
        //    用和胶囊同一个 ContinuousRoundedRectangle（连续圆角），18dp / 38dp 的比例
        //    与胶囊的 26dp / 52dp 一致，看上去就是「把底栏等比例缩小了一号」；
        //    普通 RoundedCorner 的角会明显更方，和胶囊打架。
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
                .background(
                    color = darkInk.copy(alpha = 0.10f),
                    shape = remember { ContinuousRoundedRectangle(IndicatorCorner) },
                ),
        )

        // 6) 三格：拖动手势由库的 DampedDragAnimation 统一接管（含点击定位）
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

        // 7) 抬起的玻璃珠：原生控制透镜。liftedContent = 图标文字录制层，
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

        // 8) 单独悬浮的「＋」：按住拖动改变圆球位置 → 液态吸附；点击新建。
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
