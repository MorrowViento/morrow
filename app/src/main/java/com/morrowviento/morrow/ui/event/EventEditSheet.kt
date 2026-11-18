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
import com.morrowviento.morrow.ui.component.Text
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
    /** 全库已用过的标签，用来「点一下就有」，不用每次手打。 */
    tagSuggestions: List<String> = emptyList(),
    onManageCategories: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (EventFormState) -> Unit,
) {
    var form by remember(initial.id) { mutableStateOf(initial) }
    var showTitleError by remember { mutableStateOf(false) }
    var reminderHint by remember { mutableStateOf<String?>(null) }
    /** 自定义提醒的输入框内容（纯数字分钟数）。 */
    var customReminder by remember { mutableStateOf("") }
    /** 标签输入框内容。 */
    var tagInput by remember { mutableStateOf("") }
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
                    }

                    // 持续事件：单日 / 跨多天。结束日展开一个只含年月日的滚轮，
                    // 与开始日共用同一个组件，避免再维护一份日期选择逻辑。
                    SectionTitle("持续")
                    ChipRow {
                        ChoiceChip("单日", form.endDate == null) { form = form.withEndDate(null) }
                        ChoiceChip(
                            label = form.endDate?.let { "到 ${it.monthValue} 月 ${it.dayOfMonth} 日" } ?: "多天",
                            selected = form.endDate != null,
                        ) {
                            form = form.withEndDate(form.endDate ?: form.date.plusDays(1))
                        }
                    }
                    form.endDate?.let { end ->
                        WheelDateTimePicker(
                            date = end,
                            time = form.endTime ?: form.time,
                            // 结束日跟随开始日的「全天 / 具体时间」：上面能选时分，下面也能选；
                            // 上面是全天，下面也只选日期，不再硬编码成全天
                            isAllDay = form.isAllDay,
                            // 滚到开始日之前就夹回「开始日 + 1」：否则 withEndDate 会把
                            // 结束日归零、滚轮当场消失，手指还在滑
                            onDateChange = {
                                form = form.withEndDate(if (it <= form.date) form.date.plusDays(1) else it)
                            },
                            onTimeChange = { form = form.withEndTime(it) },
                        )
                        val span = java.time.temporal.ChronoUnit.DAYS.between(form.date, end) + 1
                        Text(
                            text = "共 $span 天（提醒仍按第一天）",
                            style = TextStyle(fontSize = 11.sp, color = secondary),
                        )
                    }

                    SectionTitle("事件类型")
                    ChipRow {
                        com.morrowviento.morrow.domain.model.EventKind.entries.forEach { kind ->
                            ChoiceChip(kind.label, form.kind == kind) { form = form.copy(kind = kind) }
                        }
                    }
                    SectionTitle("重复")
                    ChipRow {
                        com.morrowviento.morrow.domain.model.RepeatType.entries.forEach { repeat ->
                            ChoiceChip(repeat.label, form.repeatType == repeat) {
                                form = form.withRepeatType(repeat)
                            }
                        }
                    }
                    if (form.repeatType != com.morrowviento.morrow.domain.model.RepeatType.NONE) {
                        // 间隔：「每 N 天/周/个月/年」。用加减而不是滚轮——这里只到 99，
                        // 又不是每天都改，塞一个滚轮会把重复区撑掉半屏。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("每", style = TextStyle(fontSize = 13.sp, color = secondary))
                            ChoiceChip("−", false) { form = form.shiftInterval(-1) }
                            Text(
                                text = "${form.repeatInterval}",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary),
                            )
                            ChoiceChip("+", false) { form = form.shiftInterval(1) }
                            Text(
                                text = form.repeatType.intervalUnit(),
                                style = TextStyle(fontSize = 13.sp, color = secondary),
                            )
                        }

                        if (form.repeatType == com.morrowviento.morrow.domain.model.RepeatType.WEEKLY) {
                            // 不选任何一天 = 跟开始日同一个星期几；选了就按选中的来
                            ChipRow {
                                WEEKDAY_LABELS.forEachIndexed { index, label ->
                                    val iso = index + 1
                                    ChoiceChip(label, iso in form.repeatWeekdays) {
                                        form = form.toggleWeekday(iso)
                                    }
                                }
                            }
                            Text(
                                text = if (form.repeatWeekdays.isEmpty()) {
                                    "未选星期时按开始日那一天重复"
                                } else {
                                    "每周 " + form.repeatWeekdays.joinToString("、") { WEEKDAY_LABELS[it - 1] }
                                },
                                style = TextStyle(fontSize = 11.sp, color = secondary),
                            )
                        }

                        if (form.repeatType == com.morrowviento.morrow.domain.model.RepeatType.YEARLY) {
                            ChipRow {
                                ChoiceChip("按农历", form.repeatOnLunar) {
                                    form = form.withLunarYearly(!form.repeatOnLunar)
                                }
                            }
                            if (form.repeatOnLunar) {
                                val lunar = com.morrowviento.morrow.domain.time.LunarCalendar.fromSolar(form.date)
                                Text(
                                    text = if (lunar != null) {
                                        "每年${lunar.monthLabel}${lunar.dayLabel}（农历），小月自动取月末"
                                    } else {
                                        "农历年表只覆盖 1900–2100 年，这一天超出范围"
                                    },
                                    style = TextStyle(fontSize = 11.sp, color = secondary),
                                )
                            }
                        }

                        // 结束条件：永不 / 到某天。只做日期不做次数——次数上限在倒数
                        // 应用里几乎没人用，却要多一套「还剩几次」的状态。
                        ChipRow {
                            ChoiceChip("永不结束", form.repeatUntil == null) { form = form.withRepeatUntil(null) }
                            ChoiceChip(
                                label = form.repeatUntil?.let {
                                    "到 ${it.year} 年 ${it.monthValue} 月 ${it.dayOfMonth} 日"
                                } ?: "到某天结束",
                                selected = form.repeatUntil != null,
                            ) {
                                form = form.withRepeatUntil(form.repeatUntil ?: form.date.plusYears(1))
                            }
                        }
                        form.repeatUntil?.let { until ->
                            WheelDateTimePicker(
                                date = until,
                                time = form.time,
                                isAllDay = true,
                                // 结束日早于开始日的规则等于没有落点，夹到开始日
                                onDateChange = {
                                    form = form.withRepeatUntil(if (it < form.date) form.date else it)
                                },
                                onTimeChange = {},
                            )
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

                    // 多维标记：分类是单选、只有一个维度；标签可多选，用来横向串起事件
                    // （「报销」「出差」同时挂在一件事上）。建议值来自全库，避免每次手打。
                    SectionTitle("标签（最多 ${com.morrowviento.morrow.domain.model.EventTags.MAX_COUNT} 个）")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassTextField(
                            value = tagInput,
                            onValueChange = {
                                tagInput = it.take(com.morrowviento.morrow.domain.model.EventTags.MAX_LENGTH)
                            },
                            placeholder = "如 报销、家人",
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChip("添加", false) {
                            form = form.addTag(tagInput)
                            tagInput = ""
                        }
                    }
                    if (form.tags.isNotEmpty()) {
                        // 已选的标签点一下移除：不需要另开一个删除态
                        ChipRow {
                            form.tags.forEach { tag ->
                                ChoiceChip("#$tag", true) { form = form.removeTag(tag) }
                            }
                        }
                    }
                    val remaining = tagSuggestions.filterNot { suggestion ->
                        form.tags.any { it.equals(suggestion, ignoreCase = true) }
                    }
                    if (remaining.isNotEmpty() && form.tags.size < com.morrowviento.morrow.domain.model.EventTags.MAX_COUNT) {
                        ChipRow {
                            remaining.take(10).forEach { suggestion ->
                                ChoiceChip("+ $suggestion", false) { form = form.addTag(suggestion) }
                            }
                        }
                    }

                    // 时长只服务时间块视图：它决定块占多高。全天事件不需要（顶部整条就是它的位置）。
                    if (!form.isAllDay) {
                        SectionTitle("时长（时间块里占多高）")
                        ChipRow {
                            listOf(30, 60, 90, 120, 240).forEach { minutes ->
                                ChoiceChip(durationText(minutes), form.durationMinutes == minutes) {
                                    form = form.withDuration(minutes)
                                }
                            }
                        }
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
                    // 自定义时间点：预设覆盖不到「提前 45 分钟」「提前 21 天」这类值，
                    // 一条事件可以有多个提醒点，靠的就是这里能加任意分钟数。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassTextField(
                            value = customReminder,
                            onValueChange = { customReminder = it.filter(Char::isDigit).take(5) },
                            placeholder = "自定义提前分钟数，如 45",
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChip("添加", false) {
                            val minutes = customReminder.toIntOrNull()
                            if (minutes != null) {
                                val next = form.addReminder(minutes)
                                reminderHint = if (next.reminders.size == form.reminders.size) {
                                    if (form.reminders.any { it.minutesBefore == minutes }) {
                                        "这条提醒已经加过了"
                                    } else {
                                        "最多 ${EventReminder.MAX_COUNT} 条提醒"
                                    }
                                } else {
                                    null
                                }
                                form = next
                                if (next.reminders.size != form.reminders.size) customReminder = ""
                            }
                        }
                    }
                    if (form.reminders.isNotEmpty()) {
                        Text(
                            text = "已设 " + form.reminders.joinToString("、") {
                                EventReminder.labelForMinutes(it.minutesBefore)
                            },
                            style = TextStyle(fontSize = 11.sp, color = secondary),
                        )
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
    // 「今天 / 明天 / 下周 / 一个月后」四个候选日期只依赖当天，面板生命周期内算一次即可
    val quickDates = remember { EventFormState.quickDates() }
    ChipRow {
        quickDates.forEach { (label, date) ->
            ChoiceChip(label, false) { onPick(date) }
        }
    }
}

private fun CountDirection.label(): String = when (this) {
    CountDirection.AUTO -> "自动方向"
    CountDirection.COUNTDOWN -> "只倒数"
    CountDirection.COUNTUP -> "只正数"
}

/** 周一到周日，下标 0 对应 ISO 1。 */
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 时长候选项的文案：30 分钟 / 1 小时 / 1.5 小时 … */
private fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60}.${(minutes % 60) * 10 / 60} 小时"
}

private fun com.morrowviento.morrow.domain.model.RepeatType.intervalUnit(): String = when (this) {
    com.morrowviento.morrow.domain.model.RepeatType.NONE -> ""
    com.morrowviento.morrow.domain.model.RepeatType.DAILY -> "天"
    com.morrowviento.morrow.domain.model.RepeatType.WEEKLY -> "周"
    com.morrowviento.morrow.domain.model.RepeatType.MONTHLY -> "个月"
    com.morrowviento.morrow.domain.model.RepeatType.YEARLY -> "年"
}

private fun CountDirection.next(): CountDirection {
    val all = CountDirection.entries
    return all[(ordinal + 1) % all.size]
}
