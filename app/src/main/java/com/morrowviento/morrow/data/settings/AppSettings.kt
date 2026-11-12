package com.morrowviento.morrow.data.settings

import com.morrowviento.morrow.domain.model.SortMode

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
 * 玻璃材质档位。
 *
 * 对应 Glasmio 的四个材质（`NavigationGlassMaterial`：Clear / RegularSmall /
 * RegularMedium / RegularLarge），也就是示例里 Materials 那四张卡：
 * 厚度依次递增，模糊、内阴影与镜面高光跟着变强，文字对比度也跟着变高。
 *
 * 放在数据层而不是 `ui/glass`：设置要落盘、要进备份，业务层不该为了一个枚举
 * 反向依赖 Glasmio。到渲染层再由 `ui/glass` 映射成库的枚举。
 */
enum class GlassMaterialLevel(
    val prefValue: String,
    /** 分段控件上的短标签。 */
    val label: String,
    /** 设置页里的一句说明。 */
    val hint: String,
) {
    CLEAR("clear", "通透", "几乎没有底色，最能看清背后内容"),
    LIGHT("light", "轻盈", "薄玻璃，背景细节保留最多"),
    STANDARD("standard", "标准", "iOS 默认厚度，日常推荐"),
    HEAVY("heavy", "厚重", "最实心，文字最清晰");

    companion object {
        /** 认不出的值（旧版本写的、被手工改过的）一律回落标准档。 */
        fun fromPref(raw: String?): GlassMaterialLevel =
            entries.firstOrNull { it.prefValue == raw } ?: STANDARD
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
    /**
     * 玻璃材质档位（设置页四段「通透 / 轻盈 / 标准 / 厚重」）。
     *
     * 取代了原来的布尔「减弱透明」开关：[GlassMaterialLevel.HEAVY] 那一档就是
     * 「更实、文字更清晰」，四档比一个开关更能表达「透多少」。
     */
    val glassMaterial: GlassMaterialLevel = GlassMaterialLevel.STANDARD,
    /**
     * 减弱透明（无障碍「降低透明度」）。
     *
     * 不再由 UI 直接写：设置页改成材质档位后，它由 [glassMaterial] 派生
     * （`HEAVY` → true）。保留下来的原因是备份文件与旧版本数据里都是这个键，
     * 删掉会让老备份导入时丢掉这项偏好。
     */
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

    /** 玻璃要不要盖色纱（「厚重」档或旧的无障碍开关任一成立）。 */
    val usesOpaqueGlass: Boolean
        get() = glassMaterial == GlassMaterialLevel.HEAVY || reduceTransparency
}
