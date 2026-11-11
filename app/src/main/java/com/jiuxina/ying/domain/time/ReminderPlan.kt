package com.jiuxina.ying.domain.time

import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.model.RepeatType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 一条待触发的提醒：已从「提前 N 分钟」换算成绝对时刻。
 *
 * @param triggerAtMillis 闹钟触发时刻
 * @param targetAtMillis 该提醒对应的目标时刻（重复事件是当次周年，不是首次目标日期）
 */
data class ScheduledReminder(
    val eventId: String,
    val title: String,
    val minutesBefore: Int,
    val triggerAtMillis: Long,
    val targetAtMillis: Long,
    val isAllDay: Boolean,
)

/**
 * 提醒排期推导（纯函数，无 Android 依赖）。
 *
 * 排期链路与 Flutter 版保持一致：锚点取 [CountdownEvent.reminderAnchor]（全天事件为当天 09:00），
 * 每条提醒触发于「锚点 − 提前分钟数」。重复事件按当次周年重新推导，而不是只排首次目标。
 *
 * 只排有限条数：闹钟数量必须有上界，否则一个每年重复 + 5 条提醒的事件会无限堆积。
 * 取「最近两次仍未完全过去的落点」，配合触发后立即重排（见 ReminderReceiver），
 * 即可让年度事件长期自转，同时任意时刻在系统里最多只有 [MAX_ALARMS] 个闹钟。
 */
object ReminderPlan {

    /** 最多为单个事件排期几次落点。 */
    const val MAX_OCCURRENCES = 2

    /** 单个事件在系统里的闹钟上限（= 落点数 × 提醒条数上限），与调度器的槽位数一致。 */
    const val MAX_ALARMS = MAX_OCCURRENCES * EventReminder.MAX_COUNT

    private const val MINUTE_MILLIS = 60_000L

    fun upcoming(
        event: CountdownEvent,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduledReminder> {
        // 已完成的事件不应再打扰用户；取消已完成事件上的提醒由调用方 sync 处理
        if (event.isCompleted) return emptyList()

        val reminders = event.reminders.filter { it.enabled && it.minutesBefore >= 0 }
        if (reminders.isEmpty()) return emptyList()

        val result = ArrayList<ScheduledReminder>(reminders.size * MAX_OCCURRENCES)
        var occurrences = 0
        for (target in targetInstants(event, fromMillis, zone)) {
            if (occurrences >= MAX_OCCURRENCES) break
            var added = false
            for (reminder in reminders) {
                val triggerAt = target - reminder.minutesBefore * MINUTE_MILLIS
                // 用 <= 而非 <：恰好等于当前时刻的提醒已经没有意义
                if (triggerAt <= fromMillis) continue
                added = true
                result += ScheduledReminder(
                    eventId = event.id,
                    title = event.title,
                    minutesBefore = reminder.minutesBefore,
                    triggerAtMillis = triggerAt,
                    targetAtMillis = target,
                    isAllDay = event.isAllDay,
                )
            }
            // 该落点的所有提醒都已过期时不计入次数，否则会把名额浪费在过去
            if (added) occurrences++
        }
        return result.sortedBy { it.triggerAtMillis }
    }

    /**
     * 事件的所有候选目标时刻（升序）。
     *
     * 非重复事件只有一个；每年重复事件取「今年前后各若干年」的周年，
     * 多取一年是为了让 12 月 31 日这类跨年边界也有足够的未来落点。
     */
    private fun targetInstants(event: CountdownEvent, fromMillis: Long, zone: ZoneId): List<Long> {
        if (event.repeatType != RepeatType.YEARLY) {
            return listOf(event.reminderAnchor.toMillis(zone))
        }
        val month = event.repeatMonth ?: event.targetDate.monthValue
        val day = event.repeatDay ?: event.targetDate.dayOfMonth
        val time = event.targetDate.toLocalTime()
        // 用 Instant.atZone 而不是 LocalDate.ofInstant：后者在 Android 上要 API 34，minSdk 只有 33
        val anchorYear = Instant.ofEpochMilli(fromMillis).atZone(zone).year
        return (anchorYear - 1..anchorYear + 2)
            .map { CalendarMath.anniversaryOn(it, month, day).atTime(time).toMillis(zone) }
            .sorted()
    }

    private fun LocalDateTime.toMillis(zone: ZoneId): Long =
        atZone(zone).toInstant().toEpochMilli()
}
