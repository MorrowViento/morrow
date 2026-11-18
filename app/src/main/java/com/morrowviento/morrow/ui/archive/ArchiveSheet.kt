package com.morrowviento.morrow.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.event.EventActions
import com.morrowviento.morrow.ui.event.EventCardModel
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.GlassTextButton
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.SheetHeader
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

/**
 * 归档箱。
 *
 * 归档与「已完成」是两条不同的状态线，这里用一句说明把它讲清楚：
 * 已完成 = 事情办到了，留在列表里给你看成绩；归档 = 从日常视线里收起来
 * （不做了、过气了、暂时搁置），不提醒、不上日历、也不计入已完成。
 * 所以归档箱是**第三段**列表，不是「已完成」的别名。
 */
@Composable
fun ArchiveSheet(
    cards: List<EventCardModel>,
    actions: EventActions,
    onDismiss: () -> Unit,
) {
    GlassSheet(title = "归档箱", onDismiss = onDismiss) { dismiss ->
        Column(Modifier.fillMaxSize()) {
            SheetHeader(title = "归档箱", onCancel = dismiss)
            Text(
                text = "归档只是把它们从列表里收起来，不等于完成。这里的事件不提醒、不上日历、也不计入已完成。",
                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
            )
            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "归档箱是空的",
                        style = TextStyle(fontSize = 13.sp, color = glassTextSecondary()),
                    )
                }
                return@Column
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    ArchiveRow(
                        card = card,
                        onRestore = { actions.unarchive(card.id) },
                        onDelete = { actions.delete(card.id) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共 ${cards.size} 个",
                    style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                    modifier = Modifier.weight(1f),
                )
                GlassTextButton(
                    text = "清空归档箱",
                    onClick = { actions.clearArchived() },
                    compact = false,
                )
            }
        }
    }
}

@Composable
private fun ArchiveRow(
    card: EventCardModel,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val dark = LocalGlassDark.current
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(glassColors().elevatedBackground.copy(alpha = if (dark) 0.55f else 0.65f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = card.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(card.category)
                    card.archivedLabel?.let { append(" · 归档于 "); append(it) }
                    if (card.isCompleted) append(" · 已完成")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 11.sp, color = secondary),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "恢复",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRestore)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = primary),
            )
            Text(
                text = "删除",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = TextStyle(fontSize = 12.sp, color = secondary),
            )
        }
    }
}
