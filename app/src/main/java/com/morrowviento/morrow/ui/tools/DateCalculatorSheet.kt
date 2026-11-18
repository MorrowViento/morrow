package com.morrowviento.morrow.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.domain.time.DateMath
import com.morrowviento.morrow.domain.time.LunarCalendar
import com.morrowviento.morrow.ui.component.ChoiceChip
import com.morrowviento.morrow.ui.component.GlassTextField
import com.morrowviento.morrow.ui.component.SectionTitle
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.component.WheelDateTimePicker
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.SheetHeader
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * 日期计算器。
 *
 * 三段各回答一个具体问题，互不依赖：
 * 1. 两个日期差多少天（含首尾）；
 * 2. 从某天起加减 N 天是哪天；
 * 3. 公历 ↔ 农历换算。
 *
 * 不做「工作日 / 节假日」：那需要一份会过期的调休表，维护成本远大于收益。
 */
@Composable
fun DateCalculatorSheet(onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(LocalDate.now()) }
    var to by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var base by remember { mutableStateOf(LocalDate.now()) }
    var offsetText by remember { mutableStateOf("30") }
    var lunarMonth by remember { mutableIntStateOf(1) }
    var lunarDay by remember { mutableIntStateOf(1) }
    // 0 = 都收起；1/2/3 表示展开哪一个日期滚轮（一次只展开一个，否则面板被三个滚轮占满）
    var expanded by remember { mutableIntStateOf(0) }

    GlassSheet(title = "日期计算器", onDismiss = onDismiss) { dismiss ->
        Column(Modifier.fillMaxSize()) {
            SheetHeader(title = "日期计算器", onCancel = dismiss)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---- 两日相差 ----
                SectionTitle("两个日期相差")
                DateTapRow(label = "开始", date = from) {
                    expanded = if (expanded == 1) 0 else 1
                }
                if (expanded == 1) {
                    WheelDateTimePicker(
                        date = from,
                        time = LocalTime.MIDNIGHT,
                        isAllDay = true,
                        onDateChange = { from = it },
                        onTimeChange = {},
                    )
                }
                DateTapRow(label = "结束", date = to) {
                    expanded = if (expanded == 2) 0 else 2
                }
                if (expanded == 2) {
                    WheelDateTimePicker(
                        date = to,
                        time = LocalTime.MIDNIGHT,
                        isAllDay = true,
                        onDateChange = { to = it },
                        onTimeChange = {},
                    )
                }
                val signed = DateMath.signedDays(from, to)
                val (weeks, rest) = DateMath.weeksAndDays(signed)
                ResultPanel {
                    Text(
                        text = "${abs(signed)} 天",
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "$weeks 周 $rest 天 · 含首尾 ${DateMath.inclusiveDays(from, to)} 天",
                        style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                    )
                    Text(
                        text = "按年月日算：${DateMath.ageLabel(from, to)}",
                        style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                    )
                    Text(
                        text = if (signed < 0) "结束早于开始（结果是绝对值）" else "结束晚于开始",
                        style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                    )
                }

                Spacer(Modifier.height(6.dp))

                // ---- 加减天数 ----
                SectionTitle("从某天起加减")
                DateTapRow(label = "基准日", date = base) {
                    expanded = if (expanded == 3) 0 else 3
                }
                if (expanded == 3) {
                    WheelDateTimePicker(
                        date = base,
                        time = LocalTime.MIDNIGHT,
                        isAllDay = true,
                        onDateChange = { base = it },
                        onTimeChange = {},
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassTextField(
                        value = offsetText,
                        // 只允许负号与数字：这里的天数可以是负的（往前推）
                        onValueChange = { raw ->
                            offsetText = raw.filter { it.isDigit() || it == '-' }.take(6)
                        },
                        placeholder = "天数，可负数",
                        modifier = Modifier.weight(1f),
                    )
                    listOf(-30, -7, 7, 30, 100).forEach { quick ->
                        ChoiceChip(if (quick > 0) "+$quick" else "$quick", false) {
                            offsetText = quick.toString()
                        }
                    }
                }
                val offset = offsetText.toLongOrNull()
                val shifted = offset?.let { DateMath.addDays(base, it) }
                ResultPanel {
                    if (shifted == null) {
                        Text(
                            text = "输入一个天数（可为负）",
                            style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                        )
                    } else {
                        Text(
                            text = DateMath.longLabel(shifted),
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = DateMath.lunarLabel(shifted) ?: "超出农历年表范围（1900–2100）",
                            style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ---- 农历换算 ----
                SectionTitle("农历换算")
                ResultPanel {
                    Text(
                        text = DateMath.longLabel(base),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
                    )
                    Text(
                        text = DateMath.lunarLabel(base) ?: "超出农历年表范围（1900–2100）",
                        style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                    )
                }
                // 反查：农历月 / 日 → 公历。年份取基准日所在农历年，避免再多一个滚轮。
                val lunarYear = LunarCalendar.fromSolar(base)?.year ?: base.year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("农历", style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()))
                    Stepper(
                        value = "$lunarYear 年",
                        onMinus = {},
                        onPlus = {},
                        enabled = false,
                    )
                    Stepper(
                        value = "${LunarCalendar.monthName(lunarMonth)}",
                        onMinus = { lunarMonth = if (lunarMonth <= 1) 12 else lunarMonth - 1 },
                        onPlus = { lunarMonth = if (lunarMonth >= 12) 1 else lunarMonth + 1 },
                    )
                    Stepper(
                        value = LunarCalendar.dayName(lunarDay),
                        onMinus = { lunarDay = if (lunarDay <= 1) 30 else lunarDay - 1 },
                        onPlus = { lunarDay = if (lunarDay >= 30) 1 else lunarDay + 1 },
                    )
                }
                val back = LunarCalendar.toSolar(lunarYear, lunarMonth, lunarDay)
                ResultPanel {
                    Text(
                        text = back?.let { "对应 ${DateMath.longLabel(it)}" } ?: "这一年没有这个农历日",
                        style = TextStyle(fontSize = 13.sp, color = glassTextPrimary()),
                    )
                    if (back != null) {
                        Text(
                            text = "小月会取月末：农历三十遇上廿九天时按廿九过",
                            style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "提示：不做工作日/节假日计算——那需要一份会过期的调休表。",
                    style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                )
            }
        }
    }
}

/** 一行日期：点击展开/收起该行的滚轮。 */
@Composable
private fun DateTapRow(label: String, date: LocalDate, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(glassColors().elevatedBackground.copy(alpha = if (LocalGlassDark.current) 0.5f else 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = TextStyle(fontSize = 12.sp, color = secondary), modifier = Modifier.weight(1f))
        Text(
            text = DateMath.longLabel(date),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = primary),
        )
    }
}

/** 结果块：用一层同色薄底把结论与输入分开，不用玻璃面板（嵌套玻璃会把采样搞乱）。 */
@Composable
private fun ResultPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(glassColors().elevatedBackground.copy(alpha = if (LocalGlassDark.current) 0.42f else 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content()
    }
}

/** 农历月/日的加减：两个按钮夹一个值。 */
@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (enabled) {
            ChoiceChip("−", false) { onMinus() }
        }
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) glassTextPrimary() else glassTextSecondary(),
                ),
            )
        }
        if (enabled) {
            ChoiceChip("+", false) { onPlus() }
        }
    }
}
