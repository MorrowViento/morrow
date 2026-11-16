package com.morrowviento.morrow.ui.event

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import java.time.format.DateTimeFormatter

/**
 * 事件详情（M2c）。
 *
 * 与其他浮层一致：同页面内浮层，保证玻璃材质仍能采样到首页背景。
 * 四个操作（完成/置顶/编辑/删除）里，删除与状态切换都会进入 6 秒撤销队列。
 */
@Composable
fun EventDetailScreen(
    card: EventCardModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleCompleted: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
) {
    // 与新建/编辑面板同一套 UIKit 弹层：下拉关闭、滚动到顶继续上拉展开
    GlassSheet(title = "事件详情", onDismiss = onDismiss) { dismiss ->
        Column(Modifier.fillMaxSize()) {
            DetailHeader(dismiss, onEdit)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
                ) {
                    Text(
                        text = card.title,
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = countdownHeadline(card),
                        style = TextStyle(fontSize = 14.sp, color = glassTextSecondary()),
                    )
                    card.remainingToday?.let { remaining ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "今日剩余 $remaining",
                            style = TextStyle(fontSize = 13.sp, color = glassTextPrimary().copy(alpha = 0.75f)),
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    InfoRow("目标", formatTarget(card))
                    InfoRow("分类", card.category)
                    InfoRow("类型", card.kind.label)
                    InfoRow("重复", card.repeatType.label)
                    InfoRow("创建", card.createdAt.format(CREATED_AT_FORMAT))
                    if (card.isCompleted) InfoRow("状态", "已完成")

                    if (card.note.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "备注",
                            style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = card.note,
                            style = TextStyle(fontSize = 14.sp, color = glassTextPrimary()),
                        )
                    }

                    Spacer(Modifier.height(26.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DetailActionIcon(pin = true, pinned = card.isPinned, onClick = onTogglePinned)
                        if (card.primaryAction != com.morrowviento.morrow.domain.model.EventPrimaryAction.NONE) {
                            com.morrowviento.morrow.ui.glass.GlassTextButton(
                                text = card.primaryAction.label,
                                onClick = onToggleCompleted,
                                modifier = Modifier.weight(1f).height(56.dp),
                                active = true,
                                compact = false,
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        DetailActionIcon(pin = false, onClick = onDelete)
                    }
                }
        }
    }
}

@Composable
private fun DetailHeader(onDismiss: () -> Unit, onEdit: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "关闭",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 14.sp, color = secondary),
        )
        Text(
            text = "详情",
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = primary),
        )
        Text(
            text = "编辑",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primary),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.32f),
            style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.68f),
            style = TextStyle(fontSize = 14.sp, color = glassTextPrimary()),
        )
    }
}

@Composable
private fun DetailActionIcon(pin: Boolean, pinned: Boolean = false, onClick: () -> Unit) {
    val color = if (pin) glassTextPrimary() else Color(0xFFE65B64)
    val description = if (pin) { if (pinned) "取消置顶" else "置顶" } else "删除"
    com.morrowviento.morrow.ui.glass.GlassCirclePanel(
        modifier = Modifier.size(52.dp).semantics {
            contentDescription = description
            if (pin) selected = pinned
        },
        prominent = pin && pinned,
        onClick = onClick,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.8.dp.toPx()
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
                color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke,
            )
            if (pin) {
                line(.3f, .18f, .7f, .18f)
                line(.36f, .18f, .36f, .48f)
                line(.64f, .18f, .64f, .48f)
                line(.36f, .48f, .23f, .62f)
                line(.64f, .48f, .77f, .62f)
                line(.23f, .62f, .77f, .62f)
                line(.5f, .62f, .5f, .9f)
            } else {
                line(.2f, .28f, .8f, .28f)
                line(.38f, .16f, .62f, .16f)
                line(.38f, .16f, .38f, .28f)
                line(.62f, .16f, .62f, .28f)
                line(.28f, .28f, .32f, .84f)
                line(.72f, .28f, .68f, .84f)
                line(.32f, .84f, .68f, .84f)
                line(.43f, .4f, .43f, .72f)
                line(.57f, .4f, .57f, .72f)
            }
        }
    }
}

private val CREATED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val TARGET_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

private fun formatTarget(card: EventCardModel): String =
    if (card.isAllDay) {
        // 全天事件只显示日期，避免"00:00"造成误解
        card.target.toLocalDate().format(CREATED_AT_FORMAT)
    } else {
        card.target.format(TARGET_FORMAT)
    }

private fun countdownHeadline(card: EventCardModel): String = when {
    card.dayDelta == 0 -> "就是今天"
    card.dayDelta > 0 -> "还有 ${card.dayDelta} 天"
    else -> "已过去 ${-card.dayDelta} 天"
}
