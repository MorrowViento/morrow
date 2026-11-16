package com.morrowviento.morrow.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.morrowviento.morrow.data.backdrop.BackdropCrop
import com.morrowviento.morrow.ui.glass.GlassTextButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.backup.BackupFiles
import com.morrowviento.morrow.data.backup.ImportMode
import com.morrowviento.morrow.data.settings.AccentColor
import com.morrowviento.morrow.data.settings.AccentPalette
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.ui.glass.GlassAlert
import com.morrowviento.morrow.ui.glass.GlassAlertButton
import com.morrowviento.morrow.ui.glass.GlassAlertRole
import com.morrowviento.morrow.ui.glass.GlassBackdrop
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassOverscrollHost
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassSegmentedControl
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.LocalGlassDark
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.glass.glassTextTertiary
import com.morrowviento.morrow.ui.update.UpdateActions
import com.morrowviento.morrow.ui.update.UpdateSheet
import com.morrowviento.morrow.widget.WidgetEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI

/**
 * 设置中心（M6）。
 *
 * 与日历页同为**整页覆盖**：必须自带 [GlassBackdrop] 当不透明底（见 HANDOFF「已知坑」28），
 * 并且自己处理返回键——否则按返回会直接退出 App 而不是回到列表。
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

@Composable
private fun BackdropCropScreen(
    pending: PendingBackdrop,
    busy: Boolean,
    onConfirm: (Bitmap, BackdropCrop) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { if (!busy) onCancel() }
    val bitmap = pending.bitmap
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val initial = remember(pending) { BackdropCrop.centered(bitmap.width, bitmap.height, pending.aspectRatio) }
    var crop by remember(pending) { mutableStateOf(initial) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxSize().background(glassColors().groupedBackground)
            .clickable(interactionSource = interaction, indication = null) { }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("裁剪背景", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()))
        Spacer(Modifier.height(8.dp))
        Text("比例固定为背景窗口；移动图片或双指缩放", style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            val previewWidth = minOf(maxWidth, maxHeight * pending.aspectRatio)
            val previewHeight = previewWidth / pending.aspectRatio
            Canvas(
                Modifier.width(previewWidth).height(previewHeight)
                    .semantics {
                        contentDescription = "背景裁剪预览"
                        if (!busy) customActions = listOf(
                            "向左移动" to Offset(-0.1f, 0f), "向右移动" to Offset(0.1f, 0f),
                            "向上移动" to Offset(0f, -0.1f), "向下移动" to Offset(0f, 0.1f),
                        ).map { (label, pan) ->
                            CustomAccessibilityAction(label) {
                                crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                                    0.5f, 0.5f, pan.x, pan.y, 1f)
                                true
                            }
                        }
                    }
                    .pointerInput(pending, busy) {
                        if (!busy) detectTransformGestures { centroid, pan, zoom, _ ->
                            crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                                centroid.x / size.width, centroid.y / size.height,
                                pan.x / size.width, pan.y / size.height, zoom)
                        }
                    },
            ) {
                clipRect {
                    withTransform({
                        scale(size.width / crop.width, size.height / crop.height, Offset.Zero)
                        translate(-crop.left, -crop.top)
                    }) { drawImage(image) }
                    repeat(2) { index ->
                        val fraction = (index + 1) / 3f
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(size.width * fraction, 0f),
                            Offset(size.width * fraction, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, size.height * fraction),
                            Offset(size.width, size.height * fraction), strokeWidth = 1.dp.toPx())
                    }
                    drawRect(Color.White.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextButton("缩小", { crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                0.5f, 0.5f, 0f, 0f, 1f / 1.25f) }, enabled = !busy, modifier = Modifier.height(44.dp))
            GlassTextButton("重置", { crop = initial }, enabled = !busy, modifier = Modifier.height(44.dp))
            GlassTextButton("放大", { crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                0.5f, 0.5f, 0f, 0f, 1.25f) }, enabled = !busy, modifier = Modifier.height(44.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextButton("取消", onCancel, modifier = Modifier.weight(1f).height(48.dp), enabled = !busy, compact = false)
            GlassTextButton(if (busy) "保存中…" else "使用此背景", { onConfirm(bitmap, crop) },
                modifier = Modifier.weight(1f).height(48.dp), active = true, enabled = !busy, compact = false)
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
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun Divider() {
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
private fun SettingBlock(
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

/**
 * 配色方案色板：七套预设 + 一档「从背景图取色」。
 *
 * 每个圆点是这套配色的**缩影**：中心是强调色，边缘往它自己的壁纸色斑上过渡。
 * 上一版是纯实心色点，只能看出「按钮会变蓝还是变紫」，看不出整页会变成什么样；
 * 加上壁纸色之后，七套方案的冷暖差异一眼能分出来。
 *
 * 第八颗（取色）在没选图时画成虚线圈：它此刻不可用，但**位置要占住**——
 * 用户的预期是「这里还有一个来源」，而不是让人先去发现有没有图。
 *
 * 选中的那圈加一道描边——不用「打勾」：色板上一眼要读的是**颜色本身**，
 * 勾号会把中间那块色盖掉一小半。
 *
 * 点一下就写设置，没有「确定」：换色是即时可见的（整页背景立刻变），
 * 多一步确认反而让用户先离开设置页才能看到效果。
 */
@Composable
private fun AccentSwatches(
    selected: AccentPalette,
    autoSelected: Boolean,
    autoPreview: AccentPalette?,
    isDark: Boolean,
    onSelected: (AccentColor) -> Unit,
    onAutoSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentColor.entries.forEach { color ->
            val palette = color.palette
            SwatchDot(
                // 用「冷」那团色斑做边缘：它在每套方案里都是最接近背景基调的一块
                center = Color(palette.argb(isDark)),
                rim = Color(palette.blobs(isDark)[1]),
                active = !autoSelected && selected.id == color.prefValue,
                onClick = { onSelected(color) },
            )
        }
        SwatchDot(
            center = autoPreview?.let { Color(it.argb(isDark)) }
                ?: glassTextSecondary().copy(alpha = 0.30f),
            rim = autoPreview?.let { Color(it.blobs(isDark)[1]) } ?: Color.Transparent,
            active = autoSelected,
            // 没有图 = 这档暂时不可用：画成虚线圈，点它会给一句「先选一张图」的提示
            dashed = autoPreview == null,
            onClick = onAutoSelected,
        )
    }
}

/** 单个色点：径向渐变圆 + 选中描边（虚线表示「暂不可用」）。 */
@Composable
private fun SwatchDot(
    center: Color,
    rim: Color,
    active: Boolean,
    dashed: Boolean = false,
    onClick: () -> Unit,
) {
    val ring = glassTextPrimary()
    Canvas(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        val stroke = 1.6.dp.toPx()
        val radius = size.minDimension / 2f
        if (active) {
            // 描边贴着边缘画，圆点本身不动——换色时整排不会跳
            drawCircle(
                color = ring.copy(alpha = 0.80f),
                radius = radius - stroke / 2f,
                style = Stroke(width = stroke),
            )
        }
        val middle = Offset(size.width / 2f, size.height / 2f)
        if (dashed) {
            drawCircle(
                color = center,
                radius = radius - stroke,
                center = middle,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
                ),
            )
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(center, center, rim),
                    center = middle,
                    radius = radius * 0.68f,
                ),
                radius = radius * 0.68f,
            )
        }
    }
}

/**
 * 背景图缩略条。
 *
 * 用一整条而不是一个小方块：用户要判断的是「这张图当整页底色好不好看」，
 * 一片 44×44 的缩略图看不出构图与明暗。112dp 的高度够读出主体与冷暖。
 *
 * 解码复用 [BackdropImage] 的进程内缓存，与 `GlassBackdrop` 共用同一张位图，
 * 不会多解一份。
 */
@Composable
private fun BackdropThumbnail(fileName: String?) {
    val context = LocalContext.current
    var bitmap by remember(fileName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(fileName) {
        bitmap = if (fileName == null) {
            null
        } else {
            withContext(Dispatchers.IO) { BackdropImage.load(context, fileName) }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(shape)
            .background(glassColors().elevatedBackground.copy(alpha = 0.45f)),
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "当前背景图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 玻璃厚度的四个图标（通透 / 轻盈 / 标准 / 厚重）——**四个圆环**。
 *
 * 圆环读作「这层玻璃的横截面」：环越细越虚 = 玻璃越薄，越粗越实 = 越厚。
 * 上一版画的是「玻璃卡压住两个圆点」，但卡片在 19dp 里只有 9dp 高，
 * 前两档的面几乎全透明，四格看上去只差一点点灰度；圆环把「清晰度」直接画成了
 * **线本身的虚实**，一眼就能分：
 *
 * - 通透：稀疏虚线环（8 段），最细最淡，光几乎不被拦；
 * - 轻盈：细密虚线环（16 段），虚但看得见走势；
 * - 标准：实线环；
 * - 厚重：粗实线外环 + 一圈内环，像一片有厚度的玻璃边。
 */
@Composable
private fun FrostGlyph(level: GlassFrostLevel, size: Dp = 20.dp, alpha: Float = 1f) {
    val ink = glassTextPrimary()
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = side / 2f - 1.4.dp.toPx()
        when (level) {
            GlassFrostLevel.CLEAR -> drawDashedRing(
                center, radius, ink.copy(alpha = alpha * 0.42f), 1.0.dp.toPx(), 8,
            )
            GlassFrostLevel.LIGHT -> drawDashedRing(
                center, radius, ink.copy(alpha = alpha * 0.62f), 1.3.dp.toPx(), 16,
            )
            GlassFrostLevel.STANDARD -> drawCircle(
                ink.copy(alpha = alpha * 0.82f), radius, center, style = Stroke(1.8.dp.toPx()),
            )
            GlassFrostLevel.HEAVY -> {
                drawCircle(
                    ink.copy(alpha = alpha * 0.95f), radius, center, style = Stroke(2.6.dp.toPx()),
                )
                drawCircle(
                    ink.copy(alpha = alpha * 0.30f), radius * 0.50f, center,
                    style = Stroke(1.1.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 虚线圆环：dash 长度按周长均分，保证首尾刚好接上。
 *
 * 用固定 dash 值会在收口处露出一段半长不短的缺口——周长不是 (dash + gap) 的整数倍。
 */
private fun DrawScope.drawDashedRing(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Float,
    segments: Int,
) {
    val segment = (2f * PI.toFloat() * radius) / segments
    val effect = PathEffect.dashPathEffect(floatArrayOf(segment * 0.58f, segment * 0.42f))
    drawCircle(color, radius, center, style = Stroke(width = stroke, pathEffect = effect))
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

/**
 * 主题的三个图标（自动 / 浅色 / 深色）。
 *
 * 用 Canvas 手画而不是引 SF Symbols：库里的 `SfSymbol` 只有 House / Clock / Gearshape
 * 这一套，没有太阳与月亮，硬凑会认不出来。
 */
@Composable
private fun ThemeGlyph(mode: ThemeMode, size: Dp = 18.dp, alpha: Float = 1f) {
    val color = glassTextPrimary().copy(alpha = alpha)
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val hair = 1.5.dp.toPx()
        when (mode) {
            ThemeMode.SYSTEM -> {
                drawCircle(color, radius = r * 0.80f, style = Stroke(width = hair))
                val half = Path().apply {
                    moveTo(c.x, c.y - r * 0.80f)
                    arcTo(Rect(c, r * 0.80f), -90f, 180f, true)
                    close()
                }
                drawPath(half, color)
            }

            ThemeMode.LIGHT -> {
                drawCircle(color, radius = r * 0.36f)
                repeat(8) { index ->
                    rotate(index * 45f, pivot = c) {
                        drawLine(
                            color = color,
                            start = Offset(c.x, c.y - r * 0.62f),
                            end = Offset(c.x, c.y - r * 0.92f),
                            strokeWidth = hair,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            ThemeMode.DARK -> {
                val disc = Path().apply { addOval(Rect(c, r * 0.86f)) }
                val bite = Path().apply {
                    addOval(Rect(Offset(c.x + r * 0.44f, c.y - r * 0.34f), r * 0.76f))
                }
                val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
                drawPath(crescent, color)
            }
        }
    }
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

/**
 * 三个分段控件的选项。
 *
 * 常量的意义不只是省一次 `map`：`GlassSegmentedControl` 把 items 当参数比较，
 * 每帧换一个新 List 会让它一直认为选项变了，指示器随之重算位置。
 */
private val ThemeItems: List<Pair<ThemeMode, String>> = ThemeMode.entries.map { it to it.label }
private val FrostItems: List<Pair<GlassFrostLevel, String>> = GlassFrostLevel.entries.map { it to it.label }
private val MotionItems: List<Pair<Boolean, String>> = listOf(false to "完整", true to "减弱")
private val BackdropItems: List<Pair<BackdropMode, String>> = BackdropMode.entries.map { it to it.label }
private val AutoTintItems: List<Pair<Boolean, String>> = listOf(false to "关", true to "开")

/** 开源许可清单：常量，不必在每次打开弹层时重建一遍列表。 */
private val LicenseEntries: List<Pair<String, String>> = listOf(
    "Glasmio" to "Liquid Glass 材质与组件，MIT",
    "AndroidX / Jetpack Compose" to "Apache License 2.0",
    "Room / DataStore" to "Apache License 2.0",
    "Gson" to "Apache License 2.0",
)

@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    GlassSheet(title = "开源许可", onDismiss = onDismiss, fitContent = true) { dismiss ->
        Column(
            Modifier
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LicenseEntries.forEach { (name, license) ->
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
