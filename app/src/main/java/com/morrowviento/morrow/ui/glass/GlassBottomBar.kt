package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
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
import com.kyant.capsule.ContinuousRoundedRectangle

/**
 * 底栏的三个位置。
 *
 * 只有三个：主页、日历、搜索（预留）。新建不占位置——它是右侧那颗独立的悬浮圆钮，
 * 和 iOS 26 把「搜索」从 tab 里拆出来单独一颗是同一个排布。
 */
enum class GlassTab(val label: String) {
    HOME("主页"),
    CALENDAR("日历"),
    SEARCH("搜索"),
}

/** 底栏高度与悬浮圆钮直径：两者必须一样，否则并排时基线会歪。 */
private val BarHeight = 62.dp

/** 悬浮圆钮与底栏之间的缝：和 iOS 26 一样，留一条能看见底色的缝。 */
private val BarGap = 10.dp

/** 胶囊形状：只在文件级建一次，避免每帧给每一格新建 Shape。 */
private val BarShape = RoundedCornerShape(50)

/**
 * 选中透镜的高度。**不是满高**：Glasmio 示例里的底栏（图 2）那枚指示器是一块
 * 「方中带圆」的玻璃片，上下各留出栏体自己的一圈边——正是这圈边让它读起来是
 * 「栏里嵌了一片玻璃」，而不是「一格被涂了色」。
 */
private val LensHeight = 46.dp

/** 透镜的圆角：≈ 高度的三分之一，接近 squircle；给满高就会退化成胶囊。 */
private val LensCorner = 15.dp

/** 透镜左右留白：格子两侧各让出一点，滑动过程中不会蹭到相邻格的图标。 */
private val LensInsetX = 12.dp

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 为什么不再用 `GlasmioNavigationBar`
 *
 * 库的导航栏有两个改不动的限制，正好对上「底栏完全不透明、且不跟随厚度」：
 *
 * - 它由 `BottomNavigationContact` 统一画材质，材质是库内部按尺寸挑的
 *   `NavigationGlassMaterial`（其中一档带三层不透明 `fills`），**没有**
 *   `material` / `frostRadius` 参数可传——厚度滑块对它完全无效；
 * - 选中格那块「透镜指示器」也只能由它自己画，外部拿不到。
 *
 * 现在就是一块普通玻璃：`GlassPanel(capsule = true)`——和卡片同一档 Clear 材质、
 * 同一个 `frostRadius`（跟随厚度）、同一份色纱。整条底栏因此和卡片一样透，
 * 也会被「通透 ↔ 厚重」改变。
 *
 * ## 选中态照 Glasmio 示例的底栏（图 2）
 *
 * 那一枚指示器不是「满格涂色」，而是一块**方中带圆的玻璃片**：
 * 上下留出栏体的一圈边、左右不贴邻格，强调色纱上浓下淡，边缘一圈细描边。
 * 三格的图标也照着它的分量做过：笔画 2.0dp（选中 2.3dp）、21dp 见方、
 * 未选中用主文字色（不是次级灰），选中才换成强调色 + 半粗字重。
 * 尺寸与形状参数见文件头的 `Lens*` 常量。
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
) {
    val tabs = GlassTab.entries
    Row(
        modifier = modifier.height(BarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassPanel(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            capsule = true,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // 每格宽度 = 整条栏 / 3；透镜的位移与宽度都按它算，
                // 因此改格数或改高度都不用再调常量。
                val cell = maxWidth / tabs.size
                val index = tabs.indexOf(selected).coerceAtLeast(0)
                val slide by animateFloatAsState(
                    targetValue = index.toFloat(),
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f, visibilityThreshold = 0.01f),
                    label = "tabLensSlide",
                )
                TabLens(
                    modifier = Modifier
                        .offset { IntOffset((cell * slide).roundToPx(), 0) }
                        .width(cell)
                        .fillMaxHeight()
                        // 上下各让出一圈边：透镜因此不是满高胶囊，而是栏里嵌的一片玻璃
                        .padding(
                            horizontal = LensInsetX,
                            vertical = (BarHeight - LensHeight) / 2,
                        ),
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEach { tab ->
                        TabCell(
                            tab = tab,
                            selected = tab == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelected(tab) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(BarGap))
        FloatingCreateButton(onClick = onCreate, label = createLabel)
    }
}

/**
 * 选中格的透镜（照 Glasmio 示例底栏那一枚）。
 *
 * 形状是一块**方中带圆**的玻璃片，不是满高胶囊：上下留出栏体的一圈边，
 * 左右不贴邻格。四件事让它像玻璃而不是色块：
 *
 * 1. 强调色纱，上浓下淡（`0.30 → 0.16`）——玻璃片顶部被照亮、底部沉下去；
 * 2. 上缘一道白色镜面，只铺到 52% 高度就化掉，不做整块渐变（整块会变奶白）；
 * 3. 一圈 0.9dp 的强调色描边，就是玻璃的「厚边」；
 * 4. 形状用 `ContinuousRoundedRectangle`（squircle），描边由同一个 shape 的
 *    `Outline` 画出来，因此边和面严格贴合——用 `RoundedCornerShape` 描边会在
 *    直边与圆角交界处露出一条缝。
 *
 * 不再嵌第二片 `GlassSurface`：底栏里再套一层玻璃就是两次采样、两次模糊，
 * 两层糊在一起反而让「选中」糊掉。
 */
@Composable
private fun TabLens(modifier: Modifier = Modifier) {
    val accent = glassAccent()
    val shape = remember { ContinuousRoundedRectangle(LensCorner) }
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                // 色纱：顶部亮、底部沉，玻璃才有厚度
                val wash = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.16f)),
                )
                val sheen = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0f)),
                    endY = size.height * 0.52f,
                )
                val rim = Stroke(width = 0.9.dp.toPx())
                val rimColor = accent.copy(alpha = 0.38f)
                onDrawBehind {
                    drawRect(wash)
                    drawRect(sheen)
                    drawOutline(outline, color = rimColor, style = rim)
                }
            },
    )
}

/** 一格：图标 + 小字，按下缩小一档（和 chips、文字按钮同一条弹簧）。 */
@Composable
private fun RowScope.TabCell(
    tab: GlassTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f, visibilityThreshold = 0.01f),
        label = "tabPress",
    )
    // 选中走强调色、加粗一档；未选中走**主文字色**而不是次级灰——
    // 示例底栏里未选中的 Home / Settings 都是近乎纯黑的实心图标，
    // 用次级灰会让整条栏看起来是「三个禁用按钮」。
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
 * 细线图标重得多，选中再重一档，配着强调色就有「按下去的那一颗」的重量差。
 */
@Composable
private fun TabGlyph(tab: GlassTab, color: Color, glyphSize: Dp, selected: Boolean) {
    Canvas(Modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = (if (selected) 2.3f else 2.0f).dp.toPx()
        when (tab) {
            // 房子：屋顶两条斜线 + 墙体 + 一扇**实心**小门（示例里那颗 Home 的重心就在门上）
            GlassTab.HOME -> {
                drawLine(color, Offset(w * 0.50f, h * 0.13f), Offset(w * 0.91f, h * 0.47f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.13f), Offset(w * 0.09f, h * 0.47f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.17f, h * 0.40f), Offset(w * 0.17f, h * 0.89f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.83f, h * 0.40f), Offset(w * 0.83f, h * 0.89f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.17f, h * 0.89f), Offset(w * 0.83f, h * 0.89f), stroke, StrokeCap.Round)
                // 门：贴底的一条实心圆角矩形，窗户般的留白交给底色
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

/**
 * 右侧独立的圆形按钮（玻璃里的「＋」）。
 *
 * 和底栏一样是 [GlassCirclePanel]：跟随厚度、跟随主题，按下时由材质自己画折射光斑，
 * 不是 Material 的矩形涟漪。
 */
@Composable
private fun FloatingCreateButton(onClick: () -> Unit, label: String) {
    GlassCirclePanel(
        modifier = Modifier
            .size(BarHeight)
            .semantics { contentDescription = label },
        onClick = onClick,
    ) {
        PlusGlyph(glassTextPrimary(), glyphSize = 25.dp)
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
