package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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

/**
 * 胶囊高度。
 *
 * 图 2 的示例栏是扁的：胶囊高度略小于右侧圆球直径。上一版 62dp 胶囊 + 76dp 珠子
 * 让整块底栏在屏幕上占掉一大条，改到 52dp 之后比例才对。
 */
private val BarHeight = 52.dp

/** 右侧「＋」圆球直径：独立悬浮的一整块玻璃，比胶囊略大。 */
private val BallDiameter = 56.dp

/** 点击时才出现的玻璃珠直径：比胶囊上下各凸 4dp，正好和图 2 那颗珠子一样。 */
private val LensDiameter = 60.dp

/** 底栏槽高：三者取最大，玻璃珠上下都不被裁。 */
private val SlotHeight = 60.dp

/** 胶囊与圆球之间的缝。留缝才有「单独悬浮」——熔成一整块会把 ＋ 吞进栏里。 */
private val BarGap = 8.dp

/** 玻璃珠上的强调色纱：只有 0.10，主体必须是透过去的壁纸 + 卡片。 */
private const val LensTintAlpha = 0.10f

/**
 * 底栏的厚度缩放（默认档 0.62 → 约 9dp 高斯）。
 *
 * 一条 52dp 高的横条若用满 16dp，背后全被抹平，眼睛读到的就是「一块实色板」。
 * 四档厚度的差异依旧单调可辨。
 */
private const val BarFrostScale = 0.55f

/** 松手后玻璃珠再停留多久才淡出：够把整段弹簧滑动看完。 */
private const val LensLingerMs = 700L

/** 三格按下时的裁剪形状：只在文件级建一次。 */
private val BarShape = RoundedCornerShape(50)

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 不用 `GlasmioNavigationBar` 的原因
 *
 * 库的导航栏由 `BottomNavigationContact` 统一画材质，材质是库内部按尺寸挑的
 * `NavigationGlassMaterial`，**没有** `material` / `frostRadius` / `backdrop` 参数可传
 * ——厚度滑块对它完全无效；示例里它能透，是因为 `GlasmioNavigationScaffold` 替它把
 * 整页滚动内容登记成了 `contentBackdrop`。我们不用那套脚手架，所以自己接过这两个职责。
 *
 * ## 这一版的四件事
 *
 * 1. **采样内容层**（[backdrop]）：见 [glassContentSource]。底栏读的是「壁纸 + 列表卡片」
 *    合成层，卡片边缘经过时会被折射弯折。这是上一版修好的透明度，原样保留。
 * 2. **胶囊与圆球拆开**：两块独立玻璃，中间 8dp 缝。圆球有自己的完整圆形轮廓、
 *    独立高光和材质按压光学，这才是「单独悬浮的 ＋」。熔成一整块联合形状时，
 *    ＋ 只是画在长胶囊末端的一个字符，看起来不像是按钮。
 * 3. **玻璃珠只在点击的时候出现**：常态没有透镜，选中只靠强调色图标 + 半粗文字表达。
 *    按下某格 → 珠子从上一位置弹簧滑过去并淡入；松手后再停留 [LensLingerMs] 让滑动
 *    看得完整，然后淡出。常态不进 composition（省一块常驻的玻璃采样）。
 * 4. **滑动回来了**：珠子的位移由 `tabLensSlide` 弹簧驱动，每次按下都会重新设目标格，
 *    包括「搜索」这种不切页的预留格；外部切页（返回键等）也会把目标同步过去。
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
    val accent = glassAccent()
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)

    // 珠子的目标格：只在按下时改，外部切页时同步。
    var lensIndex by remember { mutableIntStateOf(selectedIndex) }
    var lensHeld by remember { mutableStateOf(false) }
    var lensLinger by remember { mutableStateOf(false) }
    var lensPulse by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedIndex) {
        if (!lensHeld) lensIndex = selectedIndex
    }
    // 按下与松手各触发一次：松手会取消按住期间的那次计时，重新留 700ms。
    LaunchedEffect(lensPulse) {
        if (lensPulse == 0) return@LaunchedEffect
        lensLinger = true
        delay(LensLingerMs)
        lensLinger = false
    }

    val lensVisible = lensHeld || lensLinger
    val lensAlpha by animateFloatAsState(
        targetValue = if (lensVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (lensVisible) 110 else 240),
        label = "tabLensAlpha",
    )
    val slide by animateFloatAsState(
        targetValue = lensIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f, visibilityThreshold = 0.01f),
        label = "tabLensSlide",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SlotHeight),
    ) {
        val pillWidth = maxWidth - BallDiameter - BarGap
        val cell = pillWidth / tabs.size
        val lensInset = ((cell - LensDiameter) / 2).coerceAtLeast(0.dp)

        // 1) 胶囊：一条独立的矮玻璃
        GlassPanel(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight),
            capsule = true,
            frostScale = BarFrostScale,
            backdrop = backdrop,
        ) {}

        // 2) 点击透镜：真玻璃珠、会滑、会淡出；常态 alpha=0 时整个不组合
        if (lensAlpha > 0.001f) {
            GlassCirclePanel(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((cell * slide + lensInset).roundToPx(), 0) }
                    .size(LensDiameter)
                    .graphicsLayer { alpha = lensAlpha },
                tint = accent.copy(alpha = LensTintAlpha),
                clear = true,
                frostScale = BarFrostScale,
                backdrop = backdrop,
            ) {}
        }

        // 3) 三格的图标与文字压在珠子之上（图 2 里文字就在透镜里面）
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(pillWidth)
                .height(BarHeight),
        ) {
            tabs.forEachIndexed { index, tab ->
                TabCell(
                    tab = tab,
                    selected = tab == selected,
                    modifier = Modifier.weight(1f),
                    onPressChanged = { held ->
                        if (held) lensIndex = index
                        lensHeld = held
                        lensPulse++
                    },
                    onClick = { onSelected(tab) },
                )
            }
        }

        // 4) 单独悬浮的「＋」：一整块圆形玻璃，按压光学由材质自己画
        GlassCirclePanel(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(BallDiameter)
                .semantics { contentDescription = createLabel },
            clear = true,
            frostScale = BarFrostScale,
            backdrop = backdrop,
            onClick = onCreate,
        ) {
            PlusGlyph(glassTextPrimary(), glyphSize = 23.dp)
        }
    }
}

/** 一格：图标 + 小字，按下缩小一档（和 chips、文字按钮同一条弹簧）。 */
@Composable
private fun RowScope.TabCell(
    tab: GlassTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onPressChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f, visibilityThreshold = 0.01f),
        label = "tabPress",
    )
    // 首次组合时 pressed=false，不能当成一次「松手」广播出去，否则冷启动会闪一下珠子。
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        onPressChanged(pressed)
    }

    // 常态没有透镜，选中只能靠颜色说清：示例底栏里 Materials 也是蓝图标 + 蓝字。
    // 未选中走**主文字色**而不是次级灰，否则整条栏看起来是「三个禁用按钮」。
    val ink = if (selected) glassAccent() else glassTextPrimary()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                val scale = 1f - 0.05f * press
                scaleX = scale
                scaleY = scale
            }
            .clip(BarShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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

/**
 * 三格的图标：都是自绘线条，不依赖字体字形。
 *
 * 笔画按选中态分两档（2.0dp / 2.3dp）：示例底栏的图标本来就比 Material 的
 * 细线图标重得多，选中再重一档。
 */
@Composable
private fun TabGlyph(tab: GlassTab, color: Color, glyphSize: Dp, selected: Boolean) {
    Canvas(Modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = (if (selected) 2.3f else 2.0f).dp.toPx()
        when (tab) {
            // 房子：屋顶两条斜线 + 墙体 + 一扇**实心**小门
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
            // 日历：外框 + 两个挂环 + 一条分隔线 + 两个日期点
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
            // 放大镜：圈 + 斜柄
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
