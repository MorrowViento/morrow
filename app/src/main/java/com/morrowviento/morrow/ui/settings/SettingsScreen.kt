package com.morrowviento.morrow.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.morrowviento.morrow.data.backup.BackupFiles
import com.morrowviento.morrow.data.backup.ImportMode
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.ui.component.ChoiceChip
import com.morrowviento.morrow.ui.glass.GlassBackdrop
import com.morrowviento.morrow.ui.glass.GlassAlert
import com.morrowviento.morrow.ui.glass.GlassAlertButton
import com.morrowviento.morrow.ui.glass.GlassAlertRole
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassOverscrollHost
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.GlassSwitch
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.LocalReduceMotion
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.update.UpdateActions
import com.morrowviento.morrow.ui.update.UpdateSheet
import com.morrowviento.morrow.widget.WidgetEntries
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSectionTitle
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

            // 小部件分组内容单独拎出来：这一段最长，留在原位会把下面几个 item 的顺序读乱
            val widgetsSection: @Composable () -> Unit = {
                SettingsGroup("桌面小部件") {
                    // 与长按图标里的快捷方式同一条路：先调外观，点「添加」才落桌面
                    ActionRow(
                        title = "添加总览小部件",
                        summary = "先挑样式，点「添加」后系统再确认一次",
                        value = "添加",
                        onClick = { WidgetEntries.openAdd(context, isDetail = false) },
                    )
                    Divider()
                    ActionRow(
                        title = "添加单事件小部件",
                        summary = "先选事件与样式，点「添加」后系统再确认一次",
                        value = "添加",
                        onClick = { WidgetEntries.openAdd(context, isDetail = true) },
                    )
                    if (state.widgets.isNotEmpty()) {
                        state.widgets.forEach { widget ->
                            Divider()
                            val together = if (widget.count > 1) "（共 ${widget.count} 个，一起生效）" else ""
                            ActionRow(
                                title = if (widget.isDetail) {
                                    "单事件 · ${widget.eventTitle ?: "未选择事件"}"
                                } else {
                                    "总览"
                                },
                                summary = if (widget.isDetail) {
                                    "修改样式，或换绑另一个事件$together"
                                } else {
                                    "修改样式与显示行数$together"
                                },
                                value = "编辑",
                                onClick = { WidgetEntries.openConfig(context, widget) },
                            )
                        }
                    }
                }
            }

            // 页面级回弹：短内容也能被拉出再弹回（设置项不多时也要有反馈）
            GlassOverscrollHost(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
            LazyColumn(
                // weight 而不是 fillMaxSize：后者会把视口撑成整个 Column 的高度，
                // 头部占掉的那一截就被裁到屏幕外，最后一张卡片滚不到底
                modifier = Modifier
                    .fillMaxSize(),
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
                            summary = "让玻璃面板更实、文字更清晰，背景配色保持不变",
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

                item(key = "widgets") { widgetsSection() }

                item(key = "about") {
                    SettingsGroup("关于") {
                        ActionRow(
                            title = "朝夕",
                            summary = "用 Kotlin + Compose + Glasmio 重写",
                            value = state.appVersion.ifBlank { "—" },
                            onClick = null,
                        )
                        Divider()
                        ActionRow(
                            title = "检查更新",
                            summary = "从 GitHub Releases 与镜像源比对版本，可在应用内下载安装",
                            value = if (state.update.busy) "进行中" else "检查",
                            enabled = !state.update.busy,
                            onClick = actions.checkUpdate,
                        )
                        Divider()
                        ActionRow(title = "开源许可", onClick = actions.showLicenses)
                        Divider()
                        ActionRow(
                            title = "项目主页",
                            summary = "github.com/MorrowViento/morrow",
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

        if (state.update.visible) {
            UpdateSheet(
                state = state.update,
                actions = UpdateActions(
                    check = actions.checkUpdate,
                    download = actions.downloadUpdate,
                    install = actions.installUpdate,
                    dismiss = actions.dismissUpdate,
                ),
            )
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
        // Glasmio 的 iOS 分组列表：分组标题 + 一条连续圆角材质（行与行共享同一块玻璃，
        // 分隔线由 `glasmioGroupedListSeparator` 画在行内，不再是我们自己画的细线）
        GlasmioSectionTitle(title)
        GlasmioGroupedList {
            content()
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
    GlasmioListRow(
        title = title,
        subtitle = summary,
        showTopSeparator = false,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
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
    GlasmioListRow(
        title = title,
        subtitle = summary,
        detail = value,
        // 分隔线由我们的 Divider() 画（行的 showTopSeparator 会把线画在行内顶部，
        // 与分组首行叠在一起会多一条）
        showTopSeparator = false,
        enabled = enabled,
        titleColor = if (danger) Color(0xFFFF7A7A) else Color.Unspecified,
        onClick = if (enabled) onClick else null,
    )
}

// ---------------------------------------------------------------- 弹层

@Composable
private fun ImportConfirmSheet(
    pending: PendingImport,
    onChoose: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    // 短内容用 fitContent：弹层高度跟着内容走，不会留一大片空白
    GlassSheet(title = "导入备份", onDismiss = onDismiss, fitContent = true) { dismiss ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
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
            MenuAction(label = "取消", onClick = dismiss)
        }
    }
}

@Composable
private fun ConfirmClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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

@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    val entries = listOf(
        "Glasmio" to "Liquid Glass 材质与组件，MIT",
        "AndroidX / Jetpack Compose" to "Apache License 2.0",
        "Room / DataStore" to "Apache License 2.0",
        "Gson" to "Apache License 2.0",
    )
    GlassSheet(title = "开源许可", onDismiss = onDismiss, fitContent = true) { dismiss ->
        Column(
            Modifier
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
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
            MenuAction(label = "好", onClick = dismiss)
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
