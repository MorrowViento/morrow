package com.morrowviento.morrow.data.legacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.morrowviento.morrow.data.db.EventDao
import com.morrowviento.morrow.data.db.EventMapper
import com.morrowviento.morrow.data.settings.MigrationKeys
import com.morrowviento.morrow.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * 旧版（Flutter）数据迁移。
 *
 * 迁移范围：
 * 1. `flutter.countdown_events_v1` JSON 数组 → Room `events` 表
 * 2. 其余 `flutter.*` 键值 → DataStore（键名去掉前缀，类型原样保留）
 *
 * 幂等保证：完成后写入 [MigrationKeys.DONE_V1] 标记，二次启动直接跳过。
 * 失败策略：单条事件损坏只跳过该条并计数，不阻断整体迁移。
 */
class LegacyDataMigrator(
    private val context: Context,
    private val eventDao: EventDao,
    private val settings: SettingsRepository,
) {

    data class Report(
        val eventsImported: Int,
        val failedEvents: Int,
        val settingsImported: Int,
        val skipped: Boolean,
        val error: String? = null,
    )

    private val gson = Gson()

    /** Flutter 版写入的 SharedPreferences 文件名。 */
    private fun legacyPrefs(): SharedPreferences? =
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun migrateIfNeeded(): Report {
        // rawString 是 Flow，这里一次性读取判断，避免依赖 collect 生命周期
        val alreadyDone = settings.rawString(MigrationKeys.DONE_V1).first() != null
        if (alreadyDone) return Report(0, 0, 0, skipped = true)

        val prefs = legacyPrefs() ?: return Report(0, 0, 0, skipped = false)
        val raw = prefs.all.filterValues { it != null }
        if (raw.isEmpty()) {
            // 全新安装：直接落标记，避免每次启动重复扫描
            settings.putBoolean(MigrationKeys.DONE_V1, true)
            return Report(0, 0, 0, skipped = false)
        }

        return runCatching {
            val split = LegacyPrefsSplitter.split(raw)
            val parseResult = LegacyEventParser.parse(split.eventsJson)

            if (parseResult.events.isNotEmpty()) {
                val entities = parseResult.events.map { EventMapper.toEntity(it) }
                eventDao.replaceAll(entities)
            }

            var importedSettings = 0
            for ((key, value) in split.settings) {
                if (settings.putRaw(key, value)) importedSettings++
            }

            val report = Report(
                eventsImported = parseResult.events.size,
                failedEvents = parseResult.failed,
                settingsImported = importedSettings,
                skipped = false,
            )
            settings.putString(MigrationKeys.REPORT_V1, gson.toJson(report))
            // 标记放最后：中途失败不会写入，下次启动重试
            settings.putBoolean(MigrationKeys.DONE_V1, true)
            Log.i(TAG, "旧数据迁移完成: $report")
            report
        }.getOrElse { error ->
            Log.e(TAG, "旧数据迁移失败，将在下次启动重试", error)
            Report(0, 0, 0, skipped = false, error = error.message)
        }
    }

    companion object {
        private const val TAG = "LegacyDataMigrator"
        const val LEGACY_PREFS_NAME = "FlutterSharedPreferences"
    }
}
