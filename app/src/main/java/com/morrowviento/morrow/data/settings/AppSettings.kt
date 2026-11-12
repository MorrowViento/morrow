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
 * 玻璃「厚度」的默认值。
 *
 * 0 = 几乎全通透（能看清背后内容），1 = 厚实磨砂（iOS 那种实心卡片）。
 * 默认 0.35 接近 iOS 导航栏的观感——既看得出是玻璃，文字也够清楚。
 */
const val GLASS_FROST_DEFAULT = 0.35f

/**
 * 旧版「材质档位」字符串 → 连续厚度。
 *
 * **为什么要废弃四档**：`NavigationGlassMaterial` 只有四个值，而且
 * `RegularMedium` 与 `RegularLarge` 的五个构造参数（frostRadius / shadowRadius /
 * 明暗阴影 alpha / nativeLightIntensity）**完全一样**；在原生材质通道里两者只差一个
 * `luminanceRollOff`（0.65 与 0.4），落在模糊后的亮度调色上，肉眼几乎看不出来。
 * 四档里真正能分辨的只有「Clear ↔ Regular」，所以调档位时会觉得「没生效」。
 *
 * 现在改成 0~1 的连续值，映射到库真正有视觉差异的两个参数：
 * `frostRadius`（模糊半径，2dp→24dp）与面板内侧色纱的不透明度（0→0.8）。
 *
 * 旧值映射到等距的厚度，保证升级后观感不变。
 */
fun legacyGlassFrost(raw: String?): Float? = when (raw) {
    "clear" -> 0f
    "light" -> 0.28f
    "standard" -> 0.55f
    "heavy" -> 0.92f
    else -> null
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
     * 玻璃厚度，0~1 连续可调（设置页的滑块）。
     *
     * 之前是一个四值枚举，实际渲染上只有第一档与其它三档能看出区别
     * （见 [legacyGlassFrost] 的注释），所以换成滑块。
     */
    val glassFrost: Float = GLASS_FROST_DEFAULT,
    /**
     * 减弱透明（无障碍「降低透明度」）。
     *
     * 不再由 UI 直接写：它由 [glassFrost] 派生（厚度 ≥ 0.85 → true），
     * 与系统「降低透明度」的语义对齐。保留下来的原因是备份文件与旧版本数据里
     * 都是这个键，删掉会让老备份导入时丢掉这项偏好。
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

    /** 玻璃要不要盖到接近不透明（最厚档或旧的无障碍开关任一成立）。 */
    val usesOpaqueGlass: Boolean
        get() = glassFrost >= 0.85f || reduceTransparency
}
