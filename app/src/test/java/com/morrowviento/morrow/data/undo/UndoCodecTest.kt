package com.morrowviento.morrow.data.undo

import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.model.UndoKind
import com.morrowviento.morrow.domain.model.UndoOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class UndoCodecTest {

    private fun sampleEvent(id: String = "e1") = CountdownEvent(
        id = id,
        title = "春节回家",
        targetDate = LocalDateTime.of(2027, 2, 6, 9, 30),
        category = "节日",
        note = "带上礼物",
        icon = "🎁",
        direction = CountDirection.COUNTDOWN,
        reminders = listOf(
            EventReminder(id = "r1", minutesBefore = 60, enabled = true),
            EventReminder(id = "r2", minutesBefore = 1440, enabled = false),
        ),
        isAllDay = true,
        isCompleted = true,
        isPinned = true,
        repeatType = RepeatType.YEARLY,
        repeatMonth = 2,
        repeatDay = 6,
        createdAt = LocalDateTime.of(2026, 11, 1, 20, 15),
    )

    @Test
    fun `编解码往返保留全部字段`() {
        val operation = UndoOperation(
            id = "u1",
            kind = UndoKind.CLEAR_COMPLETED,
            message = "已清理 2 个已完成事件",
            snapshot = listOf(sampleEvent("e1"), sampleEvent("e2")),
            expiresAtMillis = 1_700_000_000_000L,
        )

        val restored = UndoCodec.decode(UndoCodec.encode(operation))

        assertEquals(operation, restored)
    }

    @Test
    fun `无提醒且不重复的事件也能往返`() {
        val event = sampleEvent().copy(reminders = emptyList(), repeatType = RepeatType.NONE, repeatMonth = null, repeatDay = null)
        val operation = UndoOperation(
            id = "u2",
            kind = UndoKind.DELETE,
            message = "已删除",
            snapshot = listOf(event),
            expiresAtMillis = 42L,
        )

        assertEquals(operation, UndoCodec.decode(UndoCodec.encode(operation)))
    }

    @Test
    fun `null 与空白输入返回 null`() {
        assertNull(UndoCodec.decode(null))
        assertNull(UndoCodec.decode(""))
    }

    @Test
    fun `非法 JSON 或结构不符返回 null`() {
        assertNull(UndoCodec.decode("not json"))
        assertNull(UndoCodec.decode("[]"))
        assertNull(UndoCodec.decode("""{"id":"u","kind":"DELETE"}"""))
    }

    @Test
    fun `未知操作类型返回 null 而不是崩溃`() {
        val raw = """{"id":"u","kind":"UNKNOWN","message":"m","expiresAt":1,"events":[]}"""
        assertNull(UndoCodec.decode(raw))
    }

    @Test
    fun `快照中存在无法解析的事件时整体判定为损坏`() {
        val raw = """
            {"id":"u","kind":"DELETE","message":"m","expiresAt":1,
             "events":[{"id":"e1"},{"title":"缺少 targetDate"}]}
        """.trimIndent()
        assertNull(UndoCodec.decode(raw))
    }

    @Test
    fun `跨时区时间戳按本地时刻还原`() {
        val raw = """
            {"id":"u","kind":"DELETE","message":"m","expiresAt":1,
             "events":[{"id":"e1","title":"t","targetDate":"2027-02-06T09:30:00Z","createdAt":"2026-11-01T20:15:00"}]}
        """.trimIndent()
        val restored = UndoCodec.decode(raw)?.snapshot?.firstOrNull()
        val expected = java.time.Instant.parse("2027-02-06T09:30:00Z")
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        assertEquals(expected, restored?.targetDate)
    }
}
