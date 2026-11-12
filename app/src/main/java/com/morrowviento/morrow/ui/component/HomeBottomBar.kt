package com.morrowviento.morrow.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.glassPanelShape
import com.morrowviento.morrow.ui.glass.glassPressFeedback
import com.morrowviento.morrow.ui.glass.glassPressHalo
import com.morrowviento.morrow.ui.glass.glassPressable
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.rememberGlassPress

/**
 * 首页底部玻璃栏（对齐 iOS / 参考图里的底栏）：
 * 左「排序」、中「＋」、右「日历」，整条是一块胶囊玻璃。
 *
 * 为什么把这三个按钮从标题栏挪下来：
 * 标题栏三颗按钮挤在 34sp 大标题右边，点「排序」时 Material ripple 会在胶囊里画出
 * 一个方形高亮框（ripple 只按布局边界裁剪，不认玻璃的圆角）。底栏本来就该放导航/操作，
 * 挪下来之后标题区只剩「朝夕」+ 更多，视觉也松了。
 *
 * 底栏不吸在屏幕最底：外面留 14dp，像 iOS 那样浮在内容上，滚动时列表从它下面穿过去。
 */
private val BarHeight = 62.dp

/** 中央「＋」的强调色：朝霞橙，与启动图标的渐变同色系。 */
private val CreateAccent = Color(0xFFF2795C)

@Composable
fun HomeBottomBar(
    sortLabel: String,
    onCreate: () -> Unit,
    onCalendar: () -> Unit,
    onCycleSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight),
        capsule = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBarItem(
                modifier = Modifier.weight(1f),
                label = sortLabel,
                contentDescription = "排序：$sortLabel，点击切换",
                onClick = onCycleSort,
            ) { color -> SortGlyph(color) }

            GlassBarCreateButton(onClick = onCreate)

            GlassBarItem(
                modifier = Modifier.weight(1f),
                label = "日历",
                contentDescription = "打开日历",
                onClick = onCalendar,
            ) { color -> CalendarGlyph(color, glyphSize = 15.dp) }
        }
    }
}

/**
 * 底栏格子（内部共享，日历页复用）。
 *
 * 结构分两层：外圈只负责**光晕**（不裁剪，按下时溢到格子的圆角之外），内圈负责缩放上浮
 * 与手势。`glassPressHalo` 用 `drawBehind` 画，因此不受内圈 `clip` 影响——把两层合成一个
 * Modifier 链，光晕会被裁成一条硬边。
 */
@Composable
internal fun GlassBarItem(
    modifier: Modifier,
    label: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit,
) {
    val press = rememberGlassPress()
    val primary = glassTextPrimary()
    val itemShape = glassPanelShape(capsule = true)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 光晕用**圆形**而不是格子自身的胶囊形：格子是 130×48 的宽条，按轮廓外扩会变成
        // 两侧各竖一条亮边；圆形光晕才是「手指下面亮起一个圆」，也与下面的圆形径向渐变一致。
        Box(
            Modifier
                .matchParentSize()
                .glassPressHalo(press, CircleShape, spread = 0.5f),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(itemShape)
                .glassPressFeedback(press, scale = 0.05f, lift = 1.5.dp)
                .glassPressable(
                    press,
                    contentDescription = contentDescription,
                    enabled = enabled,
                    onClick = onClick,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            glyph(if (enabled) primary else primary.copy(alpha = 0.42f))
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) primary else primary.copy(alpha = 0.42f),
                ),
            )
        }
    }
}

/** 中央「＋」：强调色玻璃圆钮，比两侧图标高一档，充当主操作。 */
@Composable
internal fun GlassBarCreateButton(onClick: () -> Unit) {
    val press = rememberGlassPress()
    Box(
        modifier = Modifier.width(74.dp).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        // 与圆钮同尺寸、同心的空 Box，只用来画光晕：外扩 50% 才够「一圈大光」
        Box(
            Modifier
                .size(46.dp)
                .glassPressHalo(press, CircleShape, spread = 0.5f, intensity = 1.15f),
        )
        GlassCirclePanel(
            modifier = Modifier
                .size(46.dp)
                .glassPressFeedback(press, scale = 0.08f, lift = 2.dp, shape = CircleShape),
            tint = CreateAccent.copy(alpha = 0.62f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .glassPressable(press, contentDescription = "新建事件", onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                PlusGlyph(Color.White)
            }
        }
    }
}

/** 排序：左右两支反向箭头（上/下），不依赖字体里有没有 ↕ 字形。 */
@Composable
private fun SortGlyph(color: Color) {
    Canvas(Modifier.size(17.dp)) {
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
private fun PlusGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val c = size.width / 2f
        drawLine(color, Offset(c, size.height * 0.12f), Offset(c, size.height * 0.88f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.12f, c), Offset(size.width * 0.88f, c), stroke, StrokeCap.Round)
    }
}

/**
 * 日历图标：外框 + 顶栏横线 + 两个挂环。
 *
 * 首页标题栏、底栏、日历页共用同一份画法，避免三处各画一个版本慢慢跑偏。
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
