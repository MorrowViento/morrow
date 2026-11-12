package com.morrowviento.morrow.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.roundToInt
import com.morrowviento.morrow.data.backup.BackupFiles
import com.morrowviento.morrow.data.backup.ImportMode
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
import com.morrowviento.morrow.ui.glass.GlassSlider
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.glass.glassTextTertiary
import com.morrowviento.morrow.ui.update.UpdateActions
import com.morrowviento.morrow.ui.update.UpdateSheet
import com.morrowviento.morrow.widget.WidgetEntries
import kotlinx.coroutines.delay

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
                                    items = ThemeMode.entries.map { it to it.label },
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
                            SettingBlock(
                                label = "玻璃厚度",
                                hint = frostHint(state.effectiveSettings.glassFrost),
                            ) {
                                // 连续滑块，取代原来的四档分段控件：
                                // 库的 RegularMedium 与 RegularLarge 五个参数完全一致，
                                // 「标准 / 厚重」其实一模一样，分档只会有「没生效」的错觉。
                                GlassFrostSlider(
                                    committed = state.settings.glassFrost,
                                    onPreview = actions.previewGlassFrost,
                                    onCommit = actions.setGlassFrost,
                                    modifier = Modifier.fillMaxWidth(),
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
                                    items = listOf(false to "完整", true to "减弱"),
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
 * 玻璃厚度滑块。
 *
 * 拖动期间只回调 [onPreview]（改内存里的值，整页玻璃实时变），松手才 [onCommit] 落盘。
 * 单独包一层是为了在「最后一次拖动值」上提交：库的 `onValueChangeFinished` 不带参数，
 * 而界面上的 state 要等一次重组才更新，直接读它会差一帧。
 */
@Composable
private fun GlassFrostSlider(
    committed: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 滑块拇指跟本地值走（逐帧平滑），主题只按 2% 的台阶重绘：
    // 拖动一次整页玻璃就要重建模糊半径，逐帧传会把重绘次数放大到十几倍。
    var draft by remember { mutableFloatStateOf(committed) }
    // 外部改动（导入备份、旧数据迁移、落盘回流）时把滑块拉回正确位置
    LaunchedEffect(committed) { draft = committed }
    GlassSlider(
        value = draft,
        onValueChange = {
            draft = it
            onPreview((it * 50f).roundToInt() / 50f)
        },
        onValueChangeFinished = { onCommit(draft) },
        modifier = modifier,
    )
}

/** 厚度的一句话说明，按区间给，和滑块的三处刻度对齐。 */
private fun frostHint(value: Float): String = when {
    value < 0.14f -> "几乎全通透：背后内容看得最清楚，字会稍淡"
    value < 0.45f -> "轻薄磨砂：iOS 导航栏的观感，日常推荐"
    value < 0.72f -> "标准磨砂：背景细节变模糊，正文更清楚"
    else -> "厚重：接近实心卡片，对比度最高，几乎不透视"
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
