package com.jiuxina.ying.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「设置页两行一模一样的『总览 / 编辑』」的回归测试。
 *
 * 真机反馈：桌面上放了两张总览卡片，设置页就列出两行标题、副标题、按钮完全相同的行，
 * 用户既分不清该点哪个，也不该被要求分别设两次。修法是把同类实例折叠成一行，
 * 行里带着全部 widgetId，配置台保存时对整组生效。
 *
 * 折叠是纯逻辑（[WidgetEntries.rows]），不依赖 AppWidgetManager，直接在这里锁住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetEntriesRowsTest {

    @Test
    fun `多个总览实例只出一行且保留全部 id`() {
        val rows = WidgetEntries.rows(
            overviewIds = listOf(11, 12, 13),
            detailBindings = emptyMap(),
        )
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(false, row.isDetail)
        assertEquals(listOf(11, 12, 13), row.ids)
        assertEquals(11, row.id) // 配置台回传结果仍用组里第一个 id
        assertEquals(3, row.count)
    }

    @Test
    fun `桌面没有总览时不出总览行`() {
        val rows = WidgetEntries.rows(
            overviewIds = emptyList(),
            detailBindings = mapOf(21 to "ev-a"),
            eventTitleOf = { "春节回家" },
        )
        assertEquals(1, rows.size)
        assertTrue(rows.single().isDetail)
        assertEquals(listOf(21), rows.single().ids)
        assertEquals("春节回家", rows.single().eventTitle)
    }

    @Test
    fun `单事件按绑定事件分组而不是按实例列行`() {
        val rows = WidgetEntries.rows(
            overviewIds = listOf(11, 12),
            detailBindings = mapOf(
                21 to "ev-a",
                22 to "ev-a",
                23 to "ev-b",
                24 to null,
            ),
            eventTitleOf = { id -> mapOf("ev-a" to "春节回家", "ev-b" to "妈妈生日")[id] },
        )
        // 1 行总览 + 3 组单事件（ev-a / ev-b / 未绑定）
        assertEquals(4, rows.size)
        assertEquals(false, rows[0].isDetail)
        assertEquals(listOf(11, 12), rows[0].ids)

        val details = rows.drop(1)
        assertEquals(listOf(21, 22), details.single { it.eventTitle == "春节回家" }.ids)
        assertEquals(listOf(23), details.single { it.eventTitle == "妈妈生日" }.ids)
        assertEquals(listOf(24), details.single { it.eventTitle == null }.ids)
    }

    @Test
    fun `桌面一个萤小部件都没有时列表为空`() {
        assertTrue(WidgetEntries.rows(emptyList(), emptyMap()).isEmpty())
    }
}
