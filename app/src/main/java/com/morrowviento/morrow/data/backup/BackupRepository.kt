package com.morrowviento.morrow.data.backup

import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.category.CategoryCodec
import com.morrowviento.morrow.data.category.CategoryRepository
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.widget.WidgetConfigStore
import java.time.LocalDateTime

/** 导入策略。 */
enum class ImportMode {
    /** 合并：按 id 覆盖同名事件，其余保留；设置与小部件样式不动。 */
    MERGE,

    /** 覆盖：清空后整套替换，含设置与小部件样式。 */
    REPLACE,
}

/** 导入结果，全部字段都直接用于界面文案，不做二次加工。 */
data class ImportReport(
    val mode: ImportMode,
    /** 备份里成功解析并写入的事件数。 */
    val importedEvents: Int,
    /** 导入后本机事件总数。 */
    val totalEvents: Int,
    /** 解析阶段被跳过的坏条目（备份本身有问题，不是本机问题）。 */
    val skippedEvents: Int,
    val categories: Int,
    val widgetStyles: Int,
    val settingsApplied: Boolean,
)

/**
 * 备份导出 / 导入（M6）。
 *
 * 只用 SAF（`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`）读写，
 * 不申请任何存储权限：Android 13 起 `WRITE_EXTERNAL_STORAGE` 已无效，
 * 走 SAF 才能既访问用户选的位置又不需要运行时权限。
 *
 * 这里只负责「数据怎么进出」。导入后的提醒重排与小部件刷新交给调用方——
 * 那两件事的依赖（调度器、刷新器）不属于数据层，塞进来会让本类变成第二个容器。
 */
class BackupRepository(
    private val settings: SettingsRepository,
    private val events: EventRepository,
    private val undo: com.morrowviento.morrow.data.undo.UndoRepository,
) {

    // ---------------------------------------------------------------- 导出

    suspend fun export(appVersion: String, now: LocalDateTime = LocalDateTime.now()): String {
        val document = BackupDocument(
            exportedAt = now.toString(),
            appVersion = appVersion,
            events = events.loadAll().map(BackupMapper::of),
            categories = readCategories(),
            settings = BackupMapper.ofSettings(settings.snapshotSettings()),
            widgets = readWidgetStyles(),
        )
        return BackupCodec.encode(document)
    }

    // ---------------------------------------------------------------- 导入

    suspend fun import(
        document: BackupDocument,
        mode: ImportMode,
        skipped: Int,
        reconcile: suspend (List<com.morrowviento.morrow.domain.model.CountdownEvent>) -> Unit = {},
    ): ImportReport = events.withMutation {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val before = events.loadAll()
            val incoming = document.events.mapNotNull(BackupMapper::toDomain)
            undo.clear()
            when (mode) {
                ImportMode.REPLACE -> events.replaceAll(incoming)
                // 只写导入的行，不重写本机其它事件。
                ImportMode.MERGE -> events.restore(incoming)
            }

            val categories = when (mode) {
                ImportMode.REPLACE -> document.categories
                ImportMode.MERGE -> BackupMerge.categories(readCategories(), document.categories)
            }
            if (categories.isNotEmpty()) {
                settings.putString(CategoryRepository.KEY_CATEGORIES, CategoryCodec.encode(categories))
            } else if (mode == ImportMode.REPLACE) {
                settings.remove(CategoryRepository.KEY_CATEGORIES)
            }

            // 合并模式保持本机设置与样式；覆盖模式整体替换。
            val settingsApplied = mode == ImportMode.REPLACE
            if (settingsApplied) settings.applySettings(BackupMapper.toSettings(document.settings))
            val widgetStyles = if (settingsApplied) applyWidgetStyles(document.widgets) else 0

            // 回调仍在事件操作锁内，旧快照不能在并发删除后重新排入提醒。
            reconcile(before)
            ImportReport(
                mode = mode,
                importedEvents = incoming.size,
                totalEvents = events.count(),
                skippedEvents = skipped,
                categories = categories.size,
                widgetStyles = widgetStyles,
                settingsApplied = settingsApplied,
            )
        }
    }

    // ---------------------------------------------------------------- 清空

    /**
     * 清除全部本地数据（事件 + 分类 + 小部件样式 + 设置回默认）。
     *
     * 不做撤销：这是「换人用这台机器 / 彻底重来」的动作，留一个 6 秒窗口反而危险。
     * 界面上必须先让用户确认，并提示先导出备份。
     */
    suspend fun clearAllData(
        reconcile: suspend (List<com.morrowviento.morrow.domain.model.CountdownEvent>) -> Unit = {},
    ) = events.withMutation {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val before = events.loadAll()
            undo.clear()
            events.clearAll()
            settings.removeKeys(settings.stringsOnce(WidgetConfigStore.PREFIX).keys + CategoryRepository.KEY_CATEGORIES)
            settings.applySettings(com.morrowviento.morrow.data.settings.AppSettings())
            reconcile(before)
        }
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun readCategories(): List<String> =
        CategoryCodec.decode(settings.stringOnce(CategoryRepository.KEY_CATEGORIES)).orEmpty()

    private suspend fun readWidgetStyles(): List<BackupWidget> =
        settings.stringsOnce(WidgetConfigStore.PREFIX).mapNotNull { (key, style) ->
            val id = WidgetConfigStore.widgetIdOf(key) ?: return@mapNotNull null
            BackupWidget(widgetId = id, style = style)
        }

    private suspend fun applyWidgetStyles(widgets: List<BackupWidget>): Int {
        // 备份里没有小部件样式（旧版本导出的）就别动本机配置——清空反而破坏用户桌面
        if (widgets.isEmpty()) return 0
        // 先清旧键：否则备份里没有的小部件会保留上一台设备的样式
        settings.removeKeys(settings.stringsOnce(WidgetConfigStore.PREFIX).keys)

        val values = widgets.associate { WidgetConfigStore.key(it.widgetId) to it.style }.toMutableMap()
        // 备份不含默认样式键（那是按 widgetId 存的，导出的 id 里解析不出来），
        // 这里用第一份样式补上：换机导入后新添加的小部件才有样式可继承
        values[WidgetConfigStore.KEY_DEFAULT_STYLE] = widgets.first().style
        settings.putStrings(values)
        return widgets.size
    }
}
