package com.morrowviento.morrow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子任务编解码：Room 一列 JSON（`events.checklist`）、备份、撤销三条路径共用，
 * 任何一条走偏都会让「勾了没记住」或「旧备份打不开」。
 */
class EventChecklistTest {

    @Test
    fun `编解码往返保留 id、标题与勾选状态`() {
        val items = listOf(
            ChecklistItem(id = "a", title = "订机票", done = true),
            ChecklistItem(id = "b", title = "订酒店", done = false),
        )

        assertEquals(items, EventChecklist.decode(EventChecklist.encode(items)))
    }

    @Test
    fun `空清单编码成空数组，解码空串得空清单`() {
        assertEquals("[]", EventChecklist.encode(emptyList()))
        assertEquals(emptyList<ChecklistItem>(), EventChecklist.decode(""))
        assertEquals(emptyList<ChecklistItem>(), EventChecklist.decode("   "))
    }

    @Test
    fun `标题里的逗号与引号不会破坏编码`() {
        // 标签那套逗号串存法在这里会直接毁数据，所以子任务必须走 JSON
        val items = listOf(ChecklistItem(id = "a", title = "买 3,5 号电池 \"AA\""))
        assertEquals(items, EventChecklist.decode(EventChecklist.encode(items)))
    }

    @Test
    fun `归一化会压平换行、丢空项、按标题去重且保持原顺序`() {
        val normalized = EventChecklist.normalize(
            listOf(
                ChecklistItem("a", "  订机票  "),
                ChecklistItem("b", ""),
                ChecklistItem("c", "行\n李"),
                ChecklistItem("d", "订机票"),
            ),
        )

        assertEquals(listOf("订机票", "行 李"), normalized.map { it.title })
        // 去重保留先出现的那条（id 是 a）
        assertEquals("a", normalized.first().id)
    }

    @Test
    fun `归一化去重忽略大小写`() {
        val normalized = EventChecklist.normalize(
            listOf(ChecklistItem("a", "Passport"), ChecklistItem("b", "passport")),
        )
        assertEquals(1, normalized.size)
        assertEquals("Passport", normalized.single().title)
    }

    @Test
    fun `单个标题截断到六十字符，条目最多二十条`() {
        assertEquals(60, EventChecklist.clean("一".repeat(80)).length)

        val many = (1..30).map { ChecklistItem("id$it", "第 $it 项") }
        assertEquals(EventChecklist.MAX_COUNT, EventChecklist.normalize(many).size)
        // 截断保前 20 条，不是随机留几条
        assertEquals("第 1 项", EventChecklist.normalize(many).first().title)
        assertEquals("第 20 项", EventChecklist.normalize(many).last().title)
    }

    @Test
    fun `勾选不改变顺序`() {
        val items = listOf(
            ChecklistItem("a", "第一步", done = true),
            ChecklistItem("b", "第二步"),
            ChecklistItem("c", "第三步"),
        )
        assertEquals(listOf("第一步", "第二步", "第三步"), EventChecklist.normalize(items).map { it.title })
    }

    @Test
    fun `缺失 done 字段按未完成处理`() {
        val decoded = EventChecklist.decode("""[{"id":"a","title":"订机票"}]""")
        assertFalse(decoded.single().done)
    }

    @Test
    fun `缺失 id 时按标题生成，坏 JSON 不抛异常`() {
        val decoded = EventChecklist.decode("""[{"title":"订机票"}]""")
        assertTrue(decoded.single().id.isNotBlank())

        // 手改库塞进非法内容：只丢这一份清单，不让整条事件挂掉
        assertEquals(emptyList<ChecklistItem>(), EventChecklist.decode("{不是数组}"))
        assertEquals(emptyList<ChecklistItem>(), EventChecklist.decode("null"))
    }

    @Test
    fun `坏元素只丢自己，好的照留`() {
        val decoded = EventChecklist.decode(
            """[{"id":"a","title":"订机票","done":true},{"id":"b","title":"  "},{"id":"c","title":"订酒店"}]""",
        )
        assertEquals(listOf("订机票", "订酒店"), decoded.map { it.title })
    }

    @Test
    fun `progress 是完成比例，空清单为 0`() {
        assertEquals(0f, EventChecklist.progress(emptyList()), 0.0001f)
        assertEquals(
            0.5f,
            EventChecklist.progress(
                listOf(ChecklistItem("a", "x", true), ChecklistItem("b", "y", false)),
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            EventChecklist.progress(listOf(ChecklistItem("a", "x", true))),
            0.0001f,
        )
    }
}
