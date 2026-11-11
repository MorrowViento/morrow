package com.jiuxina.ying.data.undo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jiuxina.ying.domain.model.CountDirection
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import com.jiuxina.ying.domain.model.UndoKind
import com.jiuxina.ying.domain.model.UndoOperation
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 待撤销操作的 JSON 编解码（存 DataStore 单键，支持跨进程恢复）。
 *
 * 事件字段名与旧版 Dart 序列化保持一致，便于 M6 备份直接复用同一套字段。
 * 时间统一用 ISO 字符串（无时区按本地时刻解释），不依赖 Gson 对 java.time 的反射适配。
 */
object UndoCodec {

    fun encode(op: UndoOperation): String {
        val root = JsonObject()
        root.addProperty("id", op.id)
        root.addProperty("kind", op.kind.name)
        root.addProperty("message", op.message)
        root.addProperty("expiresAt", op.expiresAtMillis)
        root.addProperty("pushedAt", op.pushedAtMillis)
        root.add("events", JsonArray().apply { op.snapshot.forEach { add(encodeEvent(it)) } })
        return root.toString()
    }

    /** @return null 表示内容不可解析，调用方应按「无待撤销操作」处理。 */
    fun decode(raw: String?): UndoOperation? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return null
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject

        val id = obj.string("id") ?: return null
        val kind = obj.string("kind")?.let { name ->
            UndoKind.entries.firstOrNull { it.name == name }
        } ?: return null
        val expiresAt = obj.long("expiresAt") ?: return null
        val eventsElement = obj.get("events")
        if (eventsElement == null || !eventsElement.isJsonArray) return null

        val snapshot = eventsElement.asJsonArray.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.let { runCatching { decodeEvent(it) }.getOrNull() }
        }
        if (snapshot.size != eventsElement.asJsonArray.size()) return null

        return UndoOperation(
            id = id,
            kind = kind,
            message = obj.string("message").orEmpty(),
            snapshot = snapshot,
            expiresAtMillis = expiresAt,
            // 旧版落盘没有这个字段：按满窗口倒推，语义与当时一致
            pushedAtMillis = obj.long("pushedAt") ?: (expiresAt - UndoOperation.WINDOW_MILLIS),
        )
    }

    private fun encodeEvent(event: CountdownEvent): JsonObject = JsonObject().apply {
        addProperty("id", event.id)
        addProperty("title", event.title)
        addProperty("targetDate", event.targetDate.toString())
        addProperty("category", event.category)
        addProperty("note", event.note)
        addProperty("icon", event.icon)
        addProperty("direction", event.direction.name.lowercase())
        addProperty("isAllDay", event.isAllDay)
        addProperty("isCompleted", event.isCompleted)
        addProperty("isPinned", event.isPinned)
        addProperty("repeatType", event.repeatType.name.lowercase())
        event.repeatMonth?.let { addProperty("repeatMonth", it) }
        event.repeatDay?.let { addProperty("repeatDay", it) }
        addProperty("createdAt", event.createdAt.toString())
        add(
            "reminders",
            JsonArray().apply {
                event.reminders.forEach { reminder ->
                    add(
                        JsonObject().apply {
                            addProperty("id", reminder.id)
                            addProperty("minutesBefore", reminder.minutesBefore)
                            addProperty("enabled", reminder.enabled)
                        },
                    )
                }
            },
        )
    }

    private fun decodeEvent(obj: JsonObject): CountdownEvent {
        val target = parseDateTime(obj.string("targetDate"))
            ?: throw IllegalArgumentException("targetDate 缺失或格式错误")
        val createdAt = parseDateTime(obj.string("createdAt")) ?: target
        val repeatType = when (obj.string("repeatType")) {
            "yearly" -> RepeatType.YEARLY
            else -> RepeatType.NONE
        }
        val reminders = obj.get("reminders")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val minutes = item.int("minutesBefore") ?: return@mapNotNull null
                if (minutes < 0) null else EventReminder(
                    id = item.string("id") ?: "r-$minutes",
                    minutesBefore = minutes,
                    enabled = item.bool("enabled") ?: true,
                )
            }
            .orEmpty()

        return CountdownEvent(
            id = obj.string("id") ?: throw IllegalArgumentException("id 缺失"),
            title = obj.string("title").orEmpty(),
            targetDate = target,
            category = obj.string("category") ?: CountdownEvent.DEFAULT_CATEGORY,
            note = obj.string("note").orEmpty(),
            icon = obj.string("icon").orEmpty(),
            direction = when (obj.string("direction")) {
                "countdown" -> CountDirection.COUNTDOWN
                "countup" -> CountDirection.COUNTUP
                else -> CountDirection.AUTO
            },
            // 刻意不做 normalize：撤销要求快照逐字段还原，重排会让往返不再等价。
            // 领域层的事件本身已是归一化后的结果，这里无需二次整理。
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

    /**
     * 解析日期时间。
     *
     * 无时区按本地墙钟解释；**带偏移量或 Z 的按绝对时刻换算到本地时区**——
     * 后者语义是"某一瞬间"，直接丢弃偏移会得到错误的天数差。
     */
    private fun parseDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        runCatching { return LocalDateTime.parse(raw) }
        runCatching {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }
        runCatching { return Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        return null
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.bool(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }
}
