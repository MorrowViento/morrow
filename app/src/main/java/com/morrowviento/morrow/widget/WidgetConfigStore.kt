package com.morrowviento.morrow.widget

import com.morrowviento.morrow.data.settings.SettingsRepository

/**
 * 小部件配置存储：一个 widgetId 一条 JSON，键名 `widget.<id>.style`。
 *
 * 复用 App 的同一个 DataStore（`morrow_settings`）而不是新开文件——DataStore 同一个文件
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

    /** 这个实例有没有自己的配置（区别于「回落到默认样式」）。 */
    suspend fun hasOwn(widgetId: Int): Boolean = settings.stringOnce(key(widgetId)) != null

    /**
     * 记下「下一次新加的小部件该用什么样式」。
     *
     * 用在「先设置、再落桌面」这条路：用户在配置台里调好外观点「添加到桌面」时，
     * 桌面上还没有 widgetId（它由桌面在 pin 成功后才分配），只能先把样式挂在
     * 「待认领」槽位上，等新实例第一次 onUpdate 时再认领。
     */
    suspend fun savePending(isDetail: Boolean, style: WidgetStyle) {
        settings.putStrings(
            mapOf(
                pendingKey(isDetail) to WidgetStyleCodec.encode(style),
                pendingAtKey(isDetail) to System.currentTimeMillis().toString(),
            ),
        )
    }

    /**
     * 新实例认领待用样式：认领到就落成这个 id 的专属配置并清空槽位。
     *
     * 三条约束：
     * 1. 已有专属配置的实例不认领——它是被编辑过的老实例，不该被新样式覆盖；
     * 2. 过期不认领——用户可能点了「添加到桌面」又在系统确认框上取消，槽位会残留；
     *    时间窗一过就当作没这回事，避免几天后手动加小部件时突然套上旧样式；
     * 3. 认领即清空，保证一次设置只作用一个新实例。
     */
    suspend fun adoptPending(widgetId: Int, isDetail: Boolean): WidgetStyle? {
        if (hasOwn(widgetId)) return null
        val raw = settings.stringOnce(pendingKey(isDetail)) ?: return null
        val savedAt = settings.stringOnce(pendingAtKey(isDetail))?.toLongOrNull() ?: 0L
        val fresh = System.currentTimeMillis() - savedAt in 0..PENDING_MAX_AGE_MILLIS
        val style = WidgetStyleCodec.decode(raw)
        clearPending(isDetail)
        if (!fresh || style == null) return null
        save(widgetId, style)
        return style
    }

    suspend fun clearPending(isDetail: Boolean) {
        settings.remove(pendingKey(isDetail))
        settings.remove(pendingAtKey(isDetail))
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

        /**
         * 「先设置、再落桌面」的待认领槽位有效期。
         *
         * 用户点「添加到桌面」后系统还会弹一次确认；确认了才会产生新实例。留 10 分钟
         * 足够覆盖「点了添加后去干别的、过会儿再回来确认」的情形，又不会让槽位长期残留
         * 以致下次手动加小部件时莫名套上旧样式。
         */
        const val PENDING_MAX_AGE_MILLIS = 10 * 60 * 1000L

        fun key(widgetId: Int): String = "$PREFIX$widgetId.style"

        /** 待认领槽位按小部件类型分开：总览与单事件互不干扰。 */
        fun pendingKey(isDetail: Boolean): String =
            "$PREFIX" + if (isDetail) "pending_detail" else "pending_overview"

        fun pendingAtKey(isDetail: Boolean): String = pendingKey(isDetail) + ".at"

        /** 从 `widget.<id>.style` 解析 id；不是这种形态（含默认样式键）时返回 null。 */
        fun widgetIdOf(key: String): Int? =
            key.removePrefix(PREFIX).substringBefore('.').toIntOrNull()
    }
}
