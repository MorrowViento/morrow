package com.morrowviento.morrow.ui.settings

/** 设置中心：整页覆盖的分组菜单；背景由全窗口宿主提供，明细组件拆在 `ui/settings/` 其余文件。 */
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.morrowviento.morrow.data.backup.BackupFiles
import com.morrowviento.morrow.data.settings.AccentPalette
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.ui.glass.GlassOverscrollHost
import com.morrowviento.morrow.ui.glass.GlassSegmentedControl
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.glass.glassTextTertiary
import com.morrowviento.morrow.ui.update.UpdateActions
import com.morrowviento.morrow.ui.update.UpdateSheet
import com.morrowviento.morrow.widget.WidgetEntries

/**
 * 设置中心（M6）。
 *
 * 与日历页同为**整页覆盖**，但**不在安全区内重绘壁纸**：背景由全窗口宿主
 * （`GlassBackdropHost`）提供，本页只铺内容。之前这里调 `GlassBackdrop()` 画整页底，
 * 而它被父层的 `safeDrawingPadding()` 约束，位图从状态栏下沿才开始、渐变原点与照片
 * 裁剪比例都和系统栏那一截对不上，顶上就露出一道「割裂」的横线——日历页 4.1.1 已用
 * 同一套办法修过。首页在设置页打开期间由宿主整块让位（见 `HomeScreen`），
 * 因此不需要再靠一层不透明底色遮挡透出来的标题与卡片。
 *
 * 返回键仍由本页自己处理——否则按返回会直接退出 App 而不是回到列表。
 *
 * 分组卡片用 [GlassPanel]（真正的 `GlassSurface`），不是 `GlasmioGroupedList`：
 * 后者画的是一层 55% 不透明的**纯色**（`SheetGroupedListBackgroundAlpha`），
 * 没有采样、没有折射，在纯色底上看起来就是一块死板的深灰方块——
 * 「设置界面玻璃应用不完全」说的就是这个。换成真玻璃后材质档位也能立刻生效。
 *
 * 结构上只有一个 LazyColumn：分组卡片各占一个 item。不用「外层 verticalScroll +
 * 内层 LazyColumn」，那种组合在纵向约束无限时会直接抛异常（点 + 号闪退的根因）。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onDismiss: () -> Unit,
    backgroundAspectRatio: Float,
) {
    val context = LocalContext.current
    BackHandler { if (!state.busy) { actions.cancelBackdrop(); onDismiss() } }

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

    // 系统相册选择器（Android 13+ 是 Photo Picker）：**不需要任何存储权限**，
    // 也不要求用户理解「读取相册」这种吓人的授权文案。返回的 uri 是临时授权，
    // 返回后立即有界解码供裁剪，确认时才把最终画面保存到私有目录。
    val backdropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { actions.prepareBackdrop(it, backgroundAspectRatio) } }
    val pickBackdrop: () -> Unit = {
        runCatching {
            backdropLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "appearance") {
                        SettingsGroup("外观") {
                            SettingBlock("主题") {
                                // 图标版分段控件（示例里的 Icon Segmented Control）：选中块的
                                // 指示器是透镜材质，按下去几乎全透明、边缘把旁边图标折射变形
                                GlassSegmentedControl(
                                    items = ThemeItems,
                                    selected = state.settings.themeMode,
                                    onSelected = actions.setTheme,
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 38.dp,
                                    // 未选中的那两格压暗，和 iOS 分段控件一致
                                    itemContent = { mode, selected ->
                                        ThemeGlyph(mode, alpha = if (selected) 1f else 0.55f)
                                    },
                                )
                            }
                            Divider()
                            val backdrop = state.settings.backdrop
                            val resolved = state.settings.resolvedAccent
                            SettingBlock(
                                label = "配色方案",
                                hint = if (backdrop.tintActive) {
                                    "正在跟随背景图取色：换一张图，整套颜色跟着变"
                                } else {
                                    "背景、卡片、底栏、按钮一起换色，不只换图标"
                                },
                            ) {
                                AccentSwatches(
                                    selected = resolved,
                                    autoSelected = backdrop.tintActive,
                                    autoPreview = if (backdrop.tintSeed != 0L) {
                                        AccentPalette.fromSeed(backdrop.tintSeed)
                                    } else {
                                        null
                                    },
                                    isDark = LocalGlassDark.current,
                                    onSelected = actions.setAccent,
                                    onAutoSelected = { actions.setAutoAccent(true) },
                                )
                                Spacer(Modifier.height(9.dp))
                                Text(
                                    // 让「点了会变什么」写清楚：这一项以前只作用于按钮图标，
                                    // 用户点完发现整页没变，会以为没生效。
                                    text = if (backdrop.tintActive) "取色 · ${resolved.label}（来自背景图）" else resolved.label,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = glassTextSecondary(),
                                    ),
                                )
                            }
                            Divider()
                            SettingBlock(
                                label = "背景",
                                hint = when {
                                    backdrop.effectiveMode == BackdropMode.IMAGE ->
                                        "整页换成你选的照片；卡片、底栏、弹层会实时把它糊开、折弯"
                                    else -> "内置丝缎壁纸，颜色跟着上面的配色方案走"
                                },
                            ) {
                                GlassSegmentedControl(
                                    items = BackdropItems,
                                    selected = backdrop.effectiveMode,
                                    onSelected = { mode ->
                                        // 选「图片」但还没选过图：直接拉起相册，少一次「点了没反应」
                                        if (mode == BackdropMode.IMAGE && !backdrop.hasImage) {
                                            pickBackdrop()
                                        } else {
                                            actions.setBackdropMode(mode)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 34.dp,
                                )
                                if (backdrop.hasImage) {
                                    Spacer(Modifier.height(10.dp))
                                    BackdropThumbnail(fileName = backdrop.image)
                                }
                            }
                            Divider()
                            ActionRow(
                                title = if (backdrop.hasImage) "更换背景图片" else "选择背景图片",
                                summary = "从相册选择并裁剪，确认后保存；不需要相册权限",
                                enabled = !state.busy,
                                value = if (backdrop.hasImage) "更换" else "选择",
                                onClick = pickBackdrop,
                            )
                            Divider()
                            SettingBlock(
                                label = "从图片取色",
                                hint = when {
                                    !backdrop.hasImage -> "选一张背景图之后，可以用它的主色生成整套主题色"
                                    backdrop.tintActive -> "当前主题色由这张图的主色生成"
                                    else -> "打开后主题色改为图片主色，卡片与底栏一起变"
                                },
                            ) {
                                GlassSegmentedControl(
                                    items = AutoTintItems,
                                    selected = backdrop.autoTint && backdrop.hasImage,
                                    onSelected = { enabled ->
                                        if (backdrop.hasImage) {
                                            actions.setAutoAccent(enabled)
                                        } else {
                                            pickBackdrop()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 34.dp,
                                )
                            }
                            if (backdrop.hasImage) {
                                Divider()
                                ActionRow(
                                    title = "移除背景图片",
                                    summary = "回到丝缎壁纸，取色也一起关掉",
                                    danger = true,
                                    onClick = actions.clearBackdrop,
                                )
                            }
                            Divider()
                            SettingBlock(
                                label = "玻璃厚度",
                                hint = "${state.frostLevel.label} · ${state.frostLevel.hint}",
                            ) {
                                // 图标版分段控件：四格各画一个圆环——虚线越疏越通透、
                                // 实线越粗越厚重。库的指示器是透镜材质，按下时把相邻两格折射挤歪。
                                GlassSegmentedControl(
                                    items = FrostItems,
                                    selected = state.frostLevel,
                                    onSelected = actions.setGlassFrostLevel,
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 38.dp,
                                    itemContent = { level, selected ->
                                        FrostGlyph(level, alpha = if (selected) 1f else 0.55f)
                                    },
                                )
                            }
                            Divider()
                            // 布尔项也是两段分段控件（完整 / 减弱），而不是系统绿开关：
                            // 同一张卡里混着「分段控件 + 绿胶囊」时，绿色是整页唯一的强调色，
                            // 比玻璃本身还抢眼，和新的中性配色也不搭
                            SettingBlock(
                                label = "动效",
                                hint = if (state.settings.reduceMotion) {
                                    "只保留即时切换，不做淡入淡出与页面推入"
                                } else {
                                    "页面切换、弹层、撤销条都走 iOS 的弹簧曲线"
                                },
                            ) {
                                GlassSegmentedControl(
                                    items = MotionItems,
                                    selected = state.settings.reduceMotion,
                                    onSelected = actions.setReduceMotion,
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 34.dp,
                                )
                            }
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
                            ActionRow(
                                title = "开源许可",
                                summary = "MIT / Apache-2.0 / BSD，可离线阅读全文",
                                onClick = actions.showLicenses,
                            )
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
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                            style = TextStyle(fontSize = 11.sp, color = glassTextTertiary()),
                        )
                    }
                }
            }
        }

        state.pendingBackdrop?.let { pending ->
            BackdropCropScreen(pending, state.busy, actions.confirmBackdrop, actions.cancelBackdrop)
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
            // 回调集合只随 actions 变化：更新面板可见时每帧都新建一个对象，
            // 会让整个弹层的参数一直判为「变了」
            val updateActions = remember(actions) {
                UpdateActions(
                    check = actions.checkUpdate,
                    download = actions.downloadUpdate,
                    install = actions.installUpdate,
                    dismiss = actions.dismissUpdate,
                )
            }
            UpdateSheet(
                state = state.update,
                actions = updateActions,
            )
        }
    }
}
