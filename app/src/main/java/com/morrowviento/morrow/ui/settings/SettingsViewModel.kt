package com.morrowviento.morrow.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.backup.BackupCodec
import com.morrowviento.morrow.data.backup.BackupFiles
import com.morrowviento.morrow.data.backup.BackupParse
import com.morrowviento.morrow.data.backup.BackupRepository
import com.morrowviento.morrow.data.backup.ImportMode
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.reminder.ReminderPermissions
import com.morrowviento.morrow.data.reminder.ReminderScheduler
import com.morrowviento.morrow.data.settings.AccentColor
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.data.settings.BackdropSettings
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.data.widget.WidgetRefreshTrigger
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.service.update.ApkDownloader
import com.morrowviento.morrow.service.update.ApkInstaller
import com.morrowviento.morrow.service.update.UpdatePolicy
import com.morrowviento.morrow.service.update.UpdateRepository
import com.morrowviento.morrow.ui.update.UpdateStage
import com.morrowviento.morrow.ui.update.UpdateUiState
import com.morrowviento.morrow.widget.WidgetConfigStore
import com.morrowviento.morrow.widget.WidgetEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
     * 重读桌面上的小部件实例，并合并成设置页的行。
     *
     * 单事件小部件要显示它绑了哪个事件：配置里存的是事件 id，标题得回事件表查，
     * 所以这一步是异步的。查不到（事件已删 / 还没绑）就留空，UI 显示「未选择事件」。
     *
     * **同类合并**：总览卡片没有可区分的信息，桌面上放两个就会列出两行一模一样的
     * 「总览 / 编辑」（真机反馈），既分不清也选不错。索性一类只给一行，保存时对该类
     * 所有实例一起生效。单事件按绑定的事件分组，绑同一事件的多张卡片也合成一行。
     */
    fun refreshWidgets() {
        viewModelScope.launch {
            val placed = runCatching {
                val allEvents = events.loadAll()
                val store = WidgetConfigStore(settings)
                val overviewIds = WidgetEntries.ids(appContext, isDetail = false)
                val detailBindings = WidgetEntries.ids(appContext, isDetail = true)
                    .associateWith { id -> store.load(id).eventId }
                WidgetEntries.rows(overviewIds, detailBindings) { eventId ->
                    allEvents.firstOrNull { it.id == eventId }?.title
                }
            }.getOrDefault(emptyList())
            local.update { it.copy(widgets = placed) }
        }
    }

    // ---- 外观 ----

    /**
     * 切明暗：写盘后刷新小部件。
     *
     * 小部件「跟随系统」时按系统的深浅画背景，这里改了应用内主题反而不该动它；
     * 但「主题色」档与明暗是同一套配色，跟着刷一次不会有副作用，
     * 反而能保证「改完主题回桌面，小部件和 App 是同一套颜色」。
     */
    fun setTheme(mode: ThemeMode) = persist {
        settings.setThemeMode(mode)
        refreshSavedSettingsWidgets()
    }

    /** 切换玻璃厚度档位：直接写该档位的连续值（分段控件没有拖动预览这回事）。 */
    fun setGlassFrostLevel(level: GlassFrostLevel) = persist { settings.setGlassFrost(level.frost) }

    fun setReduceTransparency(enabled: Boolean) = persist { settings.setReduceTransparency(enabled) }

    fun setReduceMotion(enabled: Boolean) = persist { settings.setReduceMotion(enabled) }

    /**
     * 换配色：一处写入，`appSettings` 一变，整棵树（含底栏与所有浮层）跟着重建。
     *
     * 桌面上的小部件**不会**因为我们改了 DataStore 就自己重绘——它只认
     * `AppWidgetManager` 推过去的 RemoteViews。不主动刷一次，用户改完配色回到桌面
     * 还是旧颜色，看起来就像「设置没生效」。
     *
     * 同时**关掉「从背景图取色」**：用户是手动点了一个色点，如果取色开关还开着，
     * 实际配色仍由图片决定，点了没反应——这是「设置了但看不出来」的经典成因。
     */
    fun setAccent(color: AccentColor) = persist {
        val current = settings.appSettings.first().backdrop
        // 一次落盘：关取色与换强调色写在同一次 edit 里，主题子树只重建一次
        settings.setAccentColor(color, current.copy(autoTint = false))
        refreshSavedSettingsWidgets()
    }

    /** 打开 / 关闭「从背景图取色」。种子上次选图时已经算好，这里不重新读图。 */
    fun setAutoAccent(enabled: Boolean) = persist {
        val current = settings.appSettings.first().backdrop
        if (current.tintSeed == 0L) {
            // 没有种子（没选过图 / 图已失效）：开关拨不动，给一句提示而不是静默失败
            local.update { it.copy(message = "先选一张背景图，才能从它取色") }
            return@persist
        }
        settings.setBackdrop(current.copy(autoTint = enabled))
        refreshSavedSettingsWidgets()
    }

    /** 切换底色：丝缎 / 图片。切到图片但没有图时不做任何事（由 UI 拉起相册）。 */
    fun setBackdropMode(mode: BackdropMode) = persist {
        val current = settings.appSettings.first().backdrop
        if (mode == BackdropMode.IMAGE && !current.hasImage) return@persist
        settings.setBackdrop(current.copy(mode = mode))
        refreshSavedSettingsWidgets()
    }

    fun prepareBackdrop(uri: Uri, aspectRatio: Float) = runTask(failure = "读取背景失败") {
        require(aspectRatio.isFinite() && aspectRatio > 0f)
        val bitmap = BackdropImage.prepare(appContext, uri)
        try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            local.update { it.copy(pendingBackdrop = PendingBackdrop(bitmap, aspectRatio), message = null) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            bitmap.recycle()
            throw cancelled
        }
    }

    fun cancelBackdrop() {
        if (!local.value.busy) local.update { it.copy(pendingBackdrop = null) }
    }

    fun confirmBackdrop(bitmap: android.graphics.Bitmap, crop: com.morrowviento.morrow.data.backdrop.BackdropCrop) =
        runTask(failure = "设置背景失败") {
        val pending = local.value.pendingBackdrop ?: return@runTask
        if (pending.bitmap !== bitmap) return@runTask
        require(kotlin.math.abs(crop.width / crop.height - pending.aspectRatio) < 0.001f)
        val imported = BackdropImage.importCropped(appContext, bitmap, crop)
        var committed = false
        try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val current = settings.snapshotSettings().backdrop
            val next = current.copy(
                mode = BackdropMode.IMAGE,
                image = imported.name,
                autoTint = if (current.hasImage) current.autoTint else true,
                tintSeed = if (imported.seed != 0L) imported.seed else current.tintSeed,
            )
            withContext(kotlinx.coroutines.NonCancellable) {
                settings.setBackdrop(next)
                committed = true
                BackdropImage.retain(appContext, imported.name)
            }
            refreshSavedSettingsWidgets()
            local.update {
                it.copy(pendingBackdrop = null, message = when {
                    imported.seed == 0L -> "背景已换；这张图取不出颜色，配色保持不变"
                    next.autoTint -> "背景已换，配色已按图片取色"
                    else -> "背景已换，保留手选配色"
                })
            }
        } finally {
            if (!committed) BackdropImage.remove(appContext, imported.name)
        }
    }

    /** 移除自定义背景图：回到丝缎，取色种子一并清掉（留着会继续影响配色）。 */
    fun clearBackdrop() = runTask(failure = "移除背景失败") {
        local.update { it.copy(pendingBackdrop = null) }
        withContext(kotlinx.coroutines.NonCancellable) {
            settings.setBackdrop(BackdropSettings())
            BackdropImage.clear(appContext)
        }
        refreshSavedSettingsWidgets()
        local.update { it.copy(message = "已回到丝缎背景") }
    }

    private suspend fun refreshSavedSettingsWidgets() {
        try {
            widgetRefresh.refreshAll()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("MorrowSettings", "设置已保存，小部件刷新失败", error)
        }
    }

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

        val report = backup.import(pending.document, mode, pending.skippedEvents, reconcile = ::reconcile)
        local.update { it.copy(message = report.summaryText()) }
    }

    fun cancelImport() = local.update { it.copy(pendingImport = null) }

    // ---- 清空 ----

    fun requestClearAll() = local.update { it.copy(confirmClearAll = true) }

    fun cancelClearAll() = local.update { it.copy(confirmClearAll = false) }

    fun confirmClearAll() = runTask(failure = "清除失败") {
        backup.clearAllData { before ->
            local.update { it.copy(pendingBackdrop = null) }
            // 与导入/背景设置串行化，提交成功后才清理独立图片文件。
            BackdropImage.clear(appContext)
            reconcile(before)
        }
        local.update { it.copy(confirmClearAll = false, message = "已清除全部数据") }
    }

    // ---- 杂项 ----

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun showLicenses() = local.update { it.copy(showLicenses = true) }

    fun hideLicenses() = local.update { it.copy(showLicenses = false) }

    fun openNotificationSettings() = permissions.openNotificationSettings()

    fun openExactAlarmSettings() = permissions.openExactAlarmSettings()

    // ---- M7 应用内更新 ----

    private val updateRepository = UpdateRepository()
    private val apkDownloader = ApkDownloader(appContext)
    private var downloadedApk: File? = null

    /**
     * 检查更新：多线路并发探测，取版本号最大的一份。
     *
     * 手动触发而不是冷启动自动跑：更新检查要联网、有耗时，静默失败只会变成
     * 「偶尔弹个错误」；放在设置页由用户主动发起，失败原因也能直接看到。
     */
    fun checkUpdate() {
        if (local.value.update.busy) return
        val versionLabel = local.value.appVersion
        local.update {
            it.copy(update = UpdateUiState(stage = UpdateStage.CHECKING), message = null)
        }
        viewModelScope.launch {
            val result = try {
                updateRepository.check()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val release = result?.release
            val abis = Build.SUPPORTED_ABIS.toList()
            val localCode = localVersionCode()
            when {
                release == null -> failUpdate("检查更新失败，请检查网络后重试")
                !UpdatePolicy.isUpdateAvailable(release, localCode, abis) -> local.update {
                    it.copy(
                        update = UpdateUiState(),
                        message = "已是最新版本${if (versionLabel.isBlank()) "" else "（$versionLabel）"}",
                    )
                }
                else -> local.update {
                    it.copy(
                        update = UpdateUiState(
                            stage = UpdateStage.AVAILABLE,
                            release = release,
                            asset = UpdatePolicy.selectAsset(release.assets, abis),
                        ),
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val asset = local.value.update.asset ?: return
        if (local.value.update.busy) return
        local.update {
            it.copy(
                update = it.update.copy(
                    stage = UpdateStage.DOWNLOADING,
                    doneBytes = 0L,
                    totalBytes = asset.size,
                    message = null,
                ),
            )
        }
        viewModelScope.launch {
            try {
                val file = apkDownloader.download(asset) { done, total ->
                    // 回调在 IO 线程，StateFlow.update 本身是线程安全的
                    local.update { current ->
                        current.copy(update = current.update.copy(doneBytes = done, totalBytes = total))
                    }
                }
                downloadedApk = file
                local.update { it.copy(update = it.update.copy(stage = UpdateStage.READY)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failUpdate("下载失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun installUpdate() {
        val apk = downloadedApk
        if (apk == null || !apk.isFile) {
            failUpdate("安装包已失效，请重新下载")
            return
        }
        if (local.value.update.busy) return
        local.update { it.copy(update = it.update.copy(stage = UpdateStage.INSTALLING)) }
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { ApkInstaller.install(appContext, apk) }
                if (ok) {
                    local.update {
                        it.copy(update = it.update.copy(stage = UpdateStage.READY, message = "安装请求已提交，请在系统界面确认；可关闭此面板"))
                    }
                } else {
                    failUpdate("没能启动安装，请稍后重试或用系统安装器打开 ${apk.name}")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failUpdate("启动安装失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun dismissUpdate() {
        if (!local.value.update.busy) local.update { it.copy(update = UpdateUiState()) }
    }

    private fun failUpdate(reason: String) {
        local.update {
            it.copy(update = it.update.copy(stage = UpdateStage.FAILED, message = reason))
        }
    }

    /** 读不到本机版本时返回极大值：宁可判「已是最新」，也不能把旧包当新版本反复提示。 */
    private fun localVersionCode(): Int = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(Int.MAX_VALUE)

    // ---- 内部 ----

    private val settingsMutation = kotlinx.coroutines.sync.Mutex()

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                settingsMutation.withLock { withContext(Dispatchers.IO) { block() } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                local.update { it.copy(message = "保存设置失败：${error.message ?: "未知错误"}") }
            }
        }
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
                settingsMutation.withLock { withContext(Dispatchers.IO) { block() } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                local.update { it.copy(message = "$failure：${error.message ?: "未知错误"}") }
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
            try {
                before.forEach { event -> if (event.id !in alive) reminders.cancel(event.id) }
                reminders.syncAll(after)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MorrowSettings", "数据已保存，提醒对账失败", error)
            }
            refreshSavedSettingsWidgets()
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
