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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.PageChrome
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.glassAccent
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
    /** 归档 / 取消归档。归档把事件移出首页与日历，但保留数据；与「完成」不是一回事。 */
    onArchive: () -> Unit = {},
    /** 勾选 / 取消一条子任务。 */
    onToggleChecklist: (String) -> Unit = {},
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
                    card.spanLabel?.let { InfoRow("持续", it) }
                    card.durationLabel?.let { InfoRow("时长", it) }
                    InfoRow("分类", card.category)
                    if (card.tagLine.isNotBlank()) InfoRow("标签", card.tagLine)
                    InfoRow("类型", card.kind.label)
                    InfoRow("重复", card.repeatLabel)
                    InfoRow("创建", card.createdAt.format(CREATED_AT_FORMAT))
                    if (card.isCompleted) InfoRow("状态", "已完成")
                    card.archivedLabel?.let { InfoRow("归档", it) }

                    // 纪念日里程碑：只报「第几天到了没有」，不列具体公历日期——
                    // 从起始日+天数就能算出来，而多一列日期会把这屏挤成台账
                    if (card.hasMilestones) {
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "里程碑",
                                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = card.elapsedLabel ?: "",
                                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        val elapsed = card.elapsedDays ?: 0L
                        card.milestones.forEach { day ->
                            val remain = day - elapsed
                            InfoRow(
                                label = "第 $day 天",
                                value = when {
                                    remain < 0 -> "已过"
                                    remain == 0L -> "就是今天"
                                    else -> "还有 $remain 天"
                                },
                            )
                        }
                    }

                    // 子任务：能直接勾，不用进编辑面板。全部勾完也不会自动标完成——
                    // 那一步仍要用户自己点「标记完成」，否则一条「编辑标题」就能把完成态吃掉。
                    if (card.hasChecklist) {
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "子任务",
                                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${card.checklistDone}/${card.checklistTotal}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = glassTextPrimary(),
                                ),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        card.checklist.forEach { item ->
                            DetailChecklistRow(
                                item = item,
                                dim = if (card.isCompleted) 0.7f else 1f,
                                onToggle = { onToggleChecklist(item.id) },
                            )
                        }
                    }

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
                                modifier = Modifier.weight(1f).height(PageChrome.ActionButtonSize),
                                active = true,
                                compact = false,
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        // 归档与删除相邻但语义完全不同：归档保留数据、可原样恢复，
                        // 删除进 6 秒撤销窗口后即消失。归档与置顶/删除一样是 56dp 圆钮，
                        // 不再塞一个 34dp 的文字按钮进来（之前它比旁边三个按钮矮一截）。
                        DetailArchiveIcon(archived = card.isArchived, onClick = onArchive)
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
            .padding(
                start = PageChrome.HeaderHorizontal,
                end = PageChrome.HeaderHorizontal,
                top = PageChrome.HeaderTop,
                bottom = PageChrome.HeaderBottom,
            ),
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

/**
 * 归档 / 恢复圆钮。与置顶、删除同尺寸同材质，只是字形不同：
 * 未归档画「盒子 + 向下箭头」（收进抽屉），已归档画「盒子 + 向上箭头」（拿出来）。
 */
@Composable
private fun DetailArchiveIcon(archived: Boolean, onClick: () -> Unit) {
    val color = glassTextPrimary()
    val description = if (archived) "恢复" else "归档"
    com.morrowviento.morrow.ui.glass.GlassCirclePanel(
        modifier = Modifier.size(PageChrome.ActionButtonSize).semantics {
            contentDescription = description
            if (archived) selected = true
        },
        prominent = archived,
        onClick = onClick,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.8.dp.toPx()
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
                color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke,
            )
            // 盒子（抽屉）：顶部开口，左右壁与底部
            line(.2f, .4f, .8f, .4f)
            line(.3f, .4f, .3f, .82f)
            line(.7f, .4f, .7f, .82f)
            line(.3f, .82f, .7f, .82f)
            if (archived) {
                // 向上箭头：从盒子里拿出来
                line(.5f, .56f, .5f, .12f)
                line(.38f, .24f, .5f, .12f)
                line(.62f, .24f, .5f, .12f)
            } else {
                // 向下箭头：收进盒子里
                line(.5f, .12f, .5f, .6f)
                line(.38f, .48f, .5f, .6f)
                line(.62f, .48f, .5f, .6f)
            }
        }
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
        modifier = Modifier.size(PageChrome.ActionButtonSize).semantics {
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
    card.isInProgress -> if (card.daysLeftInSpan == 0) {
        "今天是最后一天"
    } else {
        "进行中 · 还有 ${card.daysLeftInSpan} 天"
    }
    card.dayDelta == 0 -> "就是今天"
    card.dayDelta > 0 -> "还有 ${card.dayDelta} 天"
    else -> "已过去 ${-card.dayDelta} 天"
}

/**
 * 详情页的一条子任务：整行可点（勾选/取消），勾上后标题加删除线并转次要色。
 *
 * 与编辑面板里的 `ChecklistEditRow` 视觉一致，区别是这里**没有删除**——
 * 详情页只做「推进度」，增删改成走编辑面板，避免误删。
 */
@Composable
private fun DetailChecklistRow(
    item: com.morrowviento.morrow.domain.model.ChecklistItem,
    dim: Float,
    onToggle: () -> Unit,
) {
    val accent = glassAccent()
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .width(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (item.done) accent.copy(alpha = 0.85f) else primary.copy(alpha = 0.10f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) {
                Text(
                    text = "✓",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontSize = 14.sp,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            ),
            color = (if (item.done) secondary else primary).let { if (dim >= 1f) it else it.copy(alpha = it.alpha * dim) },
        )
    }
}
