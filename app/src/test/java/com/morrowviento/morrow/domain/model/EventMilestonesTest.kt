package com.morrowviento.morrow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纪念日里程碑的编解码与归一化。
 *
 * 这一层的价值在于「脏输入不会把整条事件弄坏」——里程碑存在事件表的一列里，
 * 解码抛出就等于用户丢了一条纪念日。
 */
class EventMilestonesTest {

    @Test
    fun `归一化丢掉非正数与超上限`() {
        assertEquals(listOf(100, 300), EventMilestones.normalize(listOf(100, 0, -5, 300)))
        assertTrue(EventMilestones.normalize(listOf(EventMilestones.MAX_DAY + 1)).isEmpty())
        assertEquals(listOf(EventMilestones.MAX_DAY), EventMilestones.normalize(listOf(EventMilestones.MAX_DAY)))
    }

    @Test
    fun `归一化去重并升序`() {
        assertEquals(listOf(100, 300, 1000), EventMilestones.normalize(listOf(1000, 300, 100, 300, 100)))
    }

    @Test
    fun `归一化截断到上限`() {
        val many = (1..20).map { it * 100 }
        assertEquals(EventMilestones.MAX_COUNT, EventMilestones.normalize(many).size)
        // 截断发生在升序之后，留下的是最小的那几个
        assertEquals((1..EventMilestones.MAX_COUNT).map { it * 100 }, EventMilestones.normalize(many))
    }

    @Test
    fun `编码是逗号串且与归一化一致`() {
        assertEquals("100,300", EventMilestones.encode(listOf(300, 100, 300)))
        assertEquals("", EventMilestones.encode(emptyList()))
    }

    @Test
    fun `解码宽松：脏 token 只丢自己`() {
        assertEquals(listOf(100, 300), EventMilestones.decode("100,abc,300,,-2"))
        assertEquals(emptyList<Int>(), EventMilestones.decode(""))
        assertEquals(emptyList<Int>(), EventMilestones.decode("   "))
        // 分号不是分隔符：整串解析失败即视为一项坏值，丢掉而不是猜
        assertEquals(emptyList<Int>(), EventMilestones.decode("100;200"))
    }

    @Test
    fun `编解码往返保持稳定`() {
        val source = listOf(10, 100, 300, 365, 1000)
        assertEquals(source, EventMilestones.decode(EventMilestones.encode(source)))
    }

    @Test
    fun `文案是第 N 天`() {
        assertEquals("第 100 天", EventMilestones.label(100))
    }

    // ---- 事件模型上的派生值 ----

    private fun anniversary(
        start: java.time.LocalDate,
        milestones: List<Int>,
        kind: EventKind = EventKind.ANNIVERSARY,
    ) = CountdownEvent(
        id = "a",
        title = "在一起",
        targetDate = start.atTime(9, 0),
        isAllDay = true,
        kind = kind,
        milestones = milestones,
        createdAt = start.atTime(9, 0),
    )

    @Test
    fun `待办上的里程碑不生效`() {
        val todo = anniversary(java.time.LocalDate.of(2026, 1, 1), listOf(100), kind = EventKind.TODO)
        assertTrue(todo.activeMilestones.isEmpty())
        assertEquals(null, todo.nextMilestone(java.time.LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `已过的天数从第一天算起`() {
        val event = anniversary(java.time.LocalDate.of(2026, 1, 1), listOf(100))
        assertEquals(0L, event.elapsedDays(java.time.LocalDate.of(2026, 1, 1)))
        assertEquals(99L, event.elapsedDays(java.time.LocalDate.of(2026, 4, 10)))
    }

    @Test
    fun `下一个里程碑当天算到达`() {
        val event = anniversary(java.time.LocalDate.of(2026, 1, 1), listOf(100, 300))
        assertEquals(
            100 to java.time.LocalDate.of(2026, 4, 11),
            event.nextMilestone(java.time.LocalDate.of(2026, 4, 11)),
        )
        // 第 100 天已过 → 指向第 300 天
        assertEquals(
            300 to java.time.LocalDate.of(2026, 10, 28),
            event.nextMilestone(java.time.LocalDate.of(2026, 4, 12)),
        )
    }

    @Test
    fun `全部走完后没有下一个里程碑`() {
        val event = anniversary(java.time.LocalDate.of(2026, 1, 1), listOf(100))
        assertEquals(null, event.nextMilestone(java.time.LocalDate.of(2026, 5, 1)))
    }
}
