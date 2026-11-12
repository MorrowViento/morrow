package com.morrowviento.morrow.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.event.EventCardModel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import androidx.compose.foundation.background

/**
 * 事件卡片：左侧标题与元信息，右侧天数徽章（带进度环）。
 *
 * 视觉靠 [GlassPanel] 提供模糊/折射，内部不画背景色，否则会盖住材质。
 */
@Composable
fun EventCardView(
    card: EventCardModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()

    GlassPanel(
        modifier = modifier.fillMaxWidth(),
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
                    text = metaLine(card),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = secondary),
                )
                card.remainingToday?.let { remaining ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "今日剩余 $remaining",
                        style = TextStyle(fontSize = 12.sp, color = primary.copy(alpha = 0.75f)),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            CountdownBadge(card)
        }
    }
}

/** 右侧天数徽章；非重复事件在外圈画进度环，重复事件不画（进度无意义）。 */
@Composable
private fun CountdownBadge(card: EventCardModel) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val isToday = card.dayDelta == 0

    Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
        if (!card.repeatsYearly) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = primary.copy(alpha = 0.14f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (card.progress > 0f) {
                    drawArc(
                        color = primary.copy(alpha = 0.8f),
                        startAngle = -90f,
                        sweepAngle = 360f * card.progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isToday) {
                Text(
                    text = "今天",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
            } else {
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

/** 元信息行：分类 · 日期 · 重复/全天标记。 */
private fun metaLine(card: EventCardModel): String {
    val date = "%d月%d日".format(card.target.monthValue, card.target.dayOfMonth)
    val parts = mutableListOf(card.category, if (card.repeatsYearly) "每年 $date" else date)
    if (card.isAllDay) parts += "全天"
    else parts += "%02d:%02d".format(card.target.hour, card.target.minute)
    return parts.joinToString(" · ")
}
