package com.jiuxina.ying.data.settings

/**
 * Flutter 版 SharedPreferences 的键名（已去掉 `flutter.` 前缀）。
 * 迁移时逐键搬运，M4/M5/M6 各里程碑按需读取，避免二次迁移。
 */
object LegacySettingsKeys {
    // 事件数据（迁入 Room，不进 DataStore）
    const val EVENTS = "countdown_events_v1"

    // 通用
    const val THEME_MODE = "theme_mode"
    const val EVENT_SORT_MODE = "event_sort_mode"
    const val REDUCE_TRANSPARENCY = "reduce_transparency"
    const val REDUCE_MOTION = "reduce_motion"
    const val AUTO_CHECK_UPDATE = "auto_check_update"
    const val AVATAR_PATH = "avatar_path"
    const val ONBOARDING_COMPLETED = "onboarding_completed_v1"

    // 更新相关
    const val LAST_UPDATE_CHECK_AT = "last_update_check_at"
    const val SKIPPED_RELEASE_VERSION = "skipped_release_version"

    // 通知
    const val NOTIFICATION_ACTION_REVISION = "notification_action_revision"
}

/** 迁移自身使用的元数据键（不属于旧版数据，前缀区分）。 */
object MigrationKeys {
    const val DONE_V1 = "migration.legacy_v1_done"
    const val REPORT_V1 = "migration.legacy_v1_report"
}

/** 旧版主题模式取值（Dart enum name）。 */
enum class ThemeModePref { system, light, dark }

/** 旧版排序模式取值（Dart enum name）。 */
enum class SortModePref { distance, targetDate, createdAt }
