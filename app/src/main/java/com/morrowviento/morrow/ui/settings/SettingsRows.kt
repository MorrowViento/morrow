package com.morrowviento.morrow.ui.settings

/** 设置页的行与弹层：头部、分组卡、动作行、确认弹层、提示条与分段控件选项常量。 */
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.data.backup.ImportMode
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.ui.glass.GlassAlert
import com.morrowviento.morrow.ui.glass.GlassAlertButton
import com.morrowviento.morrow.ui.glass.GlassAlertRole
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- 头部

@Composable
internal fun SettingsHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCirclePanel(
            modifier = Modifier.size(32.dp),
            onClick = onDismiss,
        ) {
            Text(
                text = "‹",
                style = TextStyle(fontSize = 20.sp, color = glassTextPrimary()),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "设置",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
        )
    }
}

// ---------------------------------------------------------------- 分组与行

/**
 * 一组设置：小节标题 + 一张玻璃卡。
 *
 * 标题不复用 `GlasmioSectionTitle`：它的内边距按 iOS 整页算（左右各 16dp），
 * 而我们已经在 LazyColumn 里留了 16dp，叠加后标题会比卡片缩进一大截。
 */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
        )
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.6.dp)
            .background(glassColors().separator),
    )
}

/**
 * 标题 + 控件的一整块（主题 / 玻璃材质这种「下面还得塞个分段控件」的项）。
 *
 * 之前这类项是「一行 title + 右边一排 chip」，chip 一多就挤成两行、说明文字还会被
 * 省略号截断；改成上下排布后文字能整行铺开，分段控件也能拿到整条宽度。
 */
@Composable
internal fun SettingBlock(
    label: String,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
        )
        Spacer(Modifier.height(10.dp))
        control()
        hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = glassTextSecondary()),
            )
        }
    }
}

/** 动作行。[onClick] 为 null 时只做展示（版本号）。 */
@Composable
internal fun ActionRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    SettingRow(
        title = title,
        summary = summary,
        detail = value,
        danger = danger,
        enabled = enabled,
        showChevron = onClick != null,
        onClick = if (enabled) onClick else null,
    )
}

/**
 * 设置行的底座：左侧「标题 + 说明（可换行）」占满剩余宽度，右侧尾随内容。
 *
 * 不复用 `GlasmioListRow`：它的 subtitle 是单行 + 省略号，设置页里几条长说明
 * 全被截成「让玻璃面板更实、文字更清晰，…」。这里让说明吃满两行。
 * 点击也不带 Material 的矩形 ripple（玻璃卡片上会出现一块方块），只留按下态。
 */
@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    detail: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 15.sp,
                    color = when {
                        !enabled -> primary.copy(alpha = 0.4f)
                        danger -> glassColors().destructive
                        else -> primary
                    },
                ),
            )
            summary?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = it,
                    style = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = secondary.copy(alpha = if (enabled) 1f else 0.5f),
                    ),
                )
            }
        }
        detail?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                text = it,
                style = TextStyle(fontSize = 13.sp, color = secondary),
            )
        }
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                style = TextStyle(fontSize = 18.sp, color = secondary.copy(alpha = 0.7f)),
            )
        }
    }
}

// ---------------------------------------------------------------- 弹层

@Composable
internal fun ImportConfirmSheet(
    pending: PendingImport,
    onChoose: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    // 短内容用 fitContent：弹层高度跟着内容走，不会留一大片空白
    GlassSheet(title = "导入备份", onDismiss = onDismiss, fitContent = true) { dismiss ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(
                text = pending.summary,
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = glassTextSecondary()),
            )
            Spacer(Modifier.height(16.dp))
            MenuAction(
                label = "合并（保留本机事件）",
                hint = "同 id 的事件用备份覆盖；外观与小部件样式保持本机",
                onClick = { onChoose(ImportMode.MERGE) },
            )
            Spacer(Modifier.height(4.dp))
            MenuAction(
                label = "覆盖（先清空本机数据）",
                hint = "事件、分类、外观、小部件样式全部替换成备份里的",
                danger = true,
                onClick = { onChoose(ImportMode.REPLACE) },
            )
            Spacer(Modifier.height(8.dp))
            MenuAction(label = "取消", onClick = dismiss)
        }
    }
}

@Composable
internal fun ConfirmClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassAlert(
        visible = true,
        title = "清除全部数据",
        message = "所有事件、分类、小部件样式与外观设置都会被删除，且无法撤销。建议先导出备份。",
        onDismiss = onDismiss,
        buttons = listOf(
            GlassAlertButton(label = "取消", role = GlassAlertRole.Cancel, onClick = onDismiss),
            GlassAlertButton(label = "清除", role = GlassAlertRole.Destructive, onClick = onConfirm),
        ),
    )
}

/**
 * 三个分段控件的选项。
 *
 * 常量的意义不只是省一次 `map`：`GlassSegmentedControl` 把 items 当参数比较，
 * 每帧换一个新 List 会让它一直认为选项变了，指示器随之重算位置。
 */
internal val ThemeItems: List<Pair<ThemeMode, String>> = ThemeMode.entries.map { it to it.label }
internal val FrostItems: List<Pair<GlassFrostLevel, String>> = GlassFrostLevel.entries.map { it to it.label }
internal val MotionItems: List<Pair<Boolean, String>> = listOf(false to "完整", true to "减弱")
internal val BackdropItems: List<Pair<BackdropMode, String>> = BackdropMode.entries.map { it to it.label }
internal val AutoTintItems: List<Pair<Boolean, String>> = listOf(false to "关", true to "开")

@Composable
internal fun MenuAction(
    label: String,
    hint: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                color = if (danger) glassColors().destructive else glassTextPrimary(),
            ),
        )
        hint?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = it,
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = glassTextSecondary()),
            )
        }
    }
}

@Composable
internal fun MessageBar(message: String, onDismiss: () -> Unit) {
    // 自动消失：成功提示不需要用户动手，失败提示用户也可能已经看清
    LaunchedEffect(message) {
        delay(4_000)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss),
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = TextStyle(fontSize = 13.sp, color = glassTextPrimary()),
            )
        }
    }
}
