package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.NavigationGlassUnionShape
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import kotlinx.coroutines.Job
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

/**
 * 右侧独立圆球直径。
 *
 * 比胶囊（62dp）**小 4dp**：示例里那颗独立按钮是一颗「挂在旁边的珠子」，
 * 不是和栏等高的墩子。等大时两颗圆角会互相争主次，看着像栏被截断了一节。
 */
private val BallDiameter = 58.dp

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
 * 并集融合半径：smooth-min 的 k 的**最大值**。
 *
 * **轮廓和光学场必须共用同一个 k**，库的 `BottomControlContact` 就是这么做的
 * （`nativeSmoothness` 同时交给 `NavigationNativeUnion` 与 `NavigationGlassUnionShape`）。
 * 之前轮廓走 `liquidContactShape`、光学场走 union shape，两套几何在贴合段各画各的，
 * 屏幕上就是「融合处一条分明的界线」，甚至会把胶囊自己的右端圆弧露在圆球里。
 *
 * 库默认 6dp，这里用到 10dp：贴合段会展开成一段圆润的腰，而不是两块玻璃硬碰。
 *
 * **真正让融合「连续」的是把它乘上实时进度**（[mergeProgress]）：库的
 * `BottomControlContact.createSurface()` 也是 `nativeSmoothness * mergedProgress`。
 * 常态 k = 0 → 并集退化成两条独立轮廓，＋ 仍是自己一颗玻璃；靠近时 k 平滑长大，
 * 颈部连续变粗。之前是「间隙小于阈值才整体切换渲染树」，切换那一帧要销毁两块玻璃、
 * 重建一块并集玻璃，真机上就是那一下卡顿。
 */
private val UnionSmoothness = 10.dp

/**
 * 圆球最多能往左 / 右拖多远。
 *
 * 往左的上限 = 常态缝 + [BallOverlap]：球心再往左就整个压在胶囊上了。
 * 贴住之后继续拖没有意义，几何也停在原地——这样融合段的轮廓只算一次，
 * 不会每帧重建 SDF 折线（那是拖动时掉帧的主因）。
 */
private val BallOverlap = 3.dp
private val BallDragRight = 8.dp

/**
 * 按住时圆球长大的量。
 *
 * 只做「按下去有一点涨」的手感，**不靠它凑融合**——融合由 k 连续生长，
 * 靠长大去制造接触会在松手时留下跳变。
 */
private val BallPressGrowth = 3.dp

/**
 * 常态暗色选中范围：内缩一圈的胶囊。
 *
 * 高度 46dp 对 62dp 栏高，上下各留 8dp；圆角直接取高度的一半（23dp），
 * 也就是一条**等比例的短胶囊**——和 31dp 半径的外框是同族曲线。
 *
 * 上一版用 `ContinuousRoundedRectangle(23dp)` 画 78×48 的块：连续圆角的平滑段
 * 在「圆角 ≈ 半高」时会向外鼓，看起来是一个被压扁的椭圆，用户反馈的「歪了」就是它。
 * 这里改用标准圆弧的胶囊，几何上左右上下完全对称，不会再变形。
 */
private val IndicatorHeight = 46.dp
private val IndicatorInsetX = 8.dp

/**
 * 抬起透镜的高度：Glasmio `ExpandedNavigationItemHeight = 54.dp`。
 *
 * 和暗色块 46dp 不同——透镜是**整格宽**的一块胶囊，抬起时由
 * [nativeControlLensGeometry] 在宽高上各加 16dp，所以会向上、向下各凸出 8dp，
 * 越过 62dp 的胶囊边界，和示例那样「鼓出来一坨」。
 */
private val LensHeight = 54.dp

/** 透镜抬起时宽高各增长的量（库 `nativeControlLensGeometry` 里写死的 16dp）。 */
private val LensGrowth = 16.dp

/**
 * 透镜里那层「放大后的图标文字」的放大倍数。
 *
 * 库在 `GlasmioNavigationBar` 里给录制源单独套了 1.16 倍（`lensSourceTabContentScale`），
 * 所以按下时珠子里的图标看着会比图标本身大一圈，像被镜片顶起来。
 */
private const val LensSourceScale = 0.16f

/** 暗色选中块的底色占比：库在无 backdrop 时给的是 `Black 10%`。 */
private const val IndicatorAlpha = 0.10f

/** 底栏厚度缩放：62dp 高的横条用满 16dp 高斯会把背后抹成一块实色板。 */
private const val BarFrostScale = 0.55f

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
 * ## 三条渲染层，各管一件事
 *
 * 1. **显现层**（[barLayer] 录制）：玻璃 + 暗色选中块。这一层被录制下来，专供按下透镜
 *    采样——透镜要能「折到胶囊」，采样源里就必须**有胶囊本身**。上一版只采壁纸 + 页面
 *    内容，于是按下的珠子是一块干净的空圆盘，用户反馈的「没有对胶囊折射」就是它。
 * 2. **抬起层**（[liftedLayer] 录制，alpha 0）：暗色块 + 放大 1.16 倍的图标文字，
 *    交给 `nativeControlLens(liftedContent = ...)`，由 GPU 折进透镜。显现层里的暗色块
 *    在按下时淡出，所以透镜内外不会各有一份（重影）。
 * 3. **透镜层**：整格宽 × 54dp，抬起时宽高各 +16dp（上下各鼓 8dp）。**只在按压时绘制**。
 *    上一版常驻绘制：`liftProgress = 0` 的透镜仍会把采样到的画面原样铺一遍，
 *    正好把底下那块暗色选中块盖成一个月牙——「暗色轮廓歪了」就是这么来的。
 *
 * ## ＋ 为什么常态看起来仍是「独立一颗」
 *
 * 融合（拖动吸附）需要**一块**玻璃的并集轮廓，否则中间会出现第三条接缝或方框；
 * 但把胶囊和圆球塞进同一块 `GlassSurface` 又容易变成「长胶囊末端的一截」。
 *
 * 库的解法（`BottomControlContact`）不是切换渲染树，而是**同一个 surface、融合半径跟着
 * 进度长大**：`nativeSmoothness * mergedProgress`。k = 0 时 smooth-min 退化成并集，
 * 零等值线正好是两条独立闭合轮廓——每颗胶囊各自有自己的边缘高光与投影；
 * k 变大时颈部才连续长出来。这里照这个做：轮廓与光学始终来自同一个 k，
 * 不再有「间隙小于阈值 → 销毁两块玻璃、重建一块并集玻璃」的那一帧卡顿。
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
    val pageBackdrop = backdrop?.backdrop ?: LocalGlassBackdrop.current
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
    val press = drag.pressProgressState

    /**
     * 栏自身的录制层（玻璃 + 暗色选中块）。
     *
     * 透镜的采样源必须是「页面 + 栏本身」：`rememberCombinedBackdrop` 先铺页面
     * （壁纸 + 列表内容），再把栏这一层叠上去。这样珠子折到的是**栏**，不是一个空洞。
     */
    val barLayer = rememberLayerBackdrop()
    val lensSource = rememberCombinedBackdrop(pageBackdrop, barLayer)

    /** 抬起时折进透镜的内容：暗色块 + 放大后的图标文字。 */
    val liftedLayer = rememberLayerBackdrop()

    // 圆球横向位移（px）。
    //
    // 这里刻意**不用 `Animatable`**：拖动的每个事件都 `launch { snapTo(...) }` 会一个事件
    // 起一条协程、每次都去抢 `MutatorMutex`，高刷屏上每秒上百次取消/重建，
    // 手感就是「跟手但发涩」。改成直接写状态（同步、零分配），松手时用一次
    // `animate()` 把值弹回 0——只有一条动画在跑。
    val ballOffset = remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
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
        val gapPx = with(density) { BarGap.toPx() }
        val smoothnessPx = with(density) { UnionSmoothness.toPx() }
        val insetPx = with(density) { IndicatorInsetX.toPx() }
        // 往左的极限：正好贴到胶囊再压进去一点（BallOverlap），再拖就没有意义了。
        val leftLimitPx = gapPx + with(density) { BallOverlap.toPx() }
        val rightLimitPx = with(density) { BallDragRight.toPx() }
        val growthPx = with(density) { BallPressGrowth.toPx() }

        val pillWidth = maxWidth - BallDiameter - BarGap
        val pillPx = with(density) { pillWidth.toPx() }
        val cell = pillPx / tabs.size
        cellPx.floatValue = cell
        val indicatorPx = (cell - 2f * insetPx).coerceAtLeast(with(density) { 36.dp.toPx() })
        val indicatorWidth = with(density) { indicatorPx.toDp() }
        val indicatorInsetPx = (cell - indicatorPx) / 2f

        // 按压长大只量化成 3 档：`derivedStateOf` 保证这条路上只有 3 次重组，
        // 而按住/松手的弹簧每帧都在跑——直接读 `ballPress` 会让整条栏跟着逐帧重组。
        val pressStep by remember { derivedStateOf { (ballPress * 3).roundToInt() / 3f } }
        val grownBallPx = ballPx + growthPx * pressStep

        // 0) 抬起层：**不可见**的一份录制，只给透镜当 liftedContent。
        //    内容是「这一格被顶起来」的样子：暗色块 + 放大 1.16 倍的图标文字。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight)
                .alpha(0f)
                .layerBackdrop(liftedLayer)
                .drawWithContent {
                    if (drawContext.graphicsLayer === liftedLayer.graphicsLayer) drawContent()
                },
        ) {
            SelectionBlock(
                width = indicatorWidth,
                height = IndicatorHeight,
                color = darkInk.copy(alpha = IndicatorAlpha),
                translationX = { cellPx.floatValue * drag.value + indicatorInsetPx },
                alpha = { 1f },
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    TabCell(
                        tab = tab,
                        selected = tab == selected,
                        modifier = Modifier.weight(1f),
                        // 录制源也按同一个进度换色：透镜里折进去的那份，按下时同样是
                        // 「蓝 → 黑」，不会和外面那格对不上。
                        press = { press.value },
                        sourceScale = { 1f + LensSourceScale * press.value },
                    )
                }
            }
        }

        // 1) 显现层：玻璃 + 暗色选中块，整层录进 barLayer 供透镜采样。
        //
        //    **不能开 `reuseContent`**：它的语义是「录一次、之后重放录下的显示列表」，
        //    而这一层里的东西（圆球的 offset、暗色块的 translationX、圆球的缩放）
        //    都是逐帧变的绘制/布局阶段状态，重放出来就是旧画面。上一版开了它，
        //    慢速拖 ＋ 时圆球停在上一次重录的位置、而外面的加号继续跟手 ——
        //    用户反馈的「加号跟圆形背景脱离单独偏移」就是这么来的。
        //    官方文档也写了：只有「可见与录制的绘制完全一致（含 graphicsLayer 读取）」
        //    时才能开，我们显然不满足。
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(barLayer, reuseContent = false),
        ) {
            // 玻璃交给独立的子树：只有它读拖动位移，整条栏其余部分
            //（三格图标、透镜、＋）不跟着逐帧重组。
            BottomBarGlass(
                pillPx = pillPx,
                barPx = barPx,
                slotPx = slotPx,
                restGapPx = gapPx,
                smoothnessPx = smoothnessPx,
                ballPx = ballPx,
                grownBallPx = grownBallPx,
                offset = { ballOffset.floatValue },
                backdrop = backdrop,
            ) {
                // 暗色选中块：常态显示；按下时淡出，让位给透镜（透镜里另有一份被折起来的）。
                SelectionBlock(
                    width = indicatorWidth,
                    height = IndicatorHeight,
                    color = darkInk.copy(alpha = IndicatorAlpha),
                    translationX = { cellPx.floatValue * drag.value + indicatorInsetPx },
                    alpha = { 1f - press.value },
                )
            }
        }

        // 2) 三格：拖动手势由库的 DampedDragAnimation 统一接管（含点击定位）。
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
                    // 按下时选中格**不淡出**：库虽然传了 hideSelectedItem，但那份淡出的是
                    // 可见行，真正的选中图标由单独的 morphing 组件画；这里没有那个组件，
                    // 照抄淡出就会让图标整块消失。改成把强调色渐变到正文色。
                    press = { press.value },
                )
            }
        }

        // 3) 按下透镜：整格宽的胶囊 + 原生控制透镜。**只在按压时**绘制。
        val lensVisible by remember(press) {
            derivedStateOf { press.value > 0.01f }
        }
        if (lensVisible) {
            Box(
                Modifier
                    // 只横向位移。垂直靠 `align(CenterStart)` 居中——之前这里又加了一次
                    // `(SlotHeight - LensHeight) / 2`，等于把 54dp 的盒子先居中再往下推 8dp，
                    // 透镜整块沉到槽底，只压住胶囊下边缘（用户反馈的「玻璃珠没有居中」）。
                    // 54dp 的盒子里，放大到 70dp 的子项由 nativeControlLensGeometry 居中放置，
                    // 上下各凸出 4dp 越过 62dp 的胶囊，正好对称。
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((cell * drag.value).roundToInt(), 0) }
                    .width(with(density) { cell.toDp() })
                    .height(LensHeight),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .nativeControlLensGeometry(
                            style = NativeControlLensStyle.TabBarIndicator,
                            horizontalProgress = { press.value },
                        )
                        .drawPlainBackdrop(
                            // 采样「页面 + 栏」：折到的是胶囊与选中块，不是一块空洞。
                            backdrop = lensSource,
                            shape = { Capsule() },
                            effects = {
                                nativeControlLens(
                                    style = NativeControlLensStyle.TabBarIndicator,
                                    liftProgress = press.value,
                                    isDark = isDark,
                                    liftedContent = liftedLayer,
                                )
                            },
                        ),
                )
            }
        }

        // 4) 独立悬浮的「＋」：玻璃球由第 1 步的并集玻璃画（常态 k = 0 就是一颗独立的球），
        //    这里只画字形与手势。位置与缩放都在 layout / draw 阶段的 lambda 里读状态，
        //    拖动时**不重组**这一层。
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(BallDiameter)
                // 只跟手拖动量。缩放由下面的 graphicsLayer 绕**中心**做，所以这里
                // 不能再减 growth/2，否则长大那一帧圆球圆心与字形中心会差半个增量。
                .offset { IntOffset(ballOffset.floatValue.roundToInt(), 0) }
                .graphicsLayer {
                    // 与玻璃同一个「按压长大」档位（pressStep），保证球与字形同心同大。
                    val scale = (ballPx + growthPx * pressStep) / ballPx
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
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
                            onDragStart = {
                                // 上一次回弹还没跑完就再次按住：先停掉，避免两条动画抢同一个值
                                settleJob?.cancel()
                                ballDragging = true
                            },
                            onDragEnd = {
                                ballDragging = false
                                settleJob = scope.launch {
                                    animate(
                                        initialValue = ballOffset.floatValue,
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
                                    ) { value, _ -> ballOffset.floatValue = value }
                                }
                            },
                            onDragCancel = {
                                ballDragging = false
                                settleJob = scope.launch {
                                    animate(
                                        initialValue = ballOffset.floatValue,
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
                                    ) { value, _ -> ballOffset.floatValue = value }
                                }
                            },
                            onDrag = { change, amount ->
                                // 同步写状态：不再每个事件起一条协程去 snapTo。
                                ballOffset.floatValue = (ballOffset.floatValue + amount.x)
                                    .coerceIn(-leftLimitPx, rightLimitPx)
                                change.consume()
                            },
                        )
                    }
                    .semantics { contentDescription = createLabel },
                contentAlignment = Alignment.Center,
            ) {
                PlusGlyph(darkInk, glyphSize = 24.dp)
            }
        }
    }
}

/**
 * 底栏的玻璃本体：左侧胶囊 + 右侧圆球，**始终是同一块 `GlassSurface`**。
 *
 * 这是这一版解决「融合动画卡顿」的核心。上一版按 `gap < 阈值` 在
 * 「两块独立玻璃」与「一块并集玻璃」之间切换：切换那一帧要销毁两个 `GlassSurface`、
 * 再建一个新的，材质、模糊半径、阴影全部重来一次，真机上就是拖动途中的那一下顿。
 *
 * 现在照库的 `BottomControlContact` 做：**只有一块 surface，融合半径 k 随进度长大**。
 * - 常态 `merge = 0` → `k = 0`，smooth-min 退化成并集，零等值线是两条独立闭合轮廓，
 *   圆球仍是一颗有自己边缘高光与投影的玻璃球（这就是「＋ 独立悬浮」的观感来源）；
 * - 拖近时 `merge` 从 0 连续涨到 1，k 跟着长大，颈部连续变粗；
 * - 轮廓与光学场共用同一个 k，所以贴合处没有那条分明的界线。
 *
 * @param offset 圆球拖动位移（px）。**只有这个子树读它**：整条栏其余部分不跟着逐帧重组。
 */
@Composable
private fun BottomBarGlass(
    pillPx: Float,
    barPx: Float,
    slotPx: Float,
    restGapPx: Float,
    smoothnessPx: Float,
    ballPx: Float,
    grownBallPx: Float,
    offset: () -> Float,
    backdrop: GlassContentBackdrop?,
    content: @Composable BoxScope.() -> Unit,
) {
    // 圆球圆心：常态 = 胶囊右端 + 缝 + 半径，再加上拖动量。
    val center = pillPx + restGapPx + ballPx / 2f + offset()
    // 量化到整像素：拖动时每帧都会换一个 Shape，玻璃材质跟着重建渲染链；
    // 1px 的台阶在 3x 屏上约 0.33dp，看不出跳动，重建次数却少了一大半。
    val ballLeft = center.roundToInt().toFloat() - grownBallPx / 2f
    val gap = ballLeft - pillPx
    // 融合进度：贴到栏上（gap = 0）为 1，回到常态缝（restGap）为 0。
    // smoothstep 让「刚开始靠近」与「完全贴合」两端都更缓，中段才明显变粗。
    val raw = ((restGapPx - gap) / restGapPx).coerceIn(0f, 1f)
    val eased = raw * raw * (3f - 2f * raw)
    // 再量化到 1/48：k 每帧都变一点点也会让材质重建，0.2dp 的台阶看不出来。
    val merge = (eased * 48f).roundToInt() / 48f
    val k = smoothnessPx * merge

    val capsuleRect = Rect(0f, (slotPx - barPx) / 2f, pillPx, (slotPx + barPx) / 2f)
    val ballRect = Rect(
        ballLeft,
        (slotPx - grownBallPx) / 2f,
        ballLeft + grownBallPx,
        (slotPx + grownBallPx) / 2f,
    )
    val first = LiquidContactCapsule(Size(pillPx, barPx), capsuleRect)
    val second = LiquidContactCapsule(Size(grownBallPx, grownBallPx), ballRect)

    // 只有**真的连通**时才需要 SDF 折线：smooth-min 的零等值线在 gap = k/2 处才合拢，
    // 更大的间隙下轮廓就是两条独立圆角矩形，用纯 Compose Path 画即可。
    // 之前不分情况一律走并集路径，等于拖动时每帧都在主线程重算一条折线——那是掉帧主因。
    val outline: Shape = if (gap < k * 0.5f) {
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
private fun androidx.compose.foundation.layout.BoxScope.SelectionBlock(
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
private fun RowScope.TabCell(
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
private fun PlusGlyph(color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 2.2.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.1f), Offset(c, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.1f, c), Offset(size.width * 0.9f, c), stroke, StrokeCap.Round)
    }
}
