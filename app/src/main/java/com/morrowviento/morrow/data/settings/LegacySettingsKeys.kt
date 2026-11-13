package com.morrowviento.morrow.data.settings

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
    /** 玻璃厚度（0~1 的 Float，滑块直接写这里）。 */
    const val GLASS_FROST = "glass_frost"
    /**
     * 旧版玻璃材质档位（`clear` / `light` / `standard` / `heavy`）。
     *
     * 已由 [GLASS_FROST] 取代，但**继续写**：降级安装旧版本时还能读回来，
     * 老备份导入也靠它迁移。
     */
    const val GLASS_MATERIAL = "glass_material"
    const val REDUCE_MOTION = "reduce_motion"

    /**
     * 主题色（[AccentColor] 的 `prefValue`）。
     *
     * 新键，旧版（Flutter）没有这一项：缺失时回落 [AccentColor.DEFAULT]，
     * 也就是库原来那支蓝，老用户升级后观感不变。
     */
    const val ACCENT_COLOR = "accent_color"

    const val AUTO_CHECK_UPDATE = "auto_check_update"
    const val AVATAR_PATH = "avatar_path"
    const val ONBOARDING_COMPLETED = "onboarding_completed_v1"

    // 更新相关
    const val LAST_UPDATE_CHECK_AT = "last_update_check_at"
    const val SKIPPED_RELEASE_VERSION = "skipped_release_version"

    // 通知
    const val NOTIFICATION_ACTION_REVISION = "notification_action_revision"

    /**
     * 上一次提醒的「计划触发时刻」与「实际投递时刻」（毫秒）。
     *
     * 存下来只为诊断页显示延迟：不精确闹钟被系统推迟是「用户能感觉到、但说不清」的问题，
     * 有了这两个数就能一眼判断「是系统在拖」还是「排期本身算错了」。
     */
    const val LAST_REMINDER_FIRED_AT = "reminder.last_fired_at"
    const val LAST_REMINDER_TRIGGER_AT = "reminder.last_trigger_at"

    /** 通知真正交给系统通知栏的时刻；与 [LAST_REMINDER_FIRED_AT] 之差即应用自身处理耗时。 */
    const val LAST_REMINDER_SHOWN_AT = "reminder.last_shown_at"
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
