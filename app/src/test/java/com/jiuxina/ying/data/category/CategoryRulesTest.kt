package com.jiuxina.ying.data.category

import com.jiuxina.ying.domain.model.EventCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRulesTest {

    private val presets = EventCategory.PRESETS

    @Test
    fun `normalize 去空白去重并保持顺序`() {
        val result = CategoryRules.normalize(listOf(" 生活 ", "工作", "生活", "", "  ", "工作"))
        assertEquals(listOf("生活", "工作"), result)
    }

    @Test
    fun `normalize 保留旧数据里的超长分类名`() {
        val longName = "这是一个非常非常长的旧分类名称"
        assertTrue(longName.length > EventCategory.MAX_NAME_LENGTH)
        assertEquals(listOf(longName), CategoryRules.normalize(listOf(longName)))
    }

    @Test
    fun `merge 把旧事件里的孤儿分类追加在末尾`() {
        val merged = CategoryRules.merge(listOf("生活", "工作"), listOf("工作", "旧分类A", "旧分类B"))
        assertEquals(listOf("生活", "工作", "旧分类A", "旧分类B"), merged)
    }

    @Test
    fun `merge 存储为空时回落到预置分类`() {
        val merged = CategoryRules.merge(emptyList(), listOf("旧分类"))
        assertEquals(presets + "旧分类", merged)
    }

    @Test
    fun `add 正常追加并去除首尾空白`() {
        val result = CategoryRules.add(listOf("生活"), "  旅行  ")
        assertEquals(CategoryEdit.Ok(listOf("生活", "旅行")), result)
    }

    @Test
    fun `add 拒绝空名与纯空白`() {
        assertEquals(CategoryEdit.Blank, CategoryRules.add(listOf("生活"), ""))
        assertEquals(CategoryEdit.Blank, CategoryRules.add(listOf("生活"), "   "))
    }

    @Test
    fun `add 拒绝重名（含首尾空白差异）`() {
        assertEquals(CategoryEdit.Duplicate, CategoryRules.add(listOf("生活"), "生活"))
        assertEquals(CategoryEdit.Duplicate, CategoryRules.add(listOf("生活"), " 生活 "))
    }

    @Test
    fun `add 拒绝超长名称`() {
        val tooLong = "一".repeat(EventCategory.MAX_NAME_LENGTH + 1)
        assertEquals(CategoryEdit.TooLong, CategoryRules.add(listOf("生活"), tooLong))
    }

    @Test
    fun `rename 保持原位置`() {
        val result = CategoryRules.rename(listOf("生活", "工作", "旅行"), "工作", "办公")
        assertEquals(CategoryEdit.Ok(listOf("生活", "办公", "旅行")), result)
    }

    @Test
    fun `rename 同名视为无变化`() {
        val current = listOf("生活", "工作")
        assertEquals(CategoryEdit.Ok(current), CategoryRules.rename(current, "工作", " 工作 "))
    }

    @Test
    fun `rename 拒绝与其它分类重名`() {
        assertEquals(
            CategoryEdit.Duplicate,
            CategoryRules.rename(listOf("生活", "工作"), "工作", "生活"),
        )
    }

    @Test
    fun `rename 拒绝空名并识别缺失分类`() {
        assertEquals(CategoryEdit.Blank, CategoryRules.rename(listOf("生活"), "生活", ""))
        assertEquals(CategoryEdit.NotFound, CategoryRules.rename(listOf("生活"), "不存在", "新名"))
    }

    @Test
    fun `remove 正常删除并保持顺序`() {
        assertEquals(
            CategoryEdit.Ok(listOf("生活", "旅行")),
            CategoryRules.remove(listOf("生活", "工作", "旅行"), "工作"),
        )
    }

    @Test
    fun `remove 不允许删到空列表`() {
        assertEquals(CategoryEdit.LastOne, CategoryRules.remove(listOf("生活"), "生活"))
    }

    @Test
    fun `remove 缺失分类返回 NotFound`() {
        assertEquals(CategoryEdit.NotFound, CategoryRules.remove(listOf("生活", "工作"), "旅行"))
    }
}
