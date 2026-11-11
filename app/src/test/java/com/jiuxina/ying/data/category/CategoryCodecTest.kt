package com.jiuxina.ying.data.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryCodecTest {

    @Test
    fun `编解码往返保持内容与顺序`() {
        val names = listOf("生活", "工作", "旅行")
        assertEquals(names, CategoryCodec.decode(CategoryCodec.encode(names)))
    }

    @Test
    fun `编码空列表得到空 JSON 数组`() {
        assertEquals("[]", CategoryCodec.encode(emptyList()))
        assertEquals(emptyList<String>(), CategoryCodec.decode("[]"))
    }

    @Test
    fun `null 与空白输入返回 null（交由上层回落默认值）`() {
        assertNull(CategoryCodec.decode(null))
        assertNull(CategoryCodec.decode(""))
        assertNull(CategoryCodec.decode("   "))
    }

    @Test
    fun `非法 JSON 返回 null 而不是抛异常`() {
        assertNull(CategoryCodec.decode("["))
        assertNull(CategoryCodec.decode("not json"))
    }

    @Test
    fun `非数组结构返回 null`() {
        assertNull(CategoryCodec.decode("""{"a":1}"""))
    }

    @Test
    fun `数组内的非字符串元素被丢弃`() {
        val decoded = CategoryCodec.decode("""["生活",1,null,{"a":1},"工作"]""")
        assertEquals(listOf("生活", "工作"), decoded)
    }
}
