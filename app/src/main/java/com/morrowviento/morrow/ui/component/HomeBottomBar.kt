package com.morrowviento.morrow.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionHost
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionStyle

/**
 * 常驻底栏（首页与日历共用同一条：切页面时它不重建、不换内容）。
 *
 * 材质与按压**全部交给 Glasmio 自己的玻璃按钮**：三个格子各是一个
 * `GlassSurface(onClick = ...)`，装在 `GlasmioToolbarActionHost(Merged)` 里，由库把相邻玻璃
 * 合并成一整条、并在按下的格子上画折射光斑。这条路径内部是 `clickable(indication = null)`
 * + 按压高光采样，所以天生没有 Material 的矩形 ripple——之前手写的「白光环 + 描边」已删除。
 *
 * 布局：左「首页」/ 中「＋」/ 右「日历」。
 * 中央的「＋」不再垫一层蓝色圆底——那是 iOS 没有的东西，也是整条底栏里唯一的色块，
 * 和玻璃材质放在一起很突兀；现在它就是一个稍大的十字，颜色跟左右两格完全一致。
 * 排序已挪到首页标题区（见 `HomeHeader` 的排序胶囊）。
 */
private val BarHeight = 62.dp

/**
 * 强调色：直接用 Glasmio 的默认 accent（iOS 蓝）。
 *
 * 原来是朝霞橙 `#F2795C`——它配原来的蓝紫粉底还算协调，但整页换成库的默认中性色之后，
 * 橙色成了画面上唯一的暖色，反而比玻璃本身更抢眼，所以一并交给主题。
 */
@Composable
private fun createAccent(): Color = glassColors().accent

@Composable
fun HomeBottomBar(
    calendarActive: Boolean,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlasmioToolbarActionHost(
        modifier = modifier.fillMaxWidth(),
        style = GlasmioToolbarActionStyle.Merged,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarCell(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "首页",
                active = !calendarActive,
                onClick = onHome,
            ) { color -> HomeGlyph(color) }

            // 只有一点宽度差：中央格子比左右略窄，视觉上不抢，也不会显得凹陷
            BarCell(
                modifier = Modifier.width(72.dp).fillMaxHeight(),
                label = "新建",
                onClick = onCreate,
            ) { color -> PlusGlyph(color, glyphSize = 22.dp) }

            BarCell(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "日历",
                active = calendarActive,
                onClick = onCalendar,
            ) { color -> CalendarGlyph(color, glyphSize = 15.dp) }
        }
    }
}

/**
 * 一格：图标 + 文字，整格可点。
 *
 * `tint` 只在选中时传：它是叠在玻璃材质之上的一层极淡色纱（不是 Material 的选中背景），
 * 按压高光由材质绘制，因此选中格按下去依然有光。
 */
@Composable
private fun BarCell(
    modifier: Modifier,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit,
) {
    val primary = glassTextPrimary()
    GlassPanel(
        modifier = modifier,
        capsule = true,
        tint = if (active) createAccent().copy(alpha = 0.16f) else null,
        onClick = onClick,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            glyph(primary)
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = primary),
            )
        }
    }
}

/** 排序：左右两支反向箭头（上/下），不依赖字体里有没有 ↕ 字形。 */
@Composable
internal fun SortGlyph(color: Color, glyphSize: Dp = 17.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        // 左：向上
        drawLine(color, Offset(w * 0.32f, h * 0.88f), Offset(w * 0.32f, h * 0.12f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.16f, h * 0.31f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.48f, h * 0.31f), stroke, StrokeCap.Round)
        // 右：向下
        drawLine(color, Offset(w * 0.68f, h * 0.12f), Offset(w * 0.68f, h * 0.88f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.88f), Offset(w * 0.52f, h * 0.69f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.88f), Offset(w * 0.84f, h * 0.69f), stroke, StrokeCap.Round)
    }
}

/** 「＋」：两条等长线，比字体里的 + 更居中、笔画更稳。 */
@Composable
private fun PlusGlyph(color: Color, glyphSize: Dp = 20.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 1.8.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.12f), Offset(c, size.height * 0.88f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.12f, c), Offset(size.width * 0.88f, c), stroke, StrokeCap.Round)
    }
}

/**
 * 首页图标：屋顶 + 墙身，线条语言与日历图标一致（同样 1.2dp 描边、圆角）。
 */
@Composable
private fun HomeGlyph(color: Color, glyphSize: Dp = 16.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 1.2.dp.toPx()
        val w = size.width
        val h = size.height
        // 屋顶：从左墙顶斜到屋脊，再斜到右墙顶
        val eaveY = h * 0.46f
        val path = Path().apply {
            moveTo(w * 0.06f, eaveY)
            lineTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.94f, eaveY)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 墙身：两段竖线 + 地面，留一个门洞的缺口
        drawLine(color, Offset(w * 0.16f, eaveY), Offset(w * 0.16f, h * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.84f, eaveY), Offset(w * 0.84f, h * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.16f, h * 0.9f), Offset(w * 0.36f, h * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.9f), Offset(w * 0.84f, h * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.9f), Offset(w * 0.36f, h * 0.62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.9f), Offset(w * 0.64f, h * 0.62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.62f), Offset(w * 0.64f, h * 0.62f), stroke, StrokeCap.Round)
    }
}

/**
 * 日历图标：外框 + 顶栏横线 + 两个挂环。首页底栏与日历页头部共用同一份画法。
 */
@Composable
fun CalendarGlyph(color: Color, glyphSize: Dp = 13.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 1.2.dp.toPx()
        val corner = 2.dp.toPx()
        val top = this.size.height * 0.16f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = Size(this.size.width, this.size.height - top),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(0f, this.size.height * 0.4f),
            end = Offset(this.size.width, this.size.height * 0.4f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.3f, 0f),
            end = Offset(this.size.width * 0.3f, this.size.height * 0.26f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.7f, 0f),
            end = Offset(this.size.width * 0.7f, this.size.height * 0.26f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
