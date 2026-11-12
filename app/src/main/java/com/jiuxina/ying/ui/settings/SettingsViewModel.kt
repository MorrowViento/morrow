package com.jiuxina.ying.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.backup.BackupCodec
import com.jiuxina.ying.data.backup.BackupFiles
import com.jiuxina.ying.data.backup.BackupParse
import com.jiuxina.ying.data.backup.BackupRepository
import com.jiuxina.ying.data.backup.ImportMode
import com.jiuxina.ying.data.reminder.ReminderPermissions
import com.jiuxina.ying.data.reminder.ReminderScheduler
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.data.settings.ThemeMode
import com.jiuxina.ying.data.widget.WidgetRefreshTrigger
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.widget.PlacedWidget
import com.jiuxina.ying.widget.WidgetConfigStore
import com.jiuxina.ying.widget.WidgetEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置中心 ViewModel（M6）。
 *
 * 状态分两半：**持久设置**来自 DataStore 的 Flow（改完立刻回流，界面无需本地副本），
 * **瞬时状态**（忙碌、待确认导入、一次性提示）放在 [local]。
 * 两者在 [state] 里合成，避免 UI 里再拼一遍。
 */
class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val events: EventRepository,
    private val reminders: ReminderScheduler,
    private val permissions: ReminderPermissions,
    private val widgetRefresh: WidgetRefreshTrigger,
) : ViewModel() {

    private val local = MutableStateFlow(
        SettingsUiState(
            notificationAllowed = permissions.notificationsAllowed(),
            exactAlarmAllowed = permissions.exactAlarmsAllowed(),
            appVersion = readVersion(),
        ),
    )

    val state: StateFlow<SettingsUiState> = combine(settings.appSettings, local) { stored, transient ->
        transient.copy(settings = stored)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        refreshWidgets()
    }

    /**
     * 从系统设置页返回后刷新权限状态。
     *
     * 权限是**在系统界面改的**，本进程收不到任何回调；只能靠页面重新可见时主动读一次。
     * 小部件列表同理：用户可能刚在桌面加了一个，回来要能立刻看到。
     */
    fun refreshPermissions() {
        local.update {
            it.copy(
                notificationAllowed = permissions.notificationsAllowed(),
                exactAlarmAllowed = permissions.exactAlarmsAllowed(),
            )
        }
        refreshWidgets()
    }

    /**
     * 重读桌面上的小部件实例。
     *
     * 单事件小部件要显示它绑了哪个事件：配置里存的是事件 id，标题得回事件表查，
     * 所以这一步是异步的。查不到（事件已删 / 还没绑）就留空，UI 显示「未选择事件」。
     */
    fun refreshWidgets() {
        viewModelScope.launch {
            val placed = runCatching {
                val overview = WidgetEntries.ids(appContext, isDetail = false)
                    .map { PlacedWidget(it, isDetail = false) }
                val allEvents = events.loadAll()
                val store = WidgetConfigStore(settings)
                val detail = WidgetEntries.ids(appContext, isDetail = true).map { id ->
                    val bound = store.load(id).eventId
                    PlacedWidget(
                        id = id,
                        isDetail = true,
                        eventTitle = allEvents.firstOrNull { it.id == bound }?.title,
                    )
                }
                overview + detail
            }.getOrDefault(emptyList())
            local.update { it.copy(widgets = placed) }
        }
    }

    // ---- 外观 ----

    fun setTheme(mode: ThemeMode) = persist { settings.setThemeMode(mode) }

    fun setReduceTransparency(enabled: Boolean) = persist { settings.setReduceTransparency(enabled) }

    fun setReduceMotion(enabled: Boolean) = persist { settings.setReduceMotion(enabled) }

    // ---- 导出 / 导入 ----

    fun exportTo(uri: Uri) = runTask(failure = "导出失败") {
        val json = backup.export(appVersion = local.value.appVersion)
        BackupFiles.write(appContext, uri, json)
        val count = events.count()
        local.update { it.copy(message = "已导出 $count 个事件到所选位置") }
    }

    /** 只解析不写入：用户还要在「覆盖 / 合并」之间选一次。 */
    fun loadImport(uri: Uri) = runTask(failure = "读取备份失败") {
        val text = BackupFiles.read(appContext, uri)
        when (val parsed = BackupCodec.decode(text)) {
            is BackupParse.Failure -> local.update { it.copy(message = parsed.reason) }
            is BackupParse.Ok -> local.update {
                it.copy(
                    pendingImport = PendingImport(parsed.document, parsed.skippedEvents),
                    message = null,
                )
            }
        }
    }

    fun confirmImport(mode: ImportMode) = runTask(failure = "导入失败") {
        val pending = local.value.pendingImport ?: return@runTask
        local.update { it.copy(pendingImport = null) }

        val before = events.loadAll()
        val report = backup.import(pending.document, mode, pending.skippedEvents)
        reconcile(before)
        local.update { it.copy(message = report.summaryText()) }
    }

    fun cancelImport() = local.update { it.copy(pendingImport = null) }

    // ---- 清空 ----

    fun requestClearAll() = local.update { it.copy(confirmClearAll = true) }

    fun cancelClearAll() = local.update { it.copy(confirmClearAll = false) }

    fun confirmClearAll() = runTask(failure = "清除失败") {
        val before = events.loadAll()
        backup.clearAllData()
        reconcile(before)
        local.update { it.copy(confirmClearAll = false, message = "已清除全部数据") }
    }

    // ---- 杂项 ----

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun showLicenses() = local.update { it.copy(showLicenses = true) }

    fun hideLicenses() = local.update { it.copy(showLicenses = false) }

    fun openNotificationSettings() = permissions.openNotificationSettings()

    fun openExactAlarmSettings() = permissions.openExactAlarmSettings()

    // ---- 内部 ----

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    /**
     * 统一处理「忙 → 干活 → 复位」与错误提示。
     *
     * 异常信息直接拼进提示条：这些失败几乎都来自文件系统（空间不足、云盘离线、权限被收回），
     * 用户看得到具体原因才可能自己解决，吞掉只会变成「点了没反应」。
     */
    private fun runTask(failure: String, block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (t: Throwable) {
                local.update { it.copy(message = "$failure：${t.message ?: "未知错误"}") }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * 导入 / 清空之后的对账。
     *
     * `syncAll` 只对传入的事件重排，**不会**清掉已被删除事件的残留闹钟，
     * 所以先按 id 差集逐个取消，再整体重排；小部件同理要整表刷新。
     */
    private suspend fun reconcile(before: List<CountdownEvent>) {
        val after = events.loadAll()
        val alive = after.mapTo(HashSet()) { it.id }
        withContext(Dispatchers.IO) {
            before.forEach { event -> if (event.id !in alive) reminders.cancel(event.id) }
            reminders.syncAll(after)
            widgetRefresh.refreshAll()
        }
    }

    private fun readVersion(): String = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val name = info.versionName ?: "unknown"
        "${name} (${info.longVersionCode})"
    }.getOrDefault("")

    companion object {
        fun factory(
            context: Context,
            settings: SettingsRepository,
            backup: BackupRepository,
            events: EventRepository,
            reminders: ReminderScheduler,
            permissions: ReminderPermissions,
            widgetRefresh: WidgetRefreshTrigger,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(
                    context.applicationContext,
                    settings,
                    backup,
                    events,
                    reminders,
                    permissions,
                    widgetRefresh,
                ) as T
        }
    }
}
