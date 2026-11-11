package com.jiuxina.ying.ui.event

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.domain.model.CountDirection
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.ui.component.ChipRow
import com.jiuxina.ying.ui.component.ChoiceChip
import com.jiuxina.ying.ui.component.GlassTextField
import com.jiuxina.ying.ui.component.SectionTitle
import com.jiuxina.ying.ui.component.WheelDateTimePicker
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary

/**
 * 新建/编辑事件面板。
 *
 * 做成同页面内的浮层而不是 ModalBottomSheet：玻璃材质依赖 `GlassBackdropHost` 的采样上下文，
 * 独立 Dialog/Sheet 窗口会把 backdrop 切断，材质直接失效。
 */
@Composable
fun EventEditSheet(
    initial: EventFormState,
    categories: List<String>,
    onManageCategories: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (EventFormState) -> Unit,
) {
    var form by remember(initial.id) { mutableStateOf(initial) }
    var showTitleError by remember { mutableStateOf(false) }
    var reminderHint by remember { mutableStateOf<String?>(null) }
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val isEditing = form.id != null

    // 分类 chips：仓库列表 + 当前值。当前值可能是旧数据里已不在列表中的分类，
    // 也必须能显示与保留，否则一打开表单就会被悄悄改掉分类。
    val categoryChips = remember(categories, form.category) {
        (categories + form.category).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.94f),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(
                    title = if (isEditing) "编辑事件" else "新建事件",
                    onCancel = onDismiss,
                    onSave = {
                        val errors = form.validate()
                        showTitleError = errors.title != null
                        if (errors.isValid) onSave(form)
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GlassTextField(
                        value = form.title,
                        onValueChange = { form = form.copy(title = it) },
                        placeholder = "标题，例如「春节回家」",
                        error = if (showTitleError) "请输入标题" else null,
                    )

                    WheelDateTimePicker(
                        date = form.date,
                        time = form.time,
                        isAllDay = form.isAllDay,
                        onDateChange = { form = form.withDate(it) },
                        onTimeChange = { form = form.copy(time = it) },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("全天", form.isAllDay) { form = form.withAllDay(!form.isAllDay) }
                        ChoiceChip(form.direction.label(), false) {
                            form = form.copy(direction = form.direction.next())
                        }
                        ChoiceChip("每年重复", form.repeatYearly) {
                            form = form.copy(repeatYearly = !form.repeatYearly)
                        }
                    }

                    QuickDateRow(onPick = { form = form.withDate(it) })

                    SectionTitle("分类")
                    ChipRow {
                        categoryChips.forEach { category ->
                            ChoiceChip(category, form.category == category) {
                                form = form.copy(category = category)
                            }
                        }
                        ChoiceChip("管理分类…", false, onClick = onManageCategories)
                    }

                    SectionTitle("提醒（最多 ${EventReminder.MAX_COUNT} 条）")
                    ChipRow {
                        EventFormState.REMINDER_PRESETS.forEach { minutes ->
                            val selected = form.reminders.any { it.minutesBefore == minutes }
                            ChoiceChip(EventReminder.labelForMinutes(minutes), selected) {
                                val next = form.toggleReminder(minutes)
                                reminderHint = if (next.reminders.size == form.reminders.size && !selected) {
                                    "最多 ${EventReminder.MAX_COUNT} 条提醒"
                                } else {
                                    null
                                }
                                form = next
                            }
                        }
                    }
                    reminderHint?.let {
                        Text(it, style = TextStyle(fontSize = 11.sp, color = secondary))
                    }

                    SectionTitle("备注")
                    GlassTextField(
                        value = form.note,
                        onValueChange = { form = form.copy(note = it) },
                        placeholder = "可不填",
                        singleLine = false,
                        minHeight = 88.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, onCancel: () -> Unit, onSave: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "取消",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 14.sp, color = secondary),
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = primary),
        )
        Text(
            text = "保存",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSave)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primary),
        )
    }
}

@Composable
private fun QuickDateRow(onPick: (java.time.LocalDate) -> Unit) {
    ChipRow {
        EventFormState.quickDates().forEach { (label, date) ->
            ChoiceChip(label, false) { onPick(date) }
        }
    }
}

private fun CountDirection.label(): String = when (this) {
    CountDirection.AUTO -> "自动方向"
    CountDirection.COUNTDOWN -> "只倒数"
    CountDirection.COUNTUP -> "只正数"
}

private fun CountDirection.next(): CountDirection {
    val all = CountDirection.entries
    return all[(ordinal + 1) % all.size]
}
