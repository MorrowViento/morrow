package com.morrowviento.morrow.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.event.EventCardModel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.LocalAccentPalette
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import androidx.compose.foundation.background

/**
 * 事件卡片：左侧标题与元信息，右侧天数徽章（带进度环）。
 *
 * 视觉靠 [GlassPanel] 提供模糊/折射，内部不画背景色，否则会盖住材质。
 *
 * @param dim 内容亮度（1 = 正常，0.55 = 已完成）。**不要**再用 `Modifier.alpha`
 *   在卡片外面套一层来做这件事：alpha < 1 会让 Compose 为这张卡单独开一块离屏层，
 *   而玻璃的按压放大会把内容画出自身边界——一旦被这层的边界裁掉，就成了
 *   「一按就缺一块角」。这里改成分别压暗文字、并在玻璃**内侧**垫一层同背景色的薄纱，
 *   观感同样是「退到后面」，但没有任何额外图层。
 */
@Composable
fun EventCardView(
    card: EventCardModel,
    modifier: Modifier = Modifier,
    dim: Float = 1f,
    onClick: () -> Unit = {},
) {
    val primary = glassTextPrimary().dimBy(dim)
    val secondary = glassTextSecondary().dimBy(dim)
    // 主题色薄纱：压得很淡（浅色 5% / 深色 9%），只让卡片本身带一点当前配色，
    // 不至于把玻璃透出来的壁纸盖成色块。
    val palette = LocalAccentPalette.current
    val dark = LocalGlassDark.current
    val accentTint = Color(palette.argb(dark))
        .copy(alpha = com.morrowviento.morrow.ui.glass.GlassOptics.accentAlpha(dark))
    // 与背景同色的薄纱：已完成的事件再叠一层，把整块面板的对比度压下去
    val scrim = if (dim >= 1f) {
        accentTint
    } else {
        glassColors().elevatedBackground.copy(alpha = (1f - dim) * 0.55f)
    }

    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        tint = scrim,
        // 按压光学由玻璃材质自己画（Glasmio 的 press highlight），没有 Material ripple
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.isPinned) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.55f)),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = card.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                        ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    // 元信息在模型里就算好了（见 EventCardModel.meta）：这里不再
                    // 每帧调用两次 String.format
                    text = card.meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = secondary),
                )
                card.remainingToday?.let { remaining ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "今日剩余 $remaining",
                        style = TextStyle(fontSize = 12.sp, color = primary.fade(0.75f)),
                    )
                }
                // 子任务：计数 + 一条细进度条。环已经在右侧徽章上，这里只补「拆了几步、
                // 做完几步」这类没法用角度表达的信息。
                if (card.hasChecklist) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${card.checklistLabel} 子任务",
                            style = TextStyle(fontSize = 11.sp, color = secondary),
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(primary.copy(alpha = 0.12f)),
                        ) {
                            if (card.checklistProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(card.checklistProgress)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(primary.copy(alpha = 0.55f)),
                                )
                            }
                        }
                    }
                }
                // 多维标记：`#报销 #出差`。放最下面一行，不抢标题和日期的位置
                if (card.tagLine.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = card.tagLine,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 11.sp, color = secondary),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            CountdownBadge(card, dim)
        }
    }
}

/**
 * 右侧天数徽章，外圈是进度环。
 *
 * 4.5.0 起所有卡片都画环：重复事件（生日、纪念日）按「上次 → 下次」的周期算，
 * 不再因为「重复」整类跳过——那恰好把最需要环的生日排除在外。
 */
@Composable
private fun CountdownBadge(card: EventCardModel, dim: Float) {
    val primary = glassTextPrimary().dimBy(dim)
    val secondary = glassTextSecondary().dimBy(dim)
    val isToday = card.dayDelta == 0

    Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = primary.fade(0.14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (card.progress > 0f) {
                drawArc(
                    color = primary.fade(0.8f),
                    startAngle = -90f,
                    sweepAngle = 360f * card.progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                // 持续事件进行中：徽章读的是「离结束还有几天」。
                // 若沿用「已经过了几天」，一场 9/1–9/5 的出差在 9/3 会显示「已过 2」，
                // 用户会以为这件事已经结束了。
                card.isInProgress -> {
                    Text(
                        text = if (card.daysLeftInSpan == 0) "今天" else "${card.daysLeftInSpan}",
                        style = TextStyle(
                            fontSize = if (card.daysLeftInSpan == 0) 15.sp else 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (card.daysLeftInSpan == 0) "结束" else "剩",
                        style = TextStyle(fontSize = 10.sp, color = secondary),
                    )
                }
                isToday -> {
                    Text(
                        text = "今天",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary),
                    )
                }
                else -> {
                    Text(
                        text = "${card.days}",
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = primary),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (card.isCountingUp) "已过" else "天",
                        style = TextStyle(fontSize = 10.sp, color = secondary),
                    )
                }
            }
        }
    }
}

/** 亮度和透明度一起收：`alpha * factor`，而不是把 alpha 直接改成 factor。 */
private fun Color.dimBy(dim: Float): Color =
    if (dim >= 1f) this else copy(alpha = alpha * dim)

/** 在已压暗的基色上再按比例降一档（进度环、副标题用）。 */
private fun Color.fade(factor: Float): Color = copy(alpha = alpha * factor)
