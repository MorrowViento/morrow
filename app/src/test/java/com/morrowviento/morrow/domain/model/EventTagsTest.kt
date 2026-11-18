package com.morrowviento.morrow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多维标记的归一化：三条存储路径（Room / 备份 / 撤销）都靠它收口。 */
class EventTagsTest {

    @Test
    fun `去空白、去井号、去重`() {
        assertEquals(
            listOf("报销", "出差"),
            EventTags.normalize(listOf("  报销 ", "#出差", "报销")),
        )
    }

    @Test
    fun `去重忽略大小写，但保留第一次出现的写法`() {
        assertEquals(listOf("Travel"), EventTags.normalize(listOf("Travel", "travel", "TRAVEL")))
    }

    @Test
    fun `逗号会被换成空格，因为它同时是存库分隔符`() {
        assertEquals(listOf("报销 出差"), EventTags.normalize(listOf("报销,出差")))
        // 全角逗号同理
        assertEquals(listOf("报销 出差"), EventTags.normalize(listOf("报销，出差")))
    }

    @Test
    fun `单个标签截断到上限，最多保留五个`() {
        assertEquals("一二三四五六七八", EventTags.clean("一二三四五六七八九十"))
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            EventTags.normalize(listOf("a", "b", "c", "d", "e", "f")),
        )
    }

    @Test
    fun `空白与只有一个井号的输入直接丢弃`() {
        assertEquals(emptyList<String>(), EventTags.normalize(listOf("", "   ", "#", "，")))
    }

    @Test
    fun `编解码往返等价`() {
        val tags = listOf("报销", "出差", "家人")

        assertEquals(tags, EventTags.decode(EventTags.encode(tags)))
        assertEquals(emptyList<String>(), EventTags.decode(""))
        // 手改库塞进非法内容也不该崩：重新归一化即可
        assertEquals(listOf("正常"), EventTags.decode(",正常,"))
    }

    @Test
    fun `展示行带井号，空列表是空串`() {
        assertEquals("#报销 #出差", EventTags.display(listOf("报销", "出差")))
        assertEquals("", EventTags.display(emptyList()))
        assertTrue(EventTags.MAX_COUNT >= 1)
    }
}
