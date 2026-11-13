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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.liquidContactShape
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

/** 胶囊高度。图 2 的示例栏是扁的，胶囊略小于右侧圆球直径。 */
private val BarHeight = 52.dp

/** 右侧「＋」圆球直径：独立悬浮的一整块玻璃。 */
private val BallDiameter = 56.dp

/** 选中指示器 / 抬起后的玻璃珠直径：比胶囊上下各凸 4dp。 */
private val LensDiameter = 60.dp

/** 底栏槽高：三者取最大，指示器上下都不被裁。 */
private val SlotHeight = 60.dp

/** 常态胶囊右端到圆球左端的缝。 */
private val BarGap = 8.dp

/**
 * 液态接触距离（库的 `liquidContactShape` 参数，单位是 px）。
 *
 * 库里间隙 ≥ `contactDistance` 时直接返回两块的原生并集（各自完整闭合），
 * 小于它才进入接触分支；`joinDistance = contactDistance / 2`，间隙再小于
 * 它才真正长出收腰颈部。设 8dp = 常态缝，于是：
 * 常态 = 完整独立的胶囊 + 圆球（单独悬浮）；按住圆球长大 3dp 就进入接触分支，
 * 再往左拖一点就融合成图 2 的收腰。
 */
private val ContactDistance = 8.dp

/** 圆球最多能往左拖多远（越界就夹住，避免两片深度重叠导致路径自交）。 */
private val BallDragLeft = 10.dp

/** 圆球最多能往右拖多远。 */
private val BallDragRight = 8.dp

/** 按住时圆球长大的量：图 2 里那颗球比常态明显大一圈。 */
private val BallPressGrowth = 6.dp

/** 底栏厚度缩放：52dp 高的横条用满 16dp 高斯会把背后抹成一块实色板。 */
private const val BarFrostScale = 0.55f

/** 形状参数万一非法的兜底（正常路径不会走到）。 */
private val FallbackShape = RoundedCornerShape(50)

/**
 * 把库算出来的 [io.github.neoruaa.glasmio.glass.LiquidContactShape] 轮廓包成
 * 「库不认识的普通 Shape」。
 *
 * 几何仍然 100% 用库的接触算法；但 `GlassSurface` 里有一串
 * `is LiquidContactShape -> navigationContainerGlassField(...)` 的 native 光学分支，
 * 那是给 `BottomNavigationContact` 整套脚手架用的（要 nativeUnion / opticalCenter /
 * 共享材质层）。我们只借形状，不接那套脚手架，所以故意让它落到 `else -> null`
 * 的通用 `lens(... chromaticAberration = true)` 路径——alpha14 已验证过这条路径
 * 配 Clear 材质是安全且透明的。
 */
private class ContactOutlineShape(
    private val delegate: Shape,
    private val capsuleBounds: Rect,
    private val ballBounds: Rect,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = try {
        delegate.createOutline(size, layoutDirection, density)
    } catch (_: LinkageError) {
        // 库的接触几何内部走 PathIterator，依赖 androidx.graphics.path 的 native 库。
        // 真机（API 34+ / graphics-path）有；Robolectric 的 JVM 单测没有，静态初始化会
        // 抛 UnsatisfiedLinkError / NoClassDefFoundError（都是 LinkageError）。
        // 这里降级成一块等价的纯 Compose 轮廓，让启动冒烟测试能画完首帧。
        val radius = capsuleBounds.height / 2f
        Outline.Generic(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        capsuleBounds.left, capsuleBounds.top,
                        capsuleBounds.right, capsuleBounds.bottom,
                        radius, radius,
                    ),
                )
                addOval(ballBounds)
            },
        )
    }
}

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 这一版从 Glasmio 源码移植过来的三样东西
 *
 * 1. **可拖动的选中指示器**（[DampedDragAnimation] + `TabBarIndicator`）：
 *    库 `GlasmioNavigationBar` / `GlasmioTabBar` 用的是同一个类。它给出
 *    `value`（浮点 tab 位置，拖动中连续）、`pressProgress`（抬起程度）、
 *    `scaleX/scaleY`（按下 78/56 放大）和三组 UIKit 弹簧。`drag.modifier`
 *    直接挂在三格 Row 上，按下定位、拖动跟手、松手吸附到最近一格并提交
 *    `onSelected`；外部切页由 `animateToValue(..., animatePress = false)` 滑过去。
 *    这就是图 3 的拖动动效，也让「按钮滑动」回来了。
 * 2. **常态暗色范围**：库在不用 backdrop 时给的指示器底色就是
 *    `Color.Black.copy(alpha = 0.10f)`（深色主题 `White 0.10`）。这里用主文字色
 *    的 10% 复刻它，随 `pressProgress` 淡出。
 * 3. **液态吸附**（[liquidContactShape]）：库 `GlasmioToolbarActionGroup` /
 *    `BottomNavigationContact` 用两个 `LiquidContactCapsule` 算连续的 S 形接触轮廓。
 *    这里整条底栏始终只有**一块玻璃**，Shape 由胶囊与圆球两个 capsule 生成：
 *    常态两片各自完整（单独悬浮），按住圆球往左拖 → 间隙缩小 → 自动长出
 *    收腰颈部（图 2）。只有一块玻璃才有连续的边缘高光，也不会两层 Clear
 *    互相糊掉透明度。
 *
 * 抬起时指示器由暗色块换成一颗 `clear = true` 的 Clear 玻璃珠（`GlassCirclePanel`），
 * 采样同一份「壁纸 + 列表内容」合成层，边缘由材质自己画色散折射。
 * 库里这一步是 `drawPlainBackdrop + nativeControlLens(liftedContent = labelsBackdrop)`，
 * 会把栏内图标文字也折进去；那条路要单独起 RuntimeShader / MipAtlas，
 * 没有真机可验证时不冒险，先用已验证过的 Clear 材质承担折射。
 *
 * 三格的图标自己画（House / Calendar / Search）：库的 `SfSymbol` 只有十几个符号，
 * 既没有日历也没有加号，之前日历只能拿 `Clock` 顶替，语义还是错的。
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
            // 库的 UIKit TabBarIndicator 按下放大系数
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
    // 外部切页（返回键、切日历）只挪指示器，不触发导航回调——和库里一致。
    LaunchedEffect(selectedIndex, drag) {
        if (drag.targetValue != selectedIndex.toFloat()) {
            drag.animateToValue(selectedIndex.toFloat(), animatePress = false)
        }
    }
    // 只在「抬起布尔值」翻转时重组，pressProgress 的逐帧变化留给 graphicsLayer。
    val lifted by remember { derivedStateOf { drag.pressProgress > 0.01f } }

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
        val contactPx = with(density) { ContactDistance.toPx() }
        val leftLimitPx = with(density) { BallDragLeft.toPx() }
        val rightLimitPx = with(density) { BallDragRight.toPx() }
        val growthPx = with(density) { BallPressGrowth.toPx() }

        val pillWidth = maxWidth - BallDiameter - BarGap
        val pillPx = with(density) { pillWidth.toPx() }
        val cell = pillPx / tabs.size
        cellPx.floatValue = cell
        val lensInsetPx = ((cell - lensPx) / 2f).coerceAtLeast(0f)

        // 圆球被按住时以中心为锚长大一圈（左缘也往胶囊靠 3dp），
        // 再往左拖动就进一步缩小间隙，触发液态收腰
        // 量化成 5 档：库的 ContactProfileCache 按 Size 精确匹配做缓存，
        // 连续变化的尺寸会每帧淘汰缓存、重算整条接触路径。
        val growth = growthPx * ((ballPress * 4).roundToInt() / 4f)
        val grownBallPx = ballPx + growth
        val ballLeft = pillPx + gapPx + ballDrag.value - growth / 2f
        val capsule = LiquidContactCapsule(
            size = Size(pillPx, barPx),
            bounds = Rect(0f, (slotPx - barPx) / 2f, pillPx, (slotPx + barPx) / 2f),
        )
        val ball = LiquidContactCapsule(
            size = Size(grownBallPx, grownBallPx),
            bounds = Rect(
                ballLeft,
                (slotPx - grownBallPx) / 2f,
                ballLeft + grownBallPx,
                (slotPx + grownBallPx) / 2f,
            ),
        )
        val barShape: Shape = ContactOutlineShape(
            delegate = liquidContactShape(
                capsule,
                ball,
                contactDistance = contactPx,
                includeDisconnected = true,
            ) ?: FallbackShape,
            capsuleBounds = capsule.bounds,
            ballBounds = ball.bounds,
        )

        // 1) 整条底栏只有一块玻璃：常态两片各自完整，靠近时自动长颈部
        GlassPanel(
            modifier = Modifier.fillMaxSize(),
            shape = barShape,
            frostScale = BarFrostScale,
            backdrop = backdrop,
        ) {}

        // 2) 常态暗色选中范围：垫在图标文字下面，会随 value 滑动
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(LensDiameter)
                .graphicsLayer {
                    translationX = cellPx.floatValue * drag.value + lensInsetPx
                    scaleX = drag.scaleX
                    scaleY = drag.scaleY
                    alpha = 1f - drag.pressProgress
                }
                .drawBehind {
                    drawCircle(darkInk.copy(alpha = 0.10f))
                },
        )

        // 3) 三格：拖动手势由库的 DampedDragAnimation 统一接管（含点击定位）
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

        // 4) 抬起后的玻璃珠：盖在图标文字之上，跟着手指走，边缘有 Clear 材质的色散
        if (lifted) {
            GlassCirclePanel(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(LensDiameter)
                    .graphicsLayer {
                        translationX = cellPx.floatValue * drag.value + lensInsetPx
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        alpha = drag.pressProgress
                    },
                clear = true,
                frostScale = BarFrostScale,
                backdrop = backdrop,
            ) {}
        }

        // 5) 单独悬浮的「＋」：按住拖动改变圆球位置 → 液态吸附；点击新建
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(BallDiameter)
                .offset { IntOffset(ballDrag.value.roundToInt(), 0) }
                .drawBehind {
                    // 图 2 里按住的那颗球会被按压高光刷白，只在按住期间出现
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
                            val next = (ballDrag.value + amount.x).coerceIn(-leftLimitPx, rightLimitPx)
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
