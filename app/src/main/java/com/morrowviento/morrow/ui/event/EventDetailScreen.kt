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
                    InfoRow("重复", if (card.repeatsYearly) "每年" else "不重复")
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionChip(
                            label = if (card.isCompleted) "恢复为进行中" else "标记完成",
                            emphasized = !card.isCompleted,
                            onClick = onToggleCompleted,
                        )
                        ActionChip(
                            label = if (card.isPinned) "取消置顶" else "置顶",
                            emphasized = false,
                            onClick = onTogglePinned,
                        )
                        ActionChip(
                            label = "删除",
                            emphasized = false,
                            danger = true,
                            onClick = onDelete,
                        )
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
private fun ActionChip(
    label: String,
    emphasized: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val contentColor = when {
        danger -> Color(0xFFFF7A7A)
        emphasized -> primary
        else -> secondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = if (emphasized) 0.16f else 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
            ),
        )
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
