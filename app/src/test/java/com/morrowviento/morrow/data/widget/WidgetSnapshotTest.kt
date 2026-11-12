package com.morrowviento.morrow.data.widget

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetSnapshotTest {

    private val today: LocalDate = LocalDate.parse("2026-03-14")
    private val now: LocalDateTime = LocalDateTime.parse("2026-03-14T10:00")

    private fun event(
        id: String,
        target: String,
        title: String = id,
        category: String = "生活",
        completed: Boolean = false,
        pinned: Boolean = false,
        allDay: Boolean = false,
        repeat: RepeatType = RepeatType.NONE,
        repeatMonth: Int? = null,
        repeatDay: Int? = null,
        createdAt: String = "2026-01-01T00:00",
    ) = CountdownEvent(
        id = id,
        title = title,
        targetDate = LocalDateTime.parse(target),
        category = category,
        isCompleted = completed,
        isPinned = pinned,
        isAllDay = allDay,
        repeatType = repeat,
        repeatMonth = repeatMonth,
        repeatDay = repeatDay,
        createdAt = LocalDateTime.parse(createdAt),
    )

    // ---- 排序与截断 ----

    @Test
    fun `已完成事件不进小部件`() {
        val snapshot = WidgetSnapshotBuilder.overview(
            events = listOf(
                event("a", "2026-03-20T09:00"),
                event("b", "2026-03-16T09:00", completed = true),
            ),
            today = today,
            now = now,
            limit = 3,
        )
        assertEquals(listOf("a"), snapshot.rows.map { it.id })
        assertEquals(1, snapshot.activeCount)
    }

    @Test
    fun `置顶优先 其余按临近程度升序`() {
        val snapshot = WidgetSnapshotBuilder.overview(
            events = listOf(
                event("far", "2026-04-01T09:00"),
                event("near", "2026-03-16T09:00"),
                event("pinned", "2026-05-01T09:00", pinned = true),
                event("mid", "2026-03-20T09:00"),
            ),
            today = today,
            now = now,
            limit = 5,
        )
        assertEquals(listOf("pinned", "near", "mid", "far"), snapshot.rows.map { it.id })
    }

    @Test
    fun `已过事件排在未来事件之后 且刚过去的更靠前`() {
        val snapshot = WidgetSnapshotBuilder.overview(
            events = listOf(
                event("passedLong", "2026-03-01T09:00"),
                event("future", "2026-03-20T09:00"),
                event("passedShort", "2026-03-12T09:00"),
            ),
            today = today,
            now = now,
            limit = 5,
        )
        assertEquals(listOf("future", "passedShort", "passedLong"), snapshot.rows.map { it.id })
    }

    @Test
    fun `limit 截断行数 但 total 仍按未完成事件数统计`() {
        val snapshot = WidgetSnapshotBuilder.overview(
            events = (1..5).map { event("e$it", "2026-03-%02dT09:00".format(14 + it)) },
            today = today,
            now = now,
            limit = 3,
        )
        assertEquals(3, snapshot.rows.size)
        assertEquals(5, snapshot.activeCount)
        assertEquals("5 个事件", snapshot.headerMeta)
    }

    @Test
    fun `没有事件时给空态文案`() {
        val snapshot = WidgetSnapshotBuilder.overview(emptyList(), today, now, limit = 3)
        assertTrue(snapshot.isEmpty)
        assertEquals("暂无进行中", snapshot.headerMeta)
    }

    // ---- 徽章文案 ----

    @Test
    fun `未来事件显示天数与天`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-03-20T09:00")), today, now, limit = 1,
        ).rows.single()
        assertEquals(6, row.days)
        assertEquals("6", row.badgeText)
        assertEquals("天", row.badgeUnit)
    }

    @Test
    fun `当天事件显示今天且无单位`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-03-14T20:00")), today, now, limit = 1,
        ).rows.single()
        assertTrue(row.isToday)
        assertEquals("今天", row.badgeText)
        assertEquals("", row.badgeUnit)
    }

    @Test
    fun `已过事件显示已过 N`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-03-10T09:00")), today, now, limit = 1,
        ).rows.single()
        assertEquals(4, row.days)
        assertEquals("4", row.badgeText)
        assertEquals("已过", row.badgeUnit)
    }

    // ---- 元信息 ----

    @Test
    fun `元信息按显隐组合拼接`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-03-20T09:00", category = "纪念日")), today, now, limit = 1,
        ).rows.single()

        assertEquals("纪念日 · 3月20日 · 09:00", row.metaLine(showCategory = true, showDate = true))
        assertEquals("3月20日 · 09:00", row.metaLine(showCategory = false, showDate = true))
        assertEquals("纪念日", row.metaLine(showCategory = true, showDate = false))
        assertEquals("", row.metaLine(showCategory = false, showDate = false))
    }

    @Test
    fun `全天事件用全天代替时分`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-03-20T00:00", allDay = true)), today, now, limit = 1,
        ).rows.single()
        assertEquals("生活 · 3月20日 · 全天", row.metaLine(showCategory = true, showDate = true))
    }

    @Test
    fun `每年重复事件标注每年并落在当年周年`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(
                event(
                    "a", "2020-03-20T09:00",
                    repeat = RepeatType.YEARLY, repeatMonth = 3, repeatDay = 20,
                ),
            ),
            today, now, limit = 1,
        ).rows.single()
        assertEquals(LocalDateTime.parse("2026-03-20T09:00"), row.target)
        assertEquals("6", row.badgeText)
        assertTrue(row.metaLine(showCategory = false, showDate = true).startsWith("每年 3月20日"))
    }

    // ---- 进度 ----

    @Test
    fun `进度取创建到目标的中点`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-01-11T00:00", createdAt = "2026-01-01T00:00")),
            today = LocalDate.parse("2026-01-06"),
            now = LocalDateTime.parse("2026-01-06T00:00"),
            limit = 1,
        ).rows.single()
        assertEquals(50, row.progressPercent)
    }

    @Test
    fun `重复事件不给进度`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(
                event(
                    "a", "2020-03-20T09:00",
                    repeat = RepeatType.YEARLY, repeatMonth = 3, repeatDay = 20,
                ),
            ),
            today, now, limit = 1,
        ).rows.single()
        assertEquals(-1, row.progressPercent)
    }

    @Test
    fun `已过事件进度封顶一百`() {
        val row = WidgetSnapshotBuilder.overview(
            listOf(event("a", "2026-03-10T09:00", createdAt = "2026-01-01T00:00")), today, now, limit = 1,
        ).rows.single()
        assertEquals(100, row.progressPercent)
    }

    // ---- 单事件 ----

    @Test
    fun `单事件找不到时返回 null`() {
        assertNull(WidgetSnapshotBuilder.single(null, today, now))
        assertTrue(WidgetSnapshotBuilder.overview(emptyList(), today, now, 3).isEmpty)
    }

    @Test
    fun `单事件不筛已完成`() {
        val row = WidgetSnapshotBuilder.single(
            event("a", "2026-03-20T09:00", completed = true), today, now,
        )
        assertEquals("a", row?.id)
    }
}
