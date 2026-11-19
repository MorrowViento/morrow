package com.morrowviento.morrow.ui.event

import com.morrowviento.morrow.domain.model.ChecklistItem
import com.morrowviento.morrow.domain.model.EventChecklist
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.time.NaturalDateParser
import com.morrowviento.morrow.domain.time.ParsedEventInput
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 子任务在表单里的增删勾，以及自然语言结果合并进表单的规则。 */
class EventChecklistFormTest {

    private fun form(title: String = "搬家") = EventFormState(
        title = title,
        date = LocalDate.of(2026, 5, 20),
        time = LocalTime.of(9, 0),
    )

    // ---- 子任务 ----

    @Test
    fun `加子任务会清标题并给新 id`() {
        val next = form().addChecklistItem("  订 搬家公司 ")
        assertEquals(1, next.checklist.size)
        assertEquals("订 搬家公司", next.checklist.single().title)
        assertFalse(next.checklist.single().done)
        assertTrue(next.checklist.single().id.isNotBlank())
    }

    @Test
    fun `空标题与重复标题都不会加进去`() {
        val once = form().addChecklistItem("打包")
        assertEquals(1, once.addChecklistItem("   ").checklist.size)
        assertEquals(1, once.addChecklistItem("打包").checklist.size)
        // 忽略大小写：Pack 与 pack 只留先写的那条（打包 之外再加一条，共 2 条）
        assertEquals(2, once.addChecklistItem("Pack").addChecklistItem("pack").checklist.size)
    }

    @Test
    fun `最多二十条`() {
        var state = form()
        repeat(25) { state = state.addChecklistItem("第 ${it + 1} 项") }
        assertEquals(EventChecklist.MAX_COUNT, state.checklist.size)
    }

    @Test
    fun `删子任务按 id，勾选只翻 done 不动顺序`() {
        val state = form()
            .addChecklistItem("第一步")
            .addChecklistItem("第二步")
            .addChecklistItem("第三步")
        val ids = state.checklist.map { it.id }

        val toggled = state.toggleChecklistItem(ids[1])
        assertEquals(listOf(false, true, false), toggled.checklist.map { it.done })
        assertEquals(listOf("第一步", "第二步", "第三步"), toggled.checklist.map { it.title })
        // 再点一次取消
        assertFalse(toggled.toggleChecklistItem(ids[1]).checklist[1].done)

        assertEquals(listOf("第一步", "第三步"), state.removeChecklistItem(ids[1]).checklist.map { it.title })
    }

    @Test
    fun `落库时归一化：丢空项、去重、截上限`() {
        val event = requireNotNull(
            form().copy(
                checklist = listOf(
                    ChecklistItem("a", "打包"),
                    ChecklistItem("b", "  "),
                    ChecklistItem("c", "打包"),
                    ChecklistItem("d", "退货"),
                ),
            ).toEvent(),
        )
        assertEquals(listOf("打包", "退货"), event.checklist.map { it.title })
        assertTrue(event.hasChecklist)
    }

    @Test
    fun `从事件回填子任务`() {
        val event = requireNotNull(
            form().copy(checklist = listOf(ChecklistItem("a", "打包", done = true))).toEvent(),
        )
        val back = EventFormState.fromEvent(event)
        assertEquals(listOf("打包"), back.checklist.map { it.title })
        assertTrue(back.checklist.single().done)
    }

    // ---- 自然语言合并 ----

    private fun parsed(
        title: String = "",
        date: LocalDate? = null,
        time: LocalTime? = null,
        isAllDay: Boolean = false,
        repeatType: RepeatType = RepeatType.NONE,
        repeatWeekdays: Set<Int> = emptySet(),
        durationMinutes: Int = 0,
    ) = ParsedEventInput(
        title = title,
        date = date,
        time = time,
        isAllDay = isAllDay,
        repeatType = repeatType,
        repeatWeekdays = repeatWeekdays,
        durationMinutes = durationMinutes,
        matched = true,
    )

    @Test
    fun `没解析到的字段一律保留表单原值`() {
        val before = form("搬家").copy(
            category = "生活",
            note = "记得订车",
            checklist = listOf(ChecklistItem("a", "打包")),
            reminders = emptyList(),
        )
        val after = before.applyParsed(parsed(title = "搬家", date = before.date, isAllDay = true))

        assertEquals("生活", after.category)
        assertEquals("记得订车", after.note)
        assertEquals(1, after.checklist.size)
        // 说了是全天就不再有时刻
        assertTrue(after.isAllDay)
    }

    @Test
    fun `标题只在空着时填`() {
        val blank = form("").applyParsed(parsed(title = "开会", date = LocalDate.of(2026, 6, 1), isAllDay = true))
        assertEquals("开会", blank.title)

        val filled = form("季度复盘").applyParsed(parsed(title = "开会", date = LocalDate.of(2026, 6, 1), isAllDay = true))
        assertEquals("季度复盘", filled.title)
    }

    @Test
    fun `解析到时刻就不再是全天`() {
        val after = form().copy(isAllDay = true).applyParsed(
            parsed(date = LocalDate.of(2026, 6, 1), time = LocalTime.of(15, 0)),
        )
        assertFalse(after.isAllDay)
        assertEquals(LocalTime.of(15, 0), after.time)
        assertEquals(LocalDate.of(2026, 6, 1), after.date)
    }

    @Test
    fun `没说日期时不动表单日期，也不翻全天`() {
        val after = form().applyParsed(parsed(durationMinutes = 30))
        assertEquals(LocalDate.of(2026, 5, 20), after.date)
        assertFalse(after.isAllDay)
        assertEquals(30, after.durationMinutes)
    }

    @Test
    fun `每周重复带上周几，并且清掉农历与结束条件`() {
        val before = form().copy(
            repeatType = RepeatType.YEARLY,
            repeatOnLunar = true,
            repeatUntil = LocalDate.of(2027, 1, 1),
        )
        val after = before.applyParsed(
            parsed(date = LocalDate.of(2026, 6, 1), repeatType = RepeatType.WEEKLY, repeatWeekdays = setOf(1, 3, 5)),
        )

        assertEquals(RepeatType.WEEKLY, after.repeatType)
        assertEquals(setOf(1, 3, 5), after.repeatWeekdays)
        assertFalse(after.repeatOnLunar)
    }

    @Test
    fun `句子没提重复时保留表单原有的重复设置`() {
        val before = form().copy(repeatType = RepeatType.WEEKLY, repeatWeekdays = setOf(2), repeatUntil = LocalDate.of(2027, 1, 1))
        // 解析器给的是 NONE（句子没提重复）：保留表单原档位，但每周字段要自洽
        val after = before.applyParsed(parsed(date = LocalDate.of(2026, 6, 1), isAllDay = true))
        assertEquals(RepeatType.WEEKLY, after.repeatType)
        assertEquals(setOf(2), after.repeatWeekdays)
    }

    @Test
    fun `新日期越过结束日时结束日作废`() {
        val before = form().copy(endDate = LocalDate.of(2026, 5, 25))
        val after = before.applyParsed(parsed(date = LocalDate.of(2026, 6, 1), isAllDay = true))
        assertNull(after.endDate)
    }

    @Test
    fun `未命中的解析结果不改变表单`() {
        val before = form()
        assertTrue(before.applyParsed(ParsedEventInput(title = "", matched = false)) === before)
    }

    @Test
    fun `一句话端到端：解析器加表单`() {
        val parsed = NaturalDateParser.parse(
            "明天下午三点 开会 1 小时",
            LocalDate.of(2026, 5, 20),
            LocalTime.of(9, 0),
        )
        val after = form("").applyParsed(parsed)

        assertEquals("开会", after.title)
        assertEquals(LocalDate.of(2026, 5, 21), after.date)
        assertEquals(LocalTime.of(15, 0), after.time)
        assertEquals(60, after.durationMinutes)
        assertFalse(after.isAllDay)
    }

    // ---- 纪念日里程碑 ----

    @Test
    fun `加里程碑按升序且可再次点掉`() {
        val next = form().toggleMilestone(300).toggleMilestone(100)
        assertEquals(listOf(100, 300), next.milestones)
        assertEquals(listOf(100), next.toggleMilestone(300).milestones)
    }

    @Test
    fun `非法的天数不进入表单`() {
        val base = form()
        assertEquals(base, base.toggleMilestone(0))
        assertEquals(base, base.toggleMilestone(-100))
        assertEquals(base, base.toggleMilestone(com.morrowviento.morrow.domain.model.EventMilestones.MAX_DAY + 1))
    }

    @Test
    fun `里程碑超过上限时后加的被丢掉`() {
        var next = form()
        repeat(com.morrowviento.morrow.domain.model.EventMilestones.MAX_COUNT + 2) {
            next = next.toggleMilestone((it + 1) * 10)
        }
        assertEquals(
            com.morrowviento.morrow.domain.model.EventMilestones.MAX_COUNT,
            next.milestones.size,
        )
    }

    @Test
    fun `保存时里程碑走归一化并带上纪念日类型`() {
        val event = form()
            .copy(kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY)
            .toggleMilestone(300)
            .toEvent(existing = null)!!
        assertEquals(listOf(300), event.milestones)
        assertEquals(com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY, event.kind)
    }
}
