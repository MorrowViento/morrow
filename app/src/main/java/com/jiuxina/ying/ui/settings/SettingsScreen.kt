package com.jiuxina.ying.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jiuxina.ying.data.backup.BackupFiles
import com.jiuxina.ying.data.backup.ImportMode
import com.jiuxina.ying.data.settings.ThemeMode
import com.jiuxina.ying.ui.component.ChoiceChip
import com.jiuxina.ying.ui.glass.GlassBackdrop
import com.jiuxina.ying.ui.glass.GlassCirclePanel
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.LocalReduceMotion
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import kotlinx.coroutines.delay

/**
 * 设置中心（M6）。
 *
 * 与日历页同为**整页覆盖**：必须自带 [GlassBackdrop] 当不透明底（见 HANDOFF「已知坑」28），
 * 并且自己处理返回键——否则按返回会直接退出 App 而不是回到列表。
 *
 * 结构上只有一个 LazyColumn：分组卡片各占一个 item。不用「外层 verticalScroll + 内层 LazyColumn」，
 * 那种组合在纵向约束无限时会直接抛异常（点 + 号闪退的根因）。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler { onDismiss() }

    // 通知 / 精确闹钟是在系统设置页改的，本进程收不到回调；回到前台时重读一次
    LifecycleResumeEffect(Unit) {
        actions.refresh()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFiles.MIME_TYPE),
    ) { uri -> uri?.let(actions.exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(actions.loadImport) }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop()

        Column(Modifier.fillMaxSize()) {
            SettingsHeader(onDismiss)
            LazyColumn(
                // weight 而不是 fillMaxSize：后者会把视口撑成整个 Column 的高度，
                // 头部占掉的那一截就被裁到屏幕外，最后一张卡片滚不到底
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "appearance") {
                    SettingsGroup("外观") {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            SettingLabel("主题")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    ChoiceChip(
                                        label = mode.label,
                                        selected = state.settings.themeMode == mode,
                                        onClick = { actions.setTheme(mode) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            SettingHint("「跟随系统」会随手机的深色模式自动切换")
                        }
                        Divider()
                        SwitchRow(
                            title = "减弱透明",
                            summary = "把玻璃材质降级为不透明面板，内容更清晰、更省电",
                            checked = state.settings.reduceTransparency,
                            onCheckedChange = actions.setReduceTransparency,
                        )
                        SwitchRow(
                            title = "减弱动效",
                            summary = "关闭页面切换的淡入淡出，只保留即时切换",
                            checked = state.settings.reduceMotion,
                            onCheckedChange = actions.setReduceMotion,
                        )
                    }
                }

                item(key = "data") {
                    SettingsGroup("数据") {
                        ActionRow(
                            title = "导出备份",
                            summary = "事件、分类、外观设置与小部件样式，存成一个 JSON 文件",
                            enabled = !state.busy,
                            onClick = { exportLauncher.launch(BackupFiles.suggestedName()) },
                        )
                        Divider()
                        ActionRow(
                            title = "导入备份",
                            summary = "可以选择只合并事件，或整套覆盖本机数据",
                            enabled = !state.busy,
                            onClick = {
                                // 只认 json：先收窄文件选择器，减少用户误选相册里的图片
                                importLauncher.launch(arrayOf(BackupFiles.MIME_TYPE, "text/plain", "*/*"))
                            },
                        )
                        Divider()
                        ActionRow(
                            title = "清除全部数据",
                            summary = "事件、分类、小部件样式与设置全部恢复初始状态",
                            danger = true,
                            enabled = !state.busy,
                            onClick = actions.requestClearAll,
                        )
                    }
                }

                item(key = "reminder") {
                    SettingsGroup("提醒") {
                        ActionRow(
                            title = "通知权限",
                            summary = if (state.notificationAllowed) "已开启" else "已关闭，到点不会提醒",
                            value = if (state.notificationAllowed) null else "去开启",
                            onClick = actions.openNotificationSettings,
                        )
                        Divider()
                        ActionRow(
                            title = "精确闹钟",
                            summary = if (state.exactAlarmAllowed) {
                                "已允许，提醒会在设定时刻准点触发"
                            } else {
                                "未允许，系统可能推迟几分钟才提醒"
                            },
                            value = if (state.exactAlarmAllowed) null else "去允许",
                            onClick = actions.openExactAlarmSettings,
                        )
                    }
                }

                item(key = "about") {
                    SettingsGroup("关于") {
                        ActionRow(
                            title = "萤",
                            summary = "用 Kotlin + Compose + Glasmio 重写",
                            value = state.appVersion.ifBlank { "—" },
                            onClick = null,
                        )
                        Divider()
                        ActionRow(title = "开源许可", onClick = actions.showLicenses)
                        Divider()
                        ActionRow(
                            title = "项目主页",
                            summary = "github.com/MorrowViento/ying-native",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, BackupFiles.projectUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                }

                item(key = "footer") {
                    Text(
                        text = "备份文件是纯文本 JSON，可以用任何编辑器打开查看",
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp),
                        style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                    )
                }
            }
        }

        state.pendingImport?.let { pending ->
            ImportConfirmSheet(
                pending = pending,
                onChoose = actions.confirmImport,
                onDismiss = actions.cancelImport,
            )
        }

        if (state.confirmClearAll) {
            ConfirmClearDialog(
                onConfirm = actions.confirmClearAll,
                onDismiss = actions.cancelClearAll,
            )
        }

        if (state.showLicenses) {
            LicensesSheet(onDismiss = actions.hideLicenses)
        }

        state.message?.let { message ->
            MessageBar(message = message, onDismiss = actions.consumeMessage)
        }
    }
}

// ---------------------------------------------------------------- 头部

@Composable
private fun SettingsHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCirclePanel(modifier = Modifier.size(32.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "‹",
                    style = TextStyle(fontSize = 20.sp, color = glassTextPrimary()),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "设置",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
        )
    }
}

// ---------------------------------------------------------------- 分组与行

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
        )
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 2.dp)
            .height(0.6.dp)
            .background(glassTextPrimary().copy(alpha = 0.08f)),
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 14.sp, color = glassTextPrimary()),
    )
}

@Composable
private fun SettingHint(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
    )
}

/** 开关行。 */
@Composable
private fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SettingLabel(title)
            summary?.let {
                Spacer(Modifier.height(2.dp))
                SettingHint(it)
            }
        }
        Spacer(Modifier.width(12.dp))
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 动作行。[onClick] 为 null 时只做展示（版本号）。 */
@Composable
private fun ActionRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val titleColor = when {
        danger -> Color(0xFFFF7A7A)
        !enabled -> glassTextPrimary().copy(alpha = 0.4f)
        else -> glassTextPrimary()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = TextStyle(fontSize = 14.sp, color = titleColor))
            summary?.let {
                Spacer(Modifier.height(2.dp))
                SettingHint(it)
            }
        }
        value?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                text = it,
                style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                style = TextStyle(fontSize = 15.sp, color = glassTextSecondary()),
            )
        }
    }
}

/**
 * 自绘开关。
 *
 * 不用 Material3 的 `Switch`：它带 Material 主题色与固定尺寸，
 * 在玻璃面板上会显得比周围控件"亮一档"，且颜色不受 `reduceTransparency` 影响。
 */
@Composable
private fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val primary = glassTextPrimary()
    val target = if (checked) 18.dp else 2.dp
    val knobOffset by animateDpAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) snap() else tween(150),
        label = "switchKnob",
    )
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(primary.copy(alpha = if (checked) 0.85f else 0.16f))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                // lambda 重载读的是布局阶段的值，不必每次滑动都触发重组
                .offset { IntOffset(knobOffset.roundToPx(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else primary.copy(alpha = 0.9f)),
        )
    }
}

// ---------------------------------------------------------------- 弹层

@Composable
private fun ImportConfirmSheet(
    pending: PendingImport,
    onChoose: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                Text(
                    text = "导入备份",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(6.dp))
                SettingHint(pending.summary)
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
                MenuAction(label = "取消", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ConfirmClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "清除全部数据",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(8.dp))
                SettingHint("所有事件、分类、小部件样式与外观设置都会被删除，且无法撤销。建议先导出备份。")
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "取消",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        style = TextStyle(fontSize = 13.sp, color = glassTextSecondary()),
                    )
                    Text(
                        text = "清除",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF7A7A)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    val entries = listOf(
        "Glasmio" to "Liquid Glass 材质与组件，MIT",
        "AndroidX / Jetpack Compose" to "Apache License 2.0",
        "Room / DataStore" to "Apache License 2.0",
        "Gson" to "Apache License 2.0",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                Text(
                    text = "开源许可",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(12.dp))
                entries.forEach { (name, license) ->
                    Text(
                        text = name,
                        style = TextStyle(fontSize = 13.sp, color = glassTextPrimary()),
                    )
                    Text(
                        text = license,
                        style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                MenuAction(label = "好", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MenuAction(
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
                color = if (danger) Color(0xFFFF7A7A) else glassTextPrimary(),
            ),
        )
        hint?.let {
            Spacer(Modifier.height(2.dp))
            SettingHint(it)
        }
    }
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
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
