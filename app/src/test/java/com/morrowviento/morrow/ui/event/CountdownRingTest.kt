package com.morrowviento.morrow.ui.event

import com.morrowviento.morrow.domain.model.ChecklistItem
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventKind
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.model.SortMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 倒计时环的进度来源（4.5.0 之前「生日/待办没有进度」的根因就在这儿）。
 *
 * 三条轨道必须各归各：
 * - 待办 + 有子任务 → 勾选比例
 * - 重复事件 → 上一次发生 → 下一次发生，本周期走了多少
 * - 其余 → 创建 → 目标
 *
 * 另外还钉两条容易被推翻的约束：每秒的 refreshClock **不许**把前两轨重算掉，
 * 以及子任务全勾完不等于事件已完成。
 */
class CountdownRingTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 11)
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 11, 15, 30)

    private fun event(
        id: String = "e1",
        title: String = "事件",
        target: LocalDateTime,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
        kind: EventKind = EventKind.ANNIVERSARY,
        repeatType: RepeatType = RepeatType.NONE,
        repeatMonth: Int? = null,
        repeatDay: Int? = null,
        checklist: List<ChecklistItem> = emptyList(),
        isCompleted: Boolean = false,
    ) = CountdownEvent(
        id = id,
        title = title,
        targetDate = target,
        createdAt = createdAt,
        kind = kind,
        repeatType = repeatType,
        repeatMonth = repeatMonth,
        repeatDay = repeatDay,
        checklist = checklist,
        isCompleted = isCompleted,
    )

    private fun card(event: CountdownEvent) = EventPresentation.toCard(event, today, now)

    @Test
    fun `待办有子任务时环看勾选比例`() {
        val withTasks = event(
            kind = EventKind.TODO,
            // 创建已久、目标还很远：如果环按「创建→目标」算，这里会接近 1
            target = LocalDateTime.of(2026, 11, 20, 9, 0),
            checklist = listOf(
                ChecklistItem("a", "打包", done = true),
                ChecklistItem("b", "订车", done = false),
                ChecklistItem("c", "退货", done = false),
                ChecklistItem("d", "交接", done = true),
            ),
        )
        val card = card(withTasks)

        assertEquals(0.5f, card.progress, 0.0001f)
        assertTrue(card.hasChecklist)
        assertEquals("2/4", card.checklistLabel)
    }

    @Test
    fun `待办没有子任务时仍走创建到目标`() {
        val plain = event(
            kind = EventKind.TODO,
            target = LocalDateTime.of(2026, 11, 21, 15, 30),
            createdAt = LocalDateTime.of(2026, 11, 11, 15, 30),
        )
        assertEquals(0f, card(plain).progress, 0.0001f)
    }

    @Test
    fun `生日这类每年重复的事件不再恒为零`() {
        // 每年 12 月 25 日：上一次 2025-12-25，下一次 2026-12-25，今天 2026-11-11
        val birthday = event(
            target = LocalDateTime.of(2026, 12, 25, 0, 0),
            repeatType = RepeatType.YEARLY,
            repeatMonth = 12,
            repeatDay = 25,
        )
        val progress = card(birthday).progress

        // 321 / 365 ≈ 0.879
        assertTrue("生日环应该已经走了大半圈，实际 $progress", progress > 0.85f && progress < 0.91f)
    }

    @Test
    fun `每周重复按本周期走量算`() {
        // 每周一：上一次 2026-11-09（周一），下一次 2026-11-16，今天周三
        val weekly = event(
            target = LocalDateTime.of(2026, 11, 9, 9, 0),
            repeatType = RepeatType.WEEKLY,
        )
        val progress = card(weekly).progress

        // 2 / 7 ≈ 0.286
        assertTrue("周环应该在 0.28 附近，实际 $progress", progress > 0.27f && progress < 0.30f)
    }

    @Test
    fun `周期进度量化到 1 per 256，避免每秒重组`() {
        val birthday = event(
            target = LocalDateTime.of(2026, 12, 25, 0, 0),
            repeatType = RepeatType.YEARLY,
            repeatMonth = 12,
            repeatDay = 25,
        )
        val progress = card(birthday).progress
        assertEquals(progress, (progress * 256f).toInt() / 256f, 0f)
    }

    @Test
    fun `每秒刷新不会把双轨进度打回旧语义`() {
        val birthday = event(
            id = "birthday",
            target = LocalDateTime.of(2026, 12, 25, 0, 0),
            repeatType = RepeatType.YEARLY,
            repeatMonth = 12,
            repeatDay = 25,
        )
        val todo = event(
            id = "todo",
            kind = EventKind.TODO,
            target = LocalDateTime.of(2026, 11, 20, 9, 0),
            checklist = listOf(ChecklistItem("a", "打包", done = true), ChecklistItem("b", "订车")),
        )
        // 创建→目标这一轨的事件：一秒之后进度会变
        val plain = event(
            id = "plain",
            target = LocalDateTime.of(2026, 11, 21, 15, 30),
            createdAt = LocalDateTime.of(2026, 11, 11, 15, 30),
        )

        // 4.6.0 起纪念日单独成段，这里两段合并后再按 id 查
        val parts = EventPresentation.partition(
            listOf(birthday, todo, plain), SortMode.DISTANCE, today, now,
        )
        val cards = parts.anniversaries + parts.active
        val byId = cards.associateBy { it.id }

        // 同一天晚一秒刷新
        val later = now.plusSeconds(1)
        val refreshed = EventPresentation.refreshClock(cards, today, later).associateBy { it.id }

        assertEquals(byId.getValue("birthday").progress, refreshed.getValue("birthday").progress, 0f)
        assertEquals(byId.getValue("todo").progress, refreshed.getValue("todo").progress, 0f)
        // 只有「创建→目标」那一条被推进
        assertTrue(refreshed.getValue("plain").progress >= byId.getValue("plain").progress)
    }

    @Test
    fun `子任务全勾完不等于事件已完成`() {
        val doneAll = event(
            kind = EventKind.TODO,
            target = LocalDateTime.of(2026, 11, 20, 9, 0),
            checklist = listOf(ChecklistItem("a", "打包", done = true), ChecklistItem("b", "订车", done = true)),
        )
        val card = card(doneAll)

        assertEquals(1f, card.progress, 0.0001f)
        // 环走满只是「清单做完了」；事件本身还要用户点「完成」才进已完成区
        assertFalse(card.isCompleted)
    }

    @Test
    fun `没有子任务时卡片不带子任务行`() {
        val card = card(event(target = LocalDateTime.of(2026, 11, 20, 9, 0)))
        assertFalse(card.hasChecklist)
        assertEquals(null, card.checklistLabel)
    }
}
