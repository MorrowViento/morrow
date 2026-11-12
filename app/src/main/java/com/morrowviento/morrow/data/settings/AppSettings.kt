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
 * 玻璃「厚度」的默认值（= [GlassFrostLevel.STANDARD]）。
 *
 * 0 = 完全不糊（只剩边缘折射），1 = 厚磨砂（背后只剩大色块）。
 * 任何一档都不会变成实心卡片——透明是液态玻璃的前提。
 */
const val GLASS_FROST_DEFAULT = 0.62f

/**
 * 玻璃厚度的四个档位（设置页的图标分段控件）。
 *
 * 底层仍然存 0~1 的连续值 [AppSettings.glassFrost]——连续性留给渲染层（模糊半径、
 * 内侧色纱都能跟着变），UI 只暴露四个有明确名字的落点，免得滑块停在「37%」这种
 * 说不清意思的位置。
 *
 * 档位的取值不是等距的：`frostRadius` 从 0.5dp 到 25.5dp，色纱从 0 到 0.26，人眼对
 * 前段（0→0.35）的差异远比对后段（0.7→1）敏感，所以前两档贴得近、后两档拉得开。
 *
 * [prefValue] 与旧的四档字符串（`clear/light/standard/heavy`）同名，升级时
 * [legacyGlassFrost] 映射过来的值正好落在对应档位的判定区间里。
 */
enum class GlassFrostLevel(
    val frost: Float,
    val prefValue: String,
    val label: String,
    val hint: String,
) {
    CLEAR(0f, "clear", "通透", "几乎不糊，只剩边缘折射，背后内容一清二楚"),
    LIGHT(0.34f, "light", "轻盈", "薄磨砂，背景轮廓还看得见，细节化开"),
    STANDARD(0.62f, "standard", "标准", "背景糊成一片柔光，正文最清楚"),
    HEAVY(0.95f, "heavy", "厚重", "厚磨砂，只剩大色块，能压住最花的背景");

    companion object {
        /** 连续厚度 → 最近的档位（分段控件用它决定哪一格亮着）。 */
        fun nearest(frost: Float): GlassFrostLevel =
            entries.minByOrNull { kotlin.math.abs(it.frost - frost) } ?: STANDARD
    }
}

/**
 * 旧版「材质档位」字符串 → 连续厚度。
 *
 * **为什么要废弃四档**：`NavigationGlassMaterial` 只有四个值，而且
 * `RegularMedium` 与 `RegularLarge` 的五个构造参数（frostRadius / shadowRadius /
 * 明暗阴影 alpha / nativeLightIntensity）**完全一样**；在原生材质通道里两者只差一个
 * `luminanceRollOff`（0.65 与 0.4），落在模糊后的亮度调色上，肉眼几乎看不出来。
 * 四档里真正能分辨的只有「Clear ↔ Regular」，所以调档位时会觉得「没生效」。
 *
 * 现在改成 0~1 的连续值，映射到两个真正有视觉差异的参数：
 * `frostRadius`（模糊半径，0.5dp→25.5dp）与面板内侧色纱的不透明度（0→0.26）。
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
     * 玻璃厚度，0~1 连续值。
     *
     * UI 上是 [GlassFrostLevel] 的四档图标分段控件，落盘时写的是该档位的 [GlassFrostLevel.frost]；
     * 保留连续类型是为了兼容上一版滑块写入的任意值（例如 0.37），
     * 读取时用 [GlassFrostLevel.nearest] 归到最近的档位显示，不悄悄改写用户的设置。
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
