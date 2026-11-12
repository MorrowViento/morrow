package com.morrowviento.morrow.ui.settings

import com.morrowviento.morrow.data.backup.BackupDocument
import com.morrowviento.morrow.data.backup.ImportMode
import com.morrowviento.morrow.data.backup.ImportReport
import com.morrowviento.morrow.data.settings.AppSettings
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

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /**
     * 拖动滑块过程中的临时厚度。
     *
     * 滑块每帧都会回调，如果每次都写 DataStore，就是每帧一次落盘 + 一次全树重组，
     * 真机上会明显卡顿。所以拖动只更新这个内存值（[settings] 在 [effectiveSettings]
     * 里被覆盖），松手才写盘。
     */
    val glassFrostPreview: Float? = null,
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
    val confirmClearAll: Boolean = false,
    val showLicenses: Boolean = false,
    /** 一次性提示，展示后由 [SettingsActions.consumeMessage] 清掉。 */
    val message: String? = null,
    /** M7：应用内更新检查 / 下载 / 安装的进度。 */
    val update: UpdateUiState = UpdateUiState(),
) {
    /** 预览优先于已存值：滑块拖动时整页玻璃跟着变厚变薄。 */
    val effectiveSettings: AppSettings
        get() = glassFrostPreview?.let { settings.copy(glassFrost = it) } ?: settings
}

data class SettingsActions(
    /** 页面重新可见时刷新系统权限状态（通知 / 精确闹钟只能在系统界面改）。 */
    val refresh: () -> Unit,
    val setTheme: (com.morrowviento.morrow.data.settings.ThemeMode) -> Unit,
    /** 拖动玻璃厚度滑块时的实时预览（不落盘，松手才写）。 */
    val previewGlassFrost: (Float) -> Unit,
    /** 松手后落盘。 */
    val setGlassFrost: (Float) -> Unit,
    val setReduceMotion: (Boolean) -> Unit,
    val exportTo: (android.net.Uri) -> Unit,
    val loadImport: (android.net.Uri) -> Unit,
    val confirmImport: (ImportMode) -> Unit,
    val cancelImport: () -> Unit,
    val requestClearAll: () -> Unit,
    val confirmClearAll: () -> Unit,
    val cancelClearAll: () -> Unit,
    val showLicenses: () -> Unit,
    val hideLicenses: () -> Unit,
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
