package com.jiuxina.ying.widget

import com.jiuxina.ying.data.settings.SettingsRepository

/**
 * 小部件配置存储：一个 widgetId 一条 JSON，键名 `widget.<id>.style`。
 *
 * 复用 App 的同一个 DataStore（`ying_settings`）而不是新开文件——DataStore 同一个文件
 * 只允许一个实例，多开会直接抛 "There are multiple DataStores active for the same file"。
 */
class WidgetConfigStore(private val settings: SettingsRepository) {

    /**
     * 读取某个小部件的样式。
     *
     * 顺序：已保存的配置 → 旧版配置映射 → 默认样式。
     * 旧配置按 widgetId 对不上（旧版存的是全局样式），所以只作为**默认值**参与，
     * 不会写回新键；用户点一次"完成"才会落成这一份的具体配置。
     */
    suspend fun load(widgetId: Int): WidgetStyle {
        val raw = settings.stringOnce(key(widgetId))
        if (raw != null) return WidgetStyleCodec.decode(raw) ?: WidgetStyle()
        return legacyDefaults()
    }

    suspend fun save(widgetId: Int, style: WidgetStyle) {
        settings.putString(key(widgetId), WidgetStyleCodec.encode(style))
    }

    suspend fun remove(widgetId: Int) = settings.remove(key(widgetId))

    /** 一次性取全部已保存配置，供批量刷新时少读几次 DataStore。 */
    suspend fun loadAll(): Map<Int, WidgetStyle> = settings.stringsOnce(PREFIX).mapNotNull { (k, v) ->
        val id = k.removePrefix(PREFIX).substringBefore('.').toIntOrNull() ?: return@mapNotNull null
        val style = WidgetStyleCodec.decode(v) ?: return@mapNotNull null
        id to style
    }.toMap()

    /** 旧版全局样式映射成默认值（读不到就返回默认样式）。 */
    suspend fun legacyDefaults(): WidgetStyle {
        val values = WidgetStyle.LEGACY_KEY_CANDIDATES.associateWith { settings.stringOnce(it) }
        return if (values.values.all { it == null }) WidgetStyle() else WidgetStyleCodec.fromLegacy(values)
    }

    companion object {
        const val PREFIX = "widget."

        fun key(widgetId: Int): String = "$PREFIX$widgetId.style"
    }
}
