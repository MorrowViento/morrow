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
import androidx.compose.runtime.LaunchedEffect
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
import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.ui.component.ChipRow
import com.morrowviento.morrow.ui.component.ChoiceChip
import com.morrowviento.morrow.ui.component.GlassTextField
import com.morrowviento.morrow.ui.component.SectionTitle
import com.morrowviento.morrow.ui.component.WheelDateTimePicker
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.SheetHeader
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

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
    /**
     * 分类改名 / 删除迁移的映射（旧名 → 新名，可链式）。
     *
     * 表单状态是面板自己持有的，父级改不到，只能把「分类名变了」这件事传进来，
     * 否则用户重命名分类后这里仍拿着旧名，保存时又把旧名写回去。
     */
    categoryAliases: Map<String, String> = emptyMap(),
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

    // 分类在管理页被改名 / 删除后，选中项跟着走；只改与旧名完全相同的值，
    // 用户随后手动点选的分类不会被覆盖。
    LaunchedEffect(categoryAliases) {
        form = form.withCategoryAlias(categoryAliases)
    }

    // 分类 chips：仓库列表 + 当前值。当前值可能是旧数据里已不在列表中的分类，
    // 也必须能显示与保留，否则一打开表单就会被悄悄改掉分类。
    val categoryChips = remember(categories, form.category) {
        (categories + form.category).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    // UIKit 式底部弹层：上滑入场、下拉关闭，滚动到顶继续上拉会展开到全屏
    GlassSheet(
        title = if (isEditing) "编辑事件" else "新建事件",
        onDismiss = onDismiss,
    ) { dismiss ->
        Column(Modifier.fillMaxSize()) {
            SheetHeader(
                title = if (isEditing) "编辑事件" else "新建事件",
                onCancel = dismiss,
                confirmLabel = "保存",
                onConfirm = {
                    val errors = form.validate()
                    showTitleError = errors.title != null
                    if (errors.isValid) {
                        onSave(form)
                        dismiss()
                    }
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
