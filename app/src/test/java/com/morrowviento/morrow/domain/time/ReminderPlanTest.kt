package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 提醒排期推导的单测。
 *
 * 全部固定时区与「当前时刻」，不依赖机器时间——排期错一天在真机上极难复现，
 * 只能在纯函数层用绝对时刻断言。
 */
class ReminderPlanTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun event(
        target: LocalDateTime,
        reminders: List<EventReminder>,
        isAllDay: Boolean = false,
        isCompleted: Boolean = false,
        repeat: RepeatType = RepeatType.NONE,
    ) = CountdownEvent(
        id = "e1",
        title = "项目上线",
        targetDate = target,
        reminders = reminders,
        isAllDay = isAllDay,
        isCompleted = isCompleted,
        repeatType = repeat,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    private fun minutes(vararg values: Int) = values.mapIndexed { index, value ->
        EventReminder(id = "r$index", minutesBefore = value)
    }

    private fun millis(value: LocalDateTime): Long =
        value.atZone(zone).toInstant().toEpochMilli()

    private fun local(value: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(value), zone)

    @Test
    fun `单次事件按提前分钟数换算成绝对时刻`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 9, 0), minutes(0, 60, 1440)),
            from,
            zone,
        )

        assertEquals(3, plan.size)
        // 按触发时刻升序：提前一天 → 提前一小时 → 准时
        assertEquals(listOf(1440, 60, 0), plan.map { it.minutesBefore })
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), local(plan[0].triggerAtMillis))
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 0), local(plan[1].triggerAtMillis))
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), local(plan[2].triggerAtMillis))
        // 目标时刻统一回传，通知正文用它显示日期而不是事件首次目标
        assertTrue(plan.all { it.targetAtMillis == millis(LocalDateTime.of(2026, 12, 1, 9, 0)) })
    }

    @Test
    fun `已经过去的触发点被过滤`() {
        val from = millis(LocalDateTime.of(2026, 12, 1, 8, 30))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 9, 0), minutes(0, 60, 1440)),
            from,
            zone,
        )

        assertEquals(1, plan.size)
        assertEquals(0, plan.single().minutesBefore)
    }

    @Test
    fun `提醒全部过期时不产生排期`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2025, 1, 1, 9, 0), minutes(0, 60)),
            from,
            zone,
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `已完成事件不再排期`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 9, 0), minutes(0), isCompleted = true),
            from,
            zone,
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `停用的提醒被跳过`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val reminders = listOf(
            EventReminder("r0", 60, enabled = false),
            EventReminder("r1", 0, enabled = true),
        )
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 9, 0), reminders),
            from,
            zone,
        )

        assertEquals(listOf(0), plan.map { it.minutesBefore })
    }

    @Test
    fun `没有提醒的事件不产生排期`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 9, 0), emptyList()),
            from,
            zone,
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `全天事件以当天九点为锚点`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 0, 0), minutes(0), isAllDay = true),
            from,
            zone,
        )

        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), local(plan.single().triggerAtMillis))
        assertTrue(plan.single().isAllDay)
    }

    @Test
    fun `全天事件提前一天落在前一天九点`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 0, 0), minutes(1440), isAllDay = true),
            from,
            zone,
        )

        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), local(plan.single().triggerAtMillis))
    }

    @Test
    fun `每年重复排到下一次周年`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2020, 5, 1, 10, 0), minutes(0), repeat = RepeatType.YEARLY),
            from,
            zone,
        )

        // 2026 年的周年已过，取 2027 与 2028 两次
        assertEquals(2, plan.size)
        assertEquals(LocalDateTime.of(2027, 5, 1, 10, 0), local(plan[0].targetAtMillis))
        assertEquals(LocalDateTime.of(2028, 5, 1, 10, 0), local(plan[1].targetAtMillis))
        assertEquals(plan[0].targetAtMillis, plan[0].triggerAtMillis)
    }

    @Test
    fun `闰日周年在平年夹到二月二十八`() {
        val from = millis(LocalDateTime.of(2026, 1, 1, 0, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2024, 2, 29, 8, 0), minutes(0), repeat = RepeatType.YEARLY),
            from,
            zone,
        )

        assertEquals(LocalDateTime.of(2026, 2, 28, 8, 0), local(plan.first().targetAtMillis))
    }

    @Test
    fun `单事件排期数量有上限`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(
                LocalDateTime.of(2020, 5, 1, 10, 0),
                minutes(0, 10, 30, 60, 1440),
                repeat = RepeatType.YEARLY,
            ),
            from,
            zone,
        )

        // 两次落点 × 五条提醒 = 上限，避免无限堆积闹钟
        assertEquals(ReminderPlan.MAX_ALARMS, plan.size)
        assertEquals(10, plan.size)
    }

    @Test
    fun `同一落点的多条提醒按时间升序`() {
        val from = millis(LocalDateTime.of(2026, 11, 11, 12, 0))
        val plan = ReminderPlan.upcoming(
            event(LocalDateTime.of(2026, 12, 1, 9, 0), minutes(1440, 0, 60, 180)),
            from,
            zone,
        )

        val triggers = plan.map { it.triggerAtMillis }
        assertEquals(triggers.sorted(), triggers)
        assertEquals(listOf(1440, 180, 60, 0), plan.map { it.minutesBefore })
    }
}
