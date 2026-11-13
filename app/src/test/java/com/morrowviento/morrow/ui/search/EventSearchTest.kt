package com.morrowviento.morrow.ui.search

import com.morrowviento.morrow.ui.event.EventCardModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EventSearchTest {

    private fun card(
        id: String,
        title: String = "标题",
        category: String = "",
        note: String = "",
        completed: Boolean = false,
    ) = EventCardModel(
        id = id,
        title = title,
        category = category,
        note = note,
        target = LocalDateTime.of(2026, 5, 1, 9, 0),
        createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
        dayDelta = 10,
        isCountingUp = false,
        isPinned = false,
        isAllDay = false,
        isCompleted = completed,
        repeatsYearly = false,
        progress = 0.5f,
        remainingToday = null,
        meta = "",
    )

    @Test
    fun `空查询与纯空白查询都不过滤`() {
        val card = card("1", title = "生日")
        assertTrue(EventSearch.matchesQuery(card, ""))
        assertTrue(EventSearch.matchesQuery(card, "   "))
    }

    @Test
    fun `查询对标题备注分类做字面包含并忽略大小写`() {
        val card = card("1", title = "Anna 的生日", category = "生活", note = "订蛋糕")
        assertTrue(EventSearch.matchesQuery(card, "anna"))
        assertTrue(EventSearch.matchesQuery(card, "生日"))
        assertTrue(EventSearch.matchesQuery(card, "蛋糕"))
        assertTrue(EventSearch.matchesQuery(card, "生活"))
        assertFalse(EventSearch.matchesQuery(card, "旅行"))
    }

    @Test
    fun `查询串首尾空白被忽略`() {
        val card = card("1", title = "周年纪念")
        assertTrue(EventSearch.matchesQuery(card, "  周年 "))
    }

    @Test
    fun `分类筛选 全部 命名 与 未分类`() {
        val life = card("1", category = "生活")
        val work = card("2", category = "工作")
        val none = card("3", category = "")

        assertTrue(EventSearch.matchesFilter(life, CategoryFilter.All))
        assertTrue(EventSearch.matchesFilter(life, CategoryFilter.Named("生活")))
        assertFalse(EventSearch.matchesFilter(life, CategoryFilter.Named("工作")))
        assertTrue(EventSearch.matchesFilter(none, CategoryFilter.Uncategorized))
        assertFalse(EventSearch.matchesFilter(work, CategoryFilter.Uncategorized))
    }

    @Test
    fun `分类与查询叠加 且进行中与已完成分开`() {
        val active = listOf(
            card("1", title = "生日", category = "生活"),
            card("2", title = "生日聚会", category = "工作"),
            card("3", title = "报销", category = "工作"),
        )
        val completed = listOf(
            card("4", title = "生日礼物", category = "工作", completed = true),
            card("5", title = "体检", category = "生活", completed = true),
        )

        val result = EventSearch.apply(active, completed, "生日", CategoryFilter.Named("工作"))

        assertEquals(listOf("2"), result.active.map { it.id })
        assertEquals(listOf("4"), result.completed.map { it.id })
        assertEquals(2, result.count)
        assertFalse(result.isEmpty)
    }

    @Test
    fun `无结果时 isEmpty 为真`() {
        val result = EventSearch.apply(listOf(card("1", title = "生日")), emptyList(), "旅行", CategoryFilter.All)
        assertTrue(result.isEmpty)
        assertEquals(0, result.count)
    }

    @Test
    fun `分类计数按给定顺序排列并跳过零计数`() {
        val cards = listOf(
            card("1", category = "生活"),
            card("2", category = "工作"),
            card("3", category = "工作"),
            card("4", category = ""),
            card("5", category = "旧分类"),
            card("6", category = "旧分类"),
        )

        val counts = EventSearch.categoryCounts(cards, listOf("生活", "生日", "工作"))

        // 「生日」没有事件 → 不出现；不在顺序表里的「旧分类」按名字追加在末尾
        assertEquals(listOf("生活" to 1, "工作" to 2, "旧分类" to 2), counts)
        assertEquals(1, EventSearch.uncategorizedCount(cards))
    }

    @Test
    fun `没有事件时计数为空`() {
        assertTrue(EventSearch.categoryCounts(emptyList(), listOf("生活")).isEmpty())
        assertEquals(0, EventSearch.uncategorizedCount(emptyList()))
    }
}
