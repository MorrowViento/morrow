package com.morrowviento.morrow.data.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「什么时候才把提醒登记成系统闹钟」。
 *
 * 真机反馈：状态栏的小闹钟图标在提醒触发后仍然挂着，用户没法判断它是不是朝夕排的。
 * 一半原因是有别的排期在后面（诊断页现在会写明归属与下一个时刻），
 * 另一半原因是以前**所有**落点都用 `setAlarmClock`——一年后的纪念日也会立刻挂上图标。
 *
 * 所以只给 12 小时内的落点登记系统闹钟，这条边界必须锁住。
 */
class AlarmClockHorizonTest {

    private val now = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun `两分钟后的提醒走系统闹钟`() {
        assertTrue(AlarmReminderScheduler.withinAlarmClockHorizon(now, now + 2 * 60_000L))
    }

    @Test
    fun `窗口边界之内仍然算`() {
        assertTrue(
            AlarmReminderScheduler.withinAlarmClockHorizon(
                now,
                now + AlarmReminderScheduler.ALARM_CLOCK_HORIZON_MILLIS,
            ),
        )
    }

    @Test
    fun `窗口之外不挂图标`() {
        assertFalse(
            AlarmReminderScheduler.withinAlarmClockHorizon(
                now,
                now + AlarmReminderScheduler.ALARM_CLOCK_HORIZON_MILLIS + 1,
            ),
        )
        // 一年后的纪念日：绝不为了它现在就在状态栏挂一个闹钟
        assertFalse(AlarmReminderScheduler.withinAlarmClockHorizon(now, now + 365 * 24 * hour))
    }
}
