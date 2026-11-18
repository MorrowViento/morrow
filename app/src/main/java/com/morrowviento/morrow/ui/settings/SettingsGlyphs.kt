package com.morrowviento.morrow.ui.settings

/** 设置页的 Canvas 图标与缩略图：主题/玻璃厚度图标、配色色板、背景缩略条。 */
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.settings.AccentColor
import com.morrowviento.morrow.data.settings.AccentPalette
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI

/**
 * 配色方案色板：七套预设 + 一档「从背景图取色」。
 *
 * 每个圆点是这套配色的**缩影**：中心是强调色，边缘往它自己的壁纸色斑上过渡。
 * 上一版是纯实心色点，只能看出「按钮会变蓝还是变紫」，看不出整页会变成什么样；
 * 加上壁纸色之后，七套方案的冷暖差异一眼能分出来。
 *
 * 第八颗（取色）在没选图时画成虚线圈：它此刻不可用，但**位置要占住**——
 * 用户的预期是「这里还有一个来源」，而不是让人先去发现有没有图。
 *
 * 选中的那圈加一道描边——不用「打勾」：色板上一眼要读的是**颜色本身**，
 * 勾号会把中间那块色盖掉一小半。
 *
 * 点一下就写设置，没有「确定」：换色是即时可见的（整页背景立刻变），
 * 多一步确认反而让用户先离开设置页才能看到效果。
 */
@Composable
internal fun AccentSwatches(
    selected: AccentPalette,
    autoSelected: Boolean,
    autoPreview: AccentPalette?,
    isDark: Boolean,
    onSelected: (AccentColor) -> Unit,
    onAutoSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentColor.entries.forEach { color ->
            val palette = color.palette
            SwatchDot(
                // 用「冷」那团色斑做边缘：它在每套方案里都是最接近背景基调的一块
                center = Color(palette.argb(isDark)),
                rim = Color(palette.blobs(isDark)[1]),
                active = !autoSelected && selected.id == color.prefValue,
                onClick = { onSelected(color) },
            )
        }
        SwatchDot(
            center = autoPreview?.let { Color(it.argb(isDark)) }
                ?: glassTextSecondary().copy(alpha = 0.30f),
            rim = autoPreview?.let { Color(it.blobs(isDark)[1]) } ?: Color.Transparent,
            active = autoSelected,
            // 没有图 = 这档暂时不可用：画成虚线圈，点它会给一句「先选一张图」的提示
            dashed = autoPreview == null,
            onClick = onAutoSelected,
        )
    }
}

/** 单个色点：径向渐变圆 + 选中描边（虚线表示「暂不可用」）。 */
@Composable
private fun SwatchDot(
    center: Color,
    rim: Color,
    active: Boolean,
    dashed: Boolean = false,
    onClick: () -> Unit,
) {
    val ring = glassTextPrimary()
    Canvas(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        val stroke = 1.6.dp.toPx()
        val radius = size.minDimension / 2f
        if (active) {
            // 描边贴着边缘画，圆点本身不动——换色时整排不会跳
            drawCircle(
                color = ring.copy(alpha = 0.80f),
                radius = radius - stroke / 2f,
                style = Stroke(width = stroke),
            )
        }
        val middle = Offset(size.width / 2f, size.height / 2f)
        if (dashed) {
            drawCircle(
                color = center,
                radius = radius - stroke,
                center = middle,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
                ),
            )
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(center, center, rim),
                    center = middle,
                    radius = radius * 0.68f,
                ),
                radius = radius * 0.68f,
            )
        }
    }
}

/**
 * 背景图缩略条。
 *
 * 用一整条而不是一个小方块：用户要判断的是「这张图当整页底色好不好看」，
 * 一片 44×44 的缩略图看不出构图与明暗。112dp 的高度够读出主体与冷暖。
 *
 * 解码复用 [BackdropImage] 的进程内缓存，与 `GlassBackdrop` 共用同一张位图，
 * 不会多解一份。
 */
@Composable
internal fun BackdropThumbnail(fileName: String?) {
    val context = LocalContext.current
    var bitmap by remember(fileName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(fileName) {
        bitmap = if (fileName == null) {
            null
        } else {
            withContext(Dispatchers.IO) { BackdropImage.load(context, fileName) }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(shape)
            .background(glassColors().elevatedBackground.copy(alpha = 0.45f)),
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "当前背景图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 玻璃厚度的四个图标（通透 / 轻盈 / 标准 / 厚重）——**四个圆环**。
 *
 * 圆环读作「这层玻璃的横截面」：环越细越虚 = 玻璃越薄，越粗越实 = 越厚。
 * 上一版画的是「玻璃卡压住两个圆点」，但卡片在 19dp 里只有 9dp 高，
 * 前两档的面几乎全透明，四格看上去只差一点点灰度；圆环把「清晰度」直接画成了
 * **线本身的虚实**，一眼就能分：
 *
 * - 通透：稀疏虚线环（8 段），最细最淡，光几乎不被拦；
 * - 轻盈：细密虚线环（16 段），虚但看得见走势；
 * - 标准：实线环；
 * - 厚重：粗实线外环 + 一圈内环，像一片有厚度的玻璃边。
 */
@Composable
internal fun FrostGlyph(level: GlassFrostLevel, size: Dp = 20.dp, alpha: Float = 1f) {
    val ink = glassTextPrimary()
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = side / 2f - 1.4.dp.toPx()
        when (level) {
            GlassFrostLevel.CLEAR -> drawDashedRing(
                center, radius, ink.copy(alpha = alpha * 0.42f), 1.0.dp.toPx(), 8,
            )
            GlassFrostLevel.LIGHT -> drawDashedRing(
                center, radius, ink.copy(alpha = alpha * 0.62f), 1.3.dp.toPx(), 16,
            )
            GlassFrostLevel.STANDARD -> drawCircle(
                ink.copy(alpha = alpha * 0.82f), radius, center, style = Stroke(1.8.dp.toPx()),
            )
            GlassFrostLevel.HEAVY -> {
                drawCircle(
                    ink.copy(alpha = alpha * 0.95f), radius, center, style = Stroke(2.6.dp.toPx()),
                )
                drawCircle(
                    ink.copy(alpha = alpha * 0.30f), radius * 0.50f, center,
                    style = Stroke(1.1.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 虚线圆环：dash 长度按周长均分，保证首尾刚好接上。
 *
 * 用固定 dash 值会在收口处露出一段半长不短的缺口——周长不是 (dash + gap) 的整数倍。
 */
private fun DrawScope.drawDashedRing(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Float,
    segments: Int,
) {
    val segment = (2f * PI.toFloat() * radius) / segments
    val effect = PathEffect.dashPathEffect(floatArrayOf(segment * 0.58f, segment * 0.42f))
    drawCircle(color, radius, center, style = Stroke(width = stroke, pathEffect = effect))
}

/**
 * 主题的三个图标（自动 / 浅色 / 深色）。
 *
 * 用 Canvas 手画而不是引 SF Symbols：库里的 `SfSymbol` 只有 House / Clock / Gearshape
 * 这一套，没有太阳与月亮，硬凑会认不出来。
 */
@Composable
internal fun ThemeGlyph(mode: ThemeMode, size: Dp = 18.dp, alpha: Float = 1f) {
    val color = glassTextPrimary().copy(alpha = alpha)
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val hair = 1.5.dp.toPx()
        when (mode) {
            ThemeMode.SYSTEM -> {
                drawCircle(color, radius = r * 0.80f, style = Stroke(width = hair))
                val half = Path().apply {
                    moveTo(c.x, c.y - r * 0.80f)
                    arcTo(Rect(c, r * 0.80f), -90f, 180f, true)
                    close()
                }
                drawPath(half, color)
            }

            ThemeMode.LIGHT -> {
                drawCircle(color, radius = r * 0.36f)
                repeat(8) { index ->
                    rotate(index * 45f, pivot = c) {
                        drawLine(
                            color = color,
                            start = Offset(c.x, c.y - r * 0.62f),
                            end = Offset(c.x, c.y - r * 0.92f),
                            strokeWidth = hair,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            ThemeMode.DARK -> {
                val disc = Path().apply { addOval(Rect(c, r * 0.86f)) }
                val bite = Path().apply {
                    addOval(Rect(Offset(c.x + r * 0.44f, c.y - r * 0.34f), r * 0.76f))
                }
                val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
                drawPath(crescent, color)
            }
        }
    }
}
