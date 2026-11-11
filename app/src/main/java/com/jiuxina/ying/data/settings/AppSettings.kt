package com.jiuxina.ying.data.settings

import com.jiuxina.ying.domain.model.SortMode

/**
 * 外观主题偏好。
 *
 * [prefValue] 与旧版 Dart enum 名一致（`system` / `light` / `dark`），
 * 迁移过来的旧值无需二次映射；[label] 只给设置中心用。
 */
enum class ThemeMode(val prefValue: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        /** 认不出的值（旧版本写的、被手工改过的）一律回落跟随系统。 */
        fun fromPref(raw: String?): ThemeMode =
            entries.firstOrNull { it.prefValue == raw } ?: SYSTEM
    }
}

/**
 * 应用设置的领域快照。
 *
 * 之所以把四个分散的 DataStore 键合成一个对象：设置中心要一次性渲染整页，
 * 备份要一次性取出整组，分别读四个 Flow / 四次 `first()` 会多绕一圈。
 *
 * **纯数据 + 纯函数**，`isDark()` 不依赖 Android 的 `Configuration`，
 * 系统深浅由调用方传入，因此可以在 JVM 单测里直接断言。
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sortMode: SortMode = SortMode.DISTANCE,
    /** 减弱透明：Glasmio 会把玻璃材质降级为不透明面板（无障碍「降低透明度」）。 */
    val reduceTransparency: Boolean = false,
    /** 减弱动效：关闭页面切换的淡入淡出与卡片位移，只保留即时切换。 */
    val reduceMotion: Boolean = false,
) {
    /** 实际渲染是否深色：跟随系统时由 [systemDark] 决定。 */
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
