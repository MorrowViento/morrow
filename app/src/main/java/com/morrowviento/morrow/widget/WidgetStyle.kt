package com.morrowviento.morrow.widget

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 小部件样式：预设 + 字号 + 配色 + 元素显隐。
 *
 * 裁剪依据 `docs/TODO.md` 的 M5 范围——旧版那套"逐元素颜色/字号/对齐 + 壁纸取色 + 相册背景
 * + 节日皮肤"没有迁移，只保留能表达同一意图的最小集合：三档预设、三档字号、四套配色、
 * 三个元素开关。**纯数据 + 纯函数**，颜色用 ARGB Int 而不是 `android.graphics.Color`，
 * 因此可以在 JVM 单测里直接断言。
 */

/** 预设决定行数与默认显隐；用户仍可单独覆盖显隐开关（切预设时回到该预设的默认）。 */
enum class WidgetPreset(
    val label: String,
    val rows: Int,
    val defaultShowCategory: Boolean,
    val defaultShowDate: Boolean,
    val defaultShowProgress: Boolean,
) {
    MINIMAL("极简", rows = 1, defaultShowCategory = false, defaultShowDate = false, defaultShowProgress = false),
    STANDARD("标准", rows = 3, defaultShowCategory = true, defaultShowDate = true, defaultShowProgress = false),
    DETAILED("详细", rows = 5, defaultShowCategory = true, defaultShowDate = true, defaultShowProgress = true),
}

/** 字号档位。小部件是远距离阅读，最小档也不能低于 20sp 的大数字。 */
enum class WidgetFontScale(
    val label: String,
    val badgeSp: Float,
    val titleSp: Float,
    val metaSp: Float,
) {
    SMALL("小", badgeSp = 20f, titleSp = 13f, metaSp = 10f),
    MEDIUM("中", badgeSp = 26f, titleSp = 15f, metaSp = 11f),
    LARGE("大", badgeSp = 34f, titleSp = 17f, metaSp = 13f),
}

/**
 * 配色。
 *
 * [SYSTEM] 不是一种颜色，而是"按系统深浅色在 [WIDGET_BG_DARK] / [WIDGET_BG_LIGHT] 之间切"，
 * 由 [WidgetStyle.colors] 在刷新时判断——RemoteViews 读不到 `values-night`，
 * 所以明暗必须在代码里定，不能靠资源限定符。
 */
enum class WidgetPalette(val label: String) {
    SYSTEM("跟随系统"),
    DARK("深色"),
    LIGHT("浅色"),
    ACCENT("品牌色"),
}

/** 一次刷新要套到 RemoteViews 上的具体颜色与底色资源。 */
data class WidgetColors(
    /** 圆角底 drawable 的资源 id（由调用方把 R.drawable.xxx 传进来，故用 Int）。 */
    val backgroundRes: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    /** 进度条着色用。 */
    val accent: Int,
)

data class WidgetStyle(
    val preset: WidgetPreset = WidgetPreset.STANDARD,
    val fontScale: WidgetFontScale = WidgetFontScale.MEDIUM,
    val palette: WidgetPalette = WidgetPalette.SYSTEM,
    val showCategory: Boolean = WidgetPreset.STANDARD.defaultShowCategory,
    val showDate: Boolean = WidgetPreset.STANDARD.defaultShowDate,
    val showProgress: Boolean = WidgetPreset.STANDARD.defaultShowProgress,
    /** 仅单事件小部件使用：绑定的事件 id。为 null 表示还没选（UI 显示引导文案）。 */
    val eventId: String? = null,
) {
    /** 总览小部件显示几行。 */
    val rows: Int get() = preset.rows

    /** 切换预设：行数与显隐一起回到该预设默认，避免"极简 + 显示 5 行"这种自相矛盾的组合。 */
    fun withPreset(next: WidgetPreset): WidgetStyle = copy(
        preset = next,
        showCategory = next.defaultShowCategory,
        showDate = next.defaultShowDate,
        showProgress = next.defaultShowProgress,
    )

    /**
     * 解析出实际颜色。
     *
     * @param systemDark 系统当前是否深色（`Configuration.UI_MODE_NIGHT_YES`）
     * @param backgroundDark 深色底资源 id
     * @param backgroundLight 浅色底资源 id
     * @param backgroundAccent 品牌渐变底资源 id
     */
    fun colors(
        systemDark: Boolean,
        backgroundDark: Int,
        backgroundLight: Int,
        backgroundAccent: Int,
    ): WidgetColors {
        val dark = when (palette) {
            WidgetPalette.SYSTEM -> systemDark
            WidgetPalette.DARK -> true
            WidgetPalette.LIGHT -> false
            WidgetPalette.ACCENT -> true // 品牌渐变按深色处理：白字
        }
        return if (dark) {
            WidgetColors(
                backgroundRes = if (palette == WidgetPalette.ACCENT) backgroundAccent else backgroundDark,
                textPrimary = WHITE,
                textSecondary = WHITE_62,
                accent = WHITE_85,
            )
        } else {
            WidgetColors(
                backgroundRes = backgroundLight,
                textPrimary = INK,
                textSecondary = INK_60,
                accent = INK_80,
            )
        }
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val WHITE_62 = 0x9EFFFFFF.toInt()
        const val WHITE_85 = 0xD9FFFFFF.toInt()
        const val INK = 0xFF1C1C1E.toInt()
        const val INK_60 = 0x993C3C43.toInt()
        const val INK_80 = 0xCC3C3C43.toInt()

        /** 旧版样式键的候选名。旧 Flutter 工程的键名无法从本机源码确认，故按前缀兜底匹配。 */
        val LEGACY_KEY_CANDIDATES = listOf(
            "widget_style_preset",
            "widget_preset",
            "widget_font_scale",
            "widget_theme",
            "widget_show_category",
            "widget_show_date",
            "widget_show_progress",
        )
    }
}

/**
 * 样式的 JSON 编解码（DataStore 单键，一个 widgetId 一条）。
 *
 * 逐字段容错而不是整包 Gson 反序列化：Gson 遇到缺失字段会把 null 塞进 non-null 属性，
 * 运行时才炸；这里每个字段都独立回落默认值，旧版本追加/删除字段都不会让配置整包失效。
 */
object WidgetStyleCodec {

    fun encode(style: WidgetStyle): String {
        val root = JsonObject()
        root.addProperty("preset", style.preset.name)
        root.addProperty("font", style.fontScale.name)
        root.addProperty("palette", style.palette.name)
        root.addProperty("showCategory", style.showCategory)
        root.addProperty("showDate", style.showDate)
        root.addProperty("showProgress", style.showProgress)
        style.eventId?.let { root.addProperty("eventId", it) }
        return root.toString()
    }

    /** @return null 表示无法解析，调用方回落 [WidgetStyle] 默认值。 */
    fun decode(raw: String?): WidgetStyle? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val preset = obj.enumOrNull<WidgetPreset>("preset") ?: WidgetPreset.STANDARD
        return WidgetStyle(
            preset = preset,
            fontScale = obj.enumOrNull<WidgetFontScale>("font") ?: WidgetFontScale.MEDIUM,
            palette = obj.enumOrNull<WidgetPalette>("palette") ?: WidgetPalette.SYSTEM,
            showCategory = obj.boolOrNull("showCategory") ?: preset.defaultShowCategory,
            showDate = obj.boolOrNull("showDate") ?: preset.defaultShowDate,
            showProgress = obj.boolOrNull("showProgress") ?: preset.defaultShowProgress,
            eventId = obj.get("eventId")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString,
        )
    }

    /**
     * 旧版配置映射。
     *
     * 本机没有旧 Flutter 小部件的源码，键名与取值都无法确认，因此这里是**尽力而为**：
     * 认得出来的值就映射，认不出来就回落默认（不清空、不抛错）。
     * 将来若拿到旧键的真实形态，只需改这张表，不用动样式模型。
     */
    fun fromLegacy(values: Map<String, String?>): WidgetStyle {
        val preset = when (values["widget_style_preset"]?.lowercase() ?: values["widget_preset"]?.lowercase()) {
            "minimal", "simple", "compact" -> WidgetPreset.MINIMAL
            "detail", "detailed", "full" -> WidgetPreset.DETAILED
            "standard", "normal" -> WidgetPreset.STANDARD
            else -> WidgetPreset.STANDARD
        }
        val font = when (values["widget_font_scale"]?.lowercase()) {
            "small", "s" -> WidgetFontScale.SMALL
            "large", "big", "l" -> WidgetFontScale.LARGE
            else -> WidgetFontScale.MEDIUM
        }
        val palette = when (values["widget_theme"]?.lowercase()) {
            "system", "auto" -> WidgetPalette.SYSTEM
            "dark" -> WidgetPalette.DARK
            "light" -> WidgetPalette.LIGHT
            "accent", "brand", "colorful" -> WidgetPalette.ACCENT
            else -> WidgetPalette.SYSTEM
        }
        // 先按预设确定默认显隐，再让旧版显式开关覆盖它——否则 withPreset 会把旧值抹掉
        val base = WidgetStyle(preset = preset, fontScale = font, palette = palette).withPreset(preset)
        return base.copy(
            showCategory = values.legacyBool("widget_show_category") ?: base.showCategory,
            showDate = values.legacyBool("widget_show_date") ?: base.showDate,
            showProgress = values.legacyBool("widget_show_progress") ?: base.showProgress,
        )
    }

    /**
     * 旧版布尔值：Dart 侧存的是 Boolean 真值，经 `putRaw` 原样搬家后仍是 Boolean；
     * 这里统一转成文本再解析，因此 `true` / `"true"` / `"1"` 都能认。
     */
    private fun Map<String, String?>.legacyBool(key: String): Boolean? =
        when (val raw = this[key]?.trim()?.lowercase()) {
            null, "" -> null
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }

    private inline fun <reified E : Enum<E>> JsonObject.enumOrNull(key: String): E? {
        val name = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: return null
        return enumValues<E>().firstOrNull { it.name == name }
    }

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
}
