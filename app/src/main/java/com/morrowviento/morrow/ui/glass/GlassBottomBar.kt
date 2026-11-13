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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
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

/** 胶囊主体高度。 */
private val BarHeight = 62.dp

/**
 * 底栏槽高 = 右端圆球直径 = 选中玻璃珠直径。
 *
 * 比 [BarHeight] 高 14dp，所以圆球和珠子上下各凸出 7dp——图 2 里那颗珠子能一眼
 * 认出是「按进栏里的一颗玻璃珠」，靠的就是它不合规矩地凸出去一圈。
 */
private val BeadDiameter = 76.dp

/** 收腰起点：从圆球左边缘往左 22dp，胶囊顶边在这里开始往内收。 */
private val NeckInset = 22.dp

/** 腰部最窄处：从圆球左边缘往左 10dp。 */
private val WaistInset = 10.dp

/** 腰的半高（21dp → 腰高 42dp，胶囊 62dp、圆球 76dp）。 */
private val WaistHalf = 21.dp

/**
 * 珠子上的强调色纱：只有 0.10。珠子主体必须是「透过去的壁纸 + 卡片」，
 * 强调色只负责说明「这颗被选中」。给到 0.30 就是一块蓝色圆片。
 */
private const val BeadTintAlpha = 0.10f

/**
 * 底栏的厚度缩放（默认档 0.62 → 约 9dp 高斯）。
 *
 * 一条 62dp 高的横条若用 16dp，背后全被抹平，眼睛读到的就是「一块实色板」——
 * 这正是上一版「底栏一点都不透明」的另一半原因。降到 0.55 之后色带与卡片边缘
 * 还认得出来，四档厚度的差异也依旧单调可辨。
 */
private const val BarFrostScale = 0.55f

/** 三格按下时的裁剪形状：只在文件级建一次。 */
private val BarShape = RoundedCornerShape(50)

/**
 * 「胶囊 + 收腰颈部 + 右圆球」的**一整块**玻璃轮廓（图 1 的吸附效果）。
 *
 * 为什么必须是一整块 Shape 而不是两片玻璃叠在一起：两块独立玻璃各有自己的
 * 外描边和折射边，接缝处会互相打架；只有共用一条轮廓，边缘高光才会顺着颈部
 * 连续地流过去，读起来才是「被吸住了」。库示例里这一步是 `BottomNavigationContact`
 * 用 SDF 算出来的，0.1.3 没有把它开放给任意组合，这里用等价的解析路径：
 *
 * - 上缘：胶囊顶边 → 两段三次贝塞尔（腰点两侧切线都水平，保证连续）→ 圆球 225° 点；
 * - 圆球只保留 225°→135° 的 270° 外弧，左边缺的 90° 交给颈部；
 * - 下缘镜像，左端用 180° 圆弧封口。
 *
 * 几何是纯解析的，没有布尔挖圆（布尔会在切线交界留尖角）。参数由离屏预览调过：
 * 腰部是一段连续 S 凹弧，圆球上下各凸出 7dp。
 *
 * 用 `object` 单例：Shape 的 equals 稳定，Compose 才能缓存 outline，不会每帧重算路径。
 */
private object BarUnionShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val r1 = with(density) { (BarHeight / 2).toPx() }
        val r2 = h / 2f
        val ballCx = w - r2
        val ballLeft = ballCx - r2
        val neckStart = ballLeft - with(density) { NeckInset.toPx() }
        val waistX = ballLeft - with(density) { WaistInset.toPx() }
        val waistHalf = with(density) { WaistHalf.toPx() }

        // 颈部与圆球相接的点：225°（左上）/ 135°（左下）
        val startRad = Math.toRadians(225.0).toFloat()
        val sweepRad = Math.toRadians(270.0).toFloat()
        val leftArcStart = Math.toRadians(90.0).toFloat()
        val leftArcSweep = Math.toRadians(180.0).toFloat()
        val rad = startRad.toDouble()
        val joinX = ballCx + r2 * cos(rad).toFloat()
        val joinY = cy + r2 * sin(rad).toFloat()
        val a = (waistX - neckStart) * 0.55f
        val b = (joinX - waistX) * 0.55f
        // 进入圆球时沿该点的切线方向，否则曲线会冲过头再回落，腰部会长出尖刺
        val k = b * 0.75f
        val lowerJoinY = cy - (joinY - cy)

        return Outline.Generic(
            Path().apply {
                moveTo(r1, cy - r1)
                lineTo(neckStart, cy - r1)
                cubicTo(neckStart + a, cy - r1, waistX - a, cy - waistHalf, waistX, cy - waistHalf)
                cubicTo(waistX + b, cy - waistHalf, joinX - k, joinY + k, joinX, joinY)
                arcToRad(Rect(ballCx - r2, cy - r2, ballCx + r2, cy + r2), startRad, sweepRad, false)
                cubicTo(joinX - k, lowerJoinY - k, waistX + b, cy + waistHalf, waistX, cy + waistHalf)
                cubicTo(waistX - a, cy + waistHalf, neckStart + a, cy + r1, neckStart, cy + r1)
                lineTo(r1, cy + r1)
                arcToRad(Rect(0f, cy - r1, r1 * 2f, cy + r1), leftArcStart, leftArcSweep, false)
                close()
            },
        )
    }
}

/**
 * 常驻底栏（首页 / 日历共用一条，切页面时它不重建、不换内容）。
 *
 * ## 不用 `GlasmioNavigationBar` 的原因
 *
 * 库的导航栏由 `BottomNavigationContact` 统一画材质，材质是库内部按尺寸挑的
 * `NavigationGlassMaterial`（有的档带三层不透明 `fills`），**没有** `material` /
 * `frostRadius` / `backdrop` 参数可传——厚度滑块对它完全无效；示例里它能透，是因为
 * `GlasmioNavigationScaffold` 替它把整页滚动内容登记成了 `contentBackdrop`。
 * 我们不用那套脚手架，所以自己接过这两个职责。
 *
 * ## 三件让底栏「真的是玻璃」的事
 *
 * 1. **采样内容层**（[backdrop]）：见 [glassContentSource]。底栏读的是
 *    「壁纸 + 列表卡片」合成层，卡片边缘经过时会被折射弯折，而不是永远只有壁纸。
 * 2. **整块联合玻璃**：胶囊、颈部、右圆球共用 [BarUnionShape] 一条轮廓，
 *    这就是图 1 的「搜索按钮吸附」。
 * 3. **凸出的真玻璃珠**：选中态是一颗独立 [GlassCirclePanel]，`clear = true`
 *    不垫面纱、直径比栏高大、采样同一层内容，边缘由材质自己画折射高光。
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BeadDiameter),
    ) {
        // 三格占「胶囊顶边直到收腰起点」这一段，正好和联合形状对齐
        val tabsWidth = maxWidth - BeadDiameter - NeckInset
        val cell = tabsWidth / tabs.size
        val index = tabs.indexOf(selected).coerceAtLeast(0)
        val slide by animateFloatAsState(
            targetValue = index.toFloat(),
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f, visibilityThreshold = 0.01f),
            label = "tabLensSlide",
        )
        // 珠子居中在格子里；格子比珠子窄时（超窄屏）至少不往左溢出
        val beadInset = ((cell - BeadDiameter) / 2).coerceAtLeast(0.dp)

        // 1) 整块联合玻璃：胶囊 + 颈部 + 圆球，一条轮廓、一次采样
        GlassPanel(
            modifier = Modifier.fillMaxSize(),
            shape = BarUnionShape,
            backdrop = backdrop,
            frostScale = BarFrostScale,
        ) {}

        // 2) 会滑的玻璃珠：真玻璃、无面纱、凸出栏体
        GlassCirclePanel(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((cell * slide + beadInset).roundToPx(), 0) }
                .size(BeadDiameter),
            tint = accent.copy(alpha = BeadTintAlpha),
            frostScale = BarFrostScale,
            clear = true,
            backdrop = backdrop,
        ) {}

        // 3) 三格的图标与文字压在珠子之上（图 2 里文字就在透镜里面）
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(tabsWidth)
                .height(BarHeight),
        ) {
            tabs.forEach { tab ->
                TabCell(
                    tab = tab,
                    selected = tab == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(tab) },
                )
            }
        }

        // 4) 右端圆球上的「＋」：联合玻璃本身不给 onClick（否则整条栏都可点），
        //    按压反馈由这一层自己画
        CreateBall(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(BeadDiameter),
            onClick = onCreate,
            label = createLabel,
        )
    }
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
                // 门：贴底的一条实心圆角矩形
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
 * 右端圆球上的「＋」。
 *
 * 圆球已经是 [BarUnionShape] 的一部分，这里**不再叠第二片玻璃**（两层 Clear 叠在
 * 一起会把刚才修好的透明度又糊回去，也会在圆球左缘留一道假的接缝）。
 * 按压改成缩一档 + 一圈很淡的强调色高光，和其余按钮同一条弹簧。
 */
@Composable
private fun CreateBall(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    label: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f, visibilityThreshold = 0.01f),
        label = "createPress",
    )
    val accent = glassAccent()
    Box(
        modifier
            .graphicsLayer {
                val s = 1f - 0.05f * press
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                if (press > 0.001f) {
                    drawCircle(accent.copy(alpha = 0.12f * press))
                }
            }
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
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
