package com.jiuxina.ying.data.legacy

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jiuxina.ying.domain.model.CountDirection
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * 解析 Flutter 版存于 SharedPreferences 的事件 JSON。
 *
 * 数据来源：`flutter.countdown_events_v1`（String），内容为
 * `CountdownEvent.encodeList` 产出的 JSON 数组。
 *
 * 兼容点（对齐 Dart 侧 `fromJson` 的兜底行为）：
 * - `category` 缺省 → 「生活」
 * - `note` / `icon` 缺省 → 空串
 * - `direction` / `repeatType` 无法识别 → 默认值
 * - 无 `reminders` 但有旧字段 `reminderMinutes` → 转成单条提醒
 * - `targetDate` / `createdAt`：Dart `toIso8601String()` 无时区，按本地时刻解释；
 *   若带 `Z` 或偏移量也能解析
 */
object LegacyEventParser {

    /** 解析结果；[failed] 记录无法解析的条目数（不因单条损坏而整体失败）。 */
    data class Result(
        val events: List<CountdownEvent>,
        val failed: Int,
    )

    fun parse(json: String?): Result {
        if (json.isNullOrBlank()) return Result(emptyList(), 0)
        val root: JsonElement = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?: return Result(emptyList(), 1)
        if (!root.isJsonArray) return Result(emptyList(), 1)

        val events = mutableListOf<CountdownEvent>()
        var failed = 0
        for (element in root.asJsonArray) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject
            val parsed = obj?.let { runCatching { parseOne(it) }.getOrNull() }
            if (parsed == null) failed++ else events.add(parsed)
        }
        return Result(events, failed)
    }

    private fun parseOne(obj: JsonObject): CountdownEvent {
        val target = parseLocalDateTime(obj.str("targetDate"))
            ?: throw IllegalArgumentException("targetDate 缺失或格式错误")
        val createdAt = parseLocalDateTime(obj.str("createdAt")) ?: target

        val repeatType = when (obj.str("repeatType")) {
            "yearly" -> RepeatType.YEARLY
            else -> RepeatType.NONE
        }
        val reminders = when {
            obj.has("reminders") && obj.get("reminders").isJsonArray ->
                EventReminder.normalize(
                    obj.getAsJsonArray("reminders").mapNotNull { parseReminder(it) },
                )
            else -> legacyReminder(obj.int("reminderMinutes"))
        }

        val id = obj.str("id") ?: throw IllegalArgumentException("id 缺失")

        return CountdownEvent(
            id = id,
            title = obj.str("title").orEmpty(),
            targetDate = target,
            category = obj.str("category") ?: CountdownEvent.DEFAULT_CATEGORY,
            note = obj.str("note").orEmpty(),
            icon = obj.str("icon").orEmpty(),
            direction = when (obj.str("direction")) {
                "countdown" -> CountDirection.COUNTDOWN
                "countup" -> CountDirection.COUNTUP
                else -> CountDirection.AUTO
            },
            reminders = reminders,
            isAllDay = obj.bool("isAllDay") ?: false,
            isCompleted = obj.bool("isCompleted") ?: false,
            isPinned = obj.bool("isPinned") ?: false,
            repeatType = repeatType,
            repeatMonth = if (repeatType == RepeatType.YEARLY) {
                obj.int("repeatMonth") ?: target.monthValue
            } else {
                null
            },
            repeatDay = if (repeatType == RepeatType.YEARLY) {
                obj.int("repeatDay") ?: target.dayOfMonth
            } else {
                null
            },
            createdAt = createdAt,
        )
    }

    private fun throwFrom(msg: String): Nothing = throw IllegalArgumentException(msg)

    private fun parseReminder(element: JsonElement): EventReminder? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val minutes = obj.int("minutesBefore") ?: return null
        if (minutes < 0) return null
        return EventReminder(
            id = obj.str("id") ?: "r-$minutes",
            minutesBefore = minutes,
            enabled = obj.bool("enabled") ?: true,
        )
    }

    /** 旧版本单提醒字段 → 提醒列表（与原实现 `_legacyReminders` 一致）。 */
    private fun legacyReminder(minutes: Int?): List<EventReminder> {
        if (minutes == null || minutes < 0) return emptyList()
        return listOf(EventReminder(id = "legacy-$minutes", minutesBefore = minutes))
    }

    /**
     * 解析日期时间：优先按无时区本地时刻，其次按带偏移（含 `Z`）。
     * Dart 的 `DateTime.toIso8601String()` 对 UTC 值会追加 `Z`。
     */
    fun parseLocalDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        runCatching { return LocalDateTime.parse(raw) }
        runCatching { return OffsetDateTime.parse(raw).toLocalDateTime() }
        runCatching { return java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
        return null
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.bool(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }
}
