package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
 * 也会被「通透 ↔ 厚重」改变。选中态是一块会滑过去的胶囊透镜：强调色纱 +
 * 顶部镜面 + 发丝描边，走同一条弹簧。
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
                        .padding(horizontal = 5.dp, vertical = 6.dp),
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
 * 选中格的透镜。
 *
 * 不再嵌第二片 `GlassSurface`：一条底栏里再套一层玻璃就是两次采样、两次模糊，
 * 两层糊在一起反而让「选中」糊掉。这里取它可感知的三件事：强调色纱、
 * 顶部镜面、发丝描边——形状会滑、边缘会亮，读起来就是一块被打到光的玻璃。
 */
@Composable
private fun TabLens(modifier: Modifier = Modifier) {
    val accent = glassAccent()
    Box(
        modifier
            .clip(BarShape)
            .background(accent.copy(alpha = 0.18f))
            .drawWithContent {
                drawContent()
                // 顶部镜面：上缘一条柔光，玻璃的「折射边」
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.65f,
                    ),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.42f),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
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
    // 选中用强调色；未选中用二级文字色——浅色下也不会灰得看不见
    val ink = if (selected) glassAccent() else glassTextSecondary()
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
        TabGlyph(tab = tab, color = ink, glyphSize = 20.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
            ),
        )
    }
}

/** 三格的图标：都是自绘线条，不依赖字体字形。 */
@Composable
private fun TabGlyph(tab: GlassTab, color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        when (tab) {
            // 房子：屋顶两条斜线 + 墙体
            GlassTab.HOME -> {
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.90f, h * 0.46f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.10f, h * 0.46f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.42f), Offset(w * 0.18f, h * 0.88f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.42f), Offset(w * 0.82f, h * 0.88f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.88f), Offset(w * 0.82f, h * 0.88f), stroke, StrokeCap.Round)
            }
            // 日历：外框 + 两个挂环 + 一条分隔线 + 两个日期点
            GlassTab.CALENDAR -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.10f, h * 0.20f),
                    size = Size(w * 0.80f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                    style = Stroke(width = stroke),
                )
                drawLine(color, Offset(w * 0.10f, h * 0.40f), Offset(w * 0.90f, h * 0.40f), stroke * 0.85f)
                drawLine(color, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.32f, h * 0.28f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, h * 0.12f), Offset(w * 0.68f, h * 0.28f), stroke, StrokeCap.Round)
                drawCircle(color, radius = w * 0.075f, center = Offset(w * 0.36f, h * 0.64f))
                drawCircle(color, radius = w * 0.075f, center = Offset(w * 0.62f, h * 0.64f))
            }
            // 放大镜：圈 + 斜柄
            GlassTab.SEARCH -> {
                drawCircle(
                    color = color,
                    radius = w * 0.30f,
                    center = Offset(w * 0.44f, h * 0.42f),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color,
                    Offset(w * 0.66f, h * 0.64f),
                    Offset(w * 0.88f, h * 0.86f),
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
        PlusGlyph(glassTextPrimary(), glyphSize = 22.dp)
    }
}

/** 「＋」：两条等长线，比字体里的 + 更居中、笔画更稳。 */
@Composable
private fun PlusGlyph(color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 1.9.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.1f), Offset(c, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.1f, c), Offset(size.width * 0.9f, c), stroke, StrokeCap.Round)
    }
}
