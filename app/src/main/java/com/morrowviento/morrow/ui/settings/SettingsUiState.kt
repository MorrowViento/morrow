package com.morrowviento.morrow.ui.settings

import com.morrowviento.morrow.data.backup.BackupDocument
import com.morrowviento.morrow.data.backup.ImportMode
import com.morrowviento.morrow.data.backup.ImportReport
import com.morrowviento.morrow.data.settings.AppFont
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import com.morrowviento.morrow.ui.update.UpdateUiState
import com.morrowviento.morrow.widget.PlacedWidget

/** 解析成功、等待用户选择「覆盖 / 合并」的备份。 */
data class PendingImport(
    val document: BackupDocument,
    val skippedEvents: Int,
) {
    val eventCount: Int get() = document.events.size
    val categoryCount: Int get() = document.categories.size
    val widgetCount: Int get() = document.widgets.size

    /** 导入确认面板上的摘要。 */
    val summary: String
        get() = buildString {
            append("备份含 $eventCount 个事件")
            if (categoryCount > 0) append("、$categoryCount 个分类")
            if (widgetCount > 0) append("、$widgetCount 份小部件样式")
            if (skippedEvents > 0) append("（有 $skippedEvents 条损坏已跳过）")
        }
}

data class PendingBackdrop(val bitmap: android.graphics.Bitmap, val aspectRatio: Float)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val notificationAllowed: Boolean = true,
    val exactAlarmAllowed: Boolean = true,
    val appVersion: String = "",
    /**
     * 桌面上已放置的朝夕小部件。
     *
     * 放在设置页而不是只依赖「长按 → 编辑」，是因为后者由桌面决定给不给；
     * 这里列出来，用户总有一条能进配置台的路。
     */
    val widgets: List<PlacedWidget> = emptyList(),
    /** 导出/导入进行中：按钮置灰，避免重复触发两次写同一个文件。 */
    val busy: Boolean = false,
    val pendingImport: PendingImport? = null,
    val pendingBackdrop: PendingBackdrop? = null,
    val confirmClearAll: Boolean = false,
    val showLicenses: Boolean = false,
    /** 字体选择面板是否打开。 */
    val showFontPicker: Boolean = false,
    /** 已导入的字体，供字体面板列出；默认字体不在这里，单独一行表示。 */
    val fonts: List<AppFont> = emptyList(),
    /** 一次性提示，展示后由 [SettingsActions.consumeMessage] 清掉。 */
    val message: String? = null,
    /**
     * 正在预览的节日皮肤（只在设置页打开期间有效，不落盘）。
     *
     * 节日一年只有八九天会生效，其余日子点开设置只能看到一句「节日当天才有」，
     * 用户没法确认这个开关到底做了什么。预览时首页 / 设置页的背景当场换成那个
     * 节日的柔光，标题下的祝语也跟着变，看完点一下结束。
     */
    val festivalPreview: com.morrowviento.morrow.ui.glass.FestivalPreview? = null,
    /** M7：应用内更新检查 / 下载 / 安装的进度。 */
    val update: UpdateUiState = UpdateUiState(),
) {
    /** 连续厚度落在哪个档位（分段控件据此点亮一格）。 */
    val frostLevel: GlassFrostLevel get() = GlassFrostLevel.nearest(settings.glassFrost)
}

data class SettingsActions(
    /** 页面重新可见时刷新系统权限状态（通知 / 精确闹钟只能在系统界面改）。 */
    val refresh: () -> Unit,
    val setTheme: (com.morrowviento.morrow.data.settings.ThemeMode) -> Unit,
    /** 换主题色（强调色）：同时关掉「从背景图取色」，否则用户选了也看不见。 */
    val setAccent: (com.morrowviento.morrow.data.settings.AccentColor) -> Unit,
    /** 切换「从背景图取色」。打开时用已算好的种子色，不需要重新读图。 */
    val setAutoAccent: (Boolean) -> Unit,
    /** 切换底色：丝缎 / 自定义图片（选图片档但还没有图时，UI 会顺手拉起相册）。 */
    val setBackdropMode: (com.morrowviento.morrow.data.settings.BackdropMode) -> Unit,
    /** 选图只解码预览；确认裁剪后才提交背景和取色。 */
    val prepareBackdrop: (android.net.Uri, Float) -> Unit,
    val confirmBackdrop: (android.graphics.Bitmap, com.morrowviento.morrow.data.backdrop.BackdropCrop) -> Unit,
    val cancelBackdrop: () -> Unit,
    /** 移除自定义图，回到丝缎（取色种子一并清掉）。 */
    val clearBackdrop: () -> Unit,
    /** 切换玻璃厚度档位（写该档位的连续值）。 */
    val setGlassFrostLevel: (GlassFrostLevel) -> Unit,
    val setReduceMotion: (Boolean) -> Unit,
    /** 节日皮肤开关（节日当天的背景柔光与标题祝语）。 */
    val setFestivalSkin: (Boolean) -> Unit,
    /** 预览节日柔光：没有预览时切到最近的节日，正在预览时切到下一个，轮完回到关闭。 */
    val cycleFestivalPreview: () -> Unit,
    /** 结束预览：离开设置页时调用，避免首页无缘无故顶着节日祝语。 */
    val clearFestivalPreview: () -> Unit,
    val exportTo: (android.net.Uri) -> Unit,
    val loadImport: (android.net.Uri) -> Unit,
    val confirmImport: (ImportMode) -> Unit,
    val cancelImport: () -> Unit,
    val requestClearAll: () -> Unit,
    val confirmClearAll: () -> Unit,
    val cancelClearAll: () -> Unit,
    val showLicenses: () -> Unit,
    val hideLicenses: () -> Unit,
    /** 打开字体选择面板（顺带重扫一次已导入的字体）。 */
    val showFonts: () -> Unit,
    val hideFonts: () -> Unit,
    /** 选中某款字体；传 [AppFont.SYSTEM] 即回到系统默认。 */
    val selectFont: (AppFont) -> Unit,
    /** 导入用户挑的字体文件；成功后直接切过去。 */
    val importFont: (android.net.Uri) -> Unit,
    /** 删除已导入的字体文件；删的正是当前字体时回落系统默认。 */
    val deleteFont: (AppFont) -> Unit,
    val consumeMessage: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
    /** M7 应用内更新。 */
    val checkUpdate: () -> Unit,
    val downloadUpdate: () -> Unit,
    val installUpdate: () -> Unit,
    val dismissUpdate: () -> Unit,
)

/** 导入结果的用户可读摘要。 */
fun ImportReport.summaryText(): String = buildString {
    when (mode) {
        ImportMode.REPLACE -> append("已覆盖导入 ${importedEvents} 个事件")
        ImportMode.MERGE -> append("已合并 ${importedEvents} 个事件")
    }
    append("，现有 $totalEvents 个")
    if (skippedEvents > 0) append("；$skippedEvents 条损坏已跳过")
    if (!settingsApplied) append("；外观设置保留本机")
}
