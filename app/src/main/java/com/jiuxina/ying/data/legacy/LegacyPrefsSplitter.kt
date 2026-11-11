package com.jiuxina.ying.data.legacy

import com.jiuxina.ying.data.settings.LegacySettingsKeys

/**
 * 把 Flutter 版 SharedPreferences 的原始键值拆分为「事件 JSON」与「设置项」两部分。
 *
 * 纯函数，无 Android 依赖，便于单测覆盖各种脏数据形态。
 */
object LegacyPrefsSplitter {

    /** Flutter Android 实现的键前缀。 */
    const val PREFIX = "flutter."

    data class Split(
        /** 事件 JSON 原文（可能为 null 表示旧版没有数据） */
        val eventsJson: String?,
        /** 已去掉 `flutter.` 前缀的设置项 */
        val settings: Map<String, Any?>,
        /** 未识别的键（仅日志用） */
        val ignored: List<String>,
    )

    fun split(raw: Map<String, Any?>): Split {
        var eventsJson: String? = null
        val settings = LinkedHashMap<String, Any?>()
        val ignored = mutableListOf<String>()

        for ((rawKey, value) in raw) {
            if (!rawKey.startsWith(PREFIX)) {
                ignored.add(rawKey)
                continue
            }
            val key = rawKey.removePrefix(PREFIX)
            when {
                key == LegacySettingsKeys.EVENTS -> {
                    eventsJson = value as? String
                }
                value == null -> ignored.add(rawKey)
                else -> settings[key] = value
            }
        }
        return Split(eventsJson = eventsJson, settings = settings, ignored = ignored)
    }
}
