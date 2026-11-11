package com.jiuxina.ying.data.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jiuxina.ying.domain.model.CountDirection
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 实体 ↔ 领域模型转换。
 *
 * 时区约定：旧版数据是「无时区的本地时刻」（Dart 侧 `toIso8601String()`），
 * 迁移时按迁移当下设备时区解释并落库为毫秒；读取时用同一时区还原。
 * 用户跨时区旅行会看到时间随本地时钟偏移，这与旧版行为一致（旧版同样按本地时刻解释）。
 */
object EventMapper {

    private val gson = Gson()
    private val reminderListType = object : TypeToken<List<ReminderJson>>() {}.type

    private data class ReminderJson(
        val id: String? = null,
        val minutesBefore: Int = 0,
        val enabled: Boolean? = null,
    )

    fun toEntity(
        event: CountdownEvent,
        zone: ZoneId = ZoneId.systemDefault(),
    ): EventEntity = EventEntity(
        id = event.id,
        title = event.title,
        targetAt = event.targetDate.atZone(zone).toInstant().toEpochMilli(),
        category = event.category,
        note = event.note,
        icon = event.icon,
        direction = event.direction.name,
        reminders = encodeReminders(event.reminders),
        isAllDay = event.isAllDay,
        isCompleted = event.isCompleted,
        isPinned = event.isPinned,
        repeatType = event.repeatType.name,
        repeatMonth = event.repeatMonth,
        repeatDay = event.repeatDay,
        createdAt = event.createdAt.atZone(zone).toInstant().toEpochMilli(),
    )

    fun toDomain(
        entity: EventEntity,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CountdownEvent = CountdownEvent(
        id = entity.id,
        title = entity.title,
        targetDate = toLocalDateTime(entity.targetAt, zone),
        category = entity.category,
        note = entity.note,
        icon = entity.icon,
        direction = enumOrDefault(entity.direction, CountDirection.AUTO),
        reminders = decodeReminders(entity.reminders),
        isAllDay = entity.isAllDay,
        isCompleted = entity.isCompleted,
        isPinned = entity.isPinned,
        repeatType = enumOrDefault(entity.repeatType, RepeatType.NONE),
        repeatMonth = entity.repeatMonth,
        repeatDay = entity.repeatDay,
        createdAt = toLocalDateTime(entity.createdAt, zone),
    )

    fun toLocalDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun encodeReminders(reminders: List<EventReminder>): String =
        gson.toJson(
            reminders.map {
                ReminderJson(id = it.id, minutesBefore = it.minutesBefore, enabled = it.enabled)
            },
        )

    fun decodeReminders(raw: String): List<EventReminder> {
        if (raw.isBlank()) return emptyList()
        val parsed: List<ReminderJson>? = runCatching {
            gson.fromJson<List<ReminderJson>>(raw, reminderListType)
        }.getOrNull()
        val values = parsed.orEmpty().map { json ->
            EventReminder(
                id = json.id ?: "r-${json.minutesBefore}",
                minutesBefore = json.minutesBefore,
                enabled = json.enabled ?: true,
            )
        }
        return EventReminder.normalize(values)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
