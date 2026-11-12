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
        val parsed = raw?.let { WidgetStyleCodec.decode(it) }
        return parsed ?: defaultStyle()
    }

    /**
     * 没有专属配置的小部件用什么样式。
     *
     * 回落链：**上一次保存的默认样式 → 旧版全局样式 → 出厂默认**。
     * 中间那一档是关键：换机 / 重装后系统分配的 widgetId 必然变了，按 id 一条都对不上，
     * 只靠旧版全局键的话，用户在新设备上每加一个小部件都要重设一遍。
     *
     * **必须剥掉 `eventId`**：它是「这份配置绑了哪个事件」，天然属于某一个小部件实例。
     * 跟着默认样式传下去，新加的单事件小部件会一上来就绑到上一个小部件的事件上，
     * 用户看到的是「我还没选，它自己挑了别人」。
     */
    suspend fun defaultStyle(): WidgetStyle {
        settings.stringOnce(KEY_DEFAULT_STYLE)?.let { raw ->
            WidgetStyleCodec.decode(raw)?.let { return it.copy(eventId = null) }
        }
        return legacyDefaults().copy(eventId = null)
    }

    suspend fun save(widgetId: Int, style: WidgetStyle) {
        settings.putStrings(
            mapOf(
                key(widgetId) to WidgetStyleCodec.encode(style),
                // 同时作为「默认样式」：备份只按 id 存，换机后 id 对不上时靠它兜底。
                // 落盘时剥掉 eventId——默认样式会被别的小部件实例读走，带上绑定就会串事件
                KEY_DEFAULT_STYLE to WidgetStyleCodec.encode(style.copy(eventId = null)),
            ),
        )
    }

    suspend fun remove(widgetId: Int) = settings.remove(key(widgetId))

    /**
     * 一次性取全部已保存配置，供批量刷新时少读几次 DataStore。
     *
     * `widget.default_style` 不参与：它的前缀段不是数字，[widgetIdOf] 会返回 null。
     */
    suspend fun loadAll(): Map<Int, WidgetStyle> = settings.stringsOnce(PREFIX).mapNotNull { (k, v) ->
        val id = widgetIdOf(k) ?: return@mapNotNull null
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

        /**
         * 最近一次保存的样式，作为「按 id 查不到配置」时的兜底。
         *
         * 存在的理由：备份里的小部件样式是按 widgetId 存的，而 widgetId 由桌面宿主分配，
         * 换机后必然对不上。少了这一份，用户换机后每加一个小部件都要重设一遍。
         */
        const val KEY_DEFAULT_STYLE = "widget.default_style"

        fun key(widgetId: Int): String = "$PREFIX$widgetId.style"

        /** 从 `widget.<id>.style` 解析 id；不是这种形态（含默认样式键）时返回 null。 */
        fun widgetIdOf(key: String): Int? =
            key.removePrefix(PREFIX).substringBefore('.').toIntOrNull()
    }
}
