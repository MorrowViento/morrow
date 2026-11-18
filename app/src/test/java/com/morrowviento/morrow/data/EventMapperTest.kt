package com.morrowviento.morrow.data

import com.morrowviento.morrow.data.db.EventMapper
import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EventMapperTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun sample() = CountdownEvent(
        id = "evt-1",
        title = "项目上线",
        targetDate = LocalDateTime.of(2026, 12, 1, 9, 30),
        category = "工作",
        note = "别忘了发布说明",
        icon = "rocket",
        direction = CountDirection.COUNTDOWN,
        reminders = listOf(EventReminder("r1", 60), EventReminder("r2", 0, enabled = false)),
        isAllDay = false,
        isCompleted = false,
        isPinned = true,
        repeatType = RepeatType.YEARLY,
        repeatMonth = 12,
        repeatDay = 1,
        createdAt = LocalDateTime.of(2024, 6, 1, 12, 0),
    )

    @Test
    fun `实体与领域模型互转无损`() {
        val original = sample()
        val entity = EventMapper.toEntity(original, zone)
        val restored = EventMapper.toDomain(entity, zone)
        assertEquals(original, restored)
    }

    @Test
    fun monthlyKindAndCursorRoundTripWithoutTimezoneDrift() {
        val original = sample().copy(
            kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY,
            repeatType = RepeatType.MONTHLY, repeatMonth = null,
            lastHandledOccurrence = java.time.LocalDate.of(2026, 11, 1),
        )
        val entity = EventMapper.toEntity(original, zone)
        assertEquals(original, EventMapper.toDomain(entity, zone))
        assertEquals(original.lastHandledOccurrence, EventMapper.toDomain(entity, ZoneId.of("UTC")).lastHandledOccurrence)
        assertEquals(com.morrowviento.morrow.domain.model.EventKind.TODO,
            EventMapper.toDomain(entity.copy(kind = "unknown"), zone).kind)
        assertEquals(null, EventMapper.toDomain(entity.copy(lastHandledOccurrence = Long.MAX_VALUE), zone).lastHandledOccurrence)
    }

    @Test
    fun multiDayEndDateAndEndTimeRoundTrip() {
        val original = sample().copy(
            endDate = java.time.LocalDate.of(2026, 12, 5),
            endTime = java.time.LocalTime.of(18, 30),
        )
        val entity = EventMapper.toEntity(original, zone)
        // 结束时刻按「分钟数」落库，18:30 → 1110
        assertEquals(1110, entity.endTime)
        assertEquals(original, EventMapper.toDomain(entity, zone))
        // 脏分钟数（越界）还原成 null，不让它炸掉读库
        assertNull(EventMapper.toDomain(entity.copy(endTime = 1440), zone).endTime)
        assertNull(EventMapper.toDomain(entity.copy(endTime = -1), zone).endTime)
    }

    @Test
    fun `毫秒时间戳按指定时区还原`() {
        val event = sample()
        val entity = EventMapper.toEntity(event, zone)
        assertEquals(event.targetDate, EventMapper.toLocalDateTime(entity.targetAt, zone))
        assertEquals(event.createdAt, EventMapper.toLocalDateTime(entity.createdAt, zone))
    }

    @Test
    fun `提醒列表编码解码保持顺序`() {
        val reminders = listOf(EventReminder("b", 120), EventReminder("a", 30))
        val json = EventMapper.encodeReminders(reminders)
        val decoded = EventMapper.decodeReminders(json)
        assertEquals(listOf(120, 30), decoded.map { it.minutesBefore })
        assertEquals(listOf("b", "a"), decoded.map { it.id })
    }

    @Test
    fun `空提醒与脏数据安全处理`() {
        assertEquals(0, EventMapper.decodeReminders("").size)
        assertEquals(0, EventMapper.decodeReminders("[]").size)
        assertEquals(0, EventMapper.decodeReminders("not json").size)
    }
}
