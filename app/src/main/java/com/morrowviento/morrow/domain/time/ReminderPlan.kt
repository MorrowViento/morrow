package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
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
    val primaryAction: com.morrowviento.morrow.domain.model.EventPrimaryAction = com.morrowviento.morrow.domain.model.EventPrimaryAction.COMPLETE,
    /**
     * 非 0 表示这是一条「第 N 天」里程碑提醒。
     *
     * 里程碑的锚点是绝对的，不在重复规则的发生日里，因此收件方**不能**拿
     * [ReminderPlan.isValidTarget] 去校验它——那条校验只认发生日，会把刚发出的
     * 里程碑通知当成过期内容立刻撤掉。
     */
    val milestoneDay: Int = 0,
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

    /**
     * 单个事件在系统里的闹钟上限。
     *
     * = 落点数 × 提醒条数上限 **+ 里程碑条数上限**。里程碑与普通提醒共用一套槽位，
     * 少算这一段的话，排在最末尾的里程碑会用到超界槽位，而取消时只扫到上界——
     * 闹钟留在系统里再也清不掉，用户会收到一条来自「已删除事件」的提醒。
     */
    const val MAX_ALARMS =
        MAX_OCCURRENCES * EventReminder.MAX_COUNT + com.morrowviento.morrow.domain.model.EventMilestones.MAX_COUNT

    private const val MINUTE_MILLIS = 60_000L

    fun upcoming(
        event: CountdownEvent,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduledReminder> {
        // 已完成的事件不应再打扰用户；取消已完成事件上的提醒由调用方 sync 处理
        if (event.isCompleted) return emptyList()
        // 归档同理：归档的意思就是「这件事先不要出现在我眼前」，
        // 提醒还照响的话归档就成了纯粹的自欺欺人。恢复归档后由调用方重新 sync。
        if (event.isArchived) return emptyList()

        val result = ArrayList<ScheduledReminder>()

        val reminders = event.reminders.filter { it.enabled && it.minutesBefore >= 0 }
        if (reminders.isNotEmpty()) {
            var occurrences = 0
            val earliestTarget = fromMillis + reminders.minOf { it.minutesBefore } * MINUTE_MILLIS
            for (target in targetInstants(event, earliestTarget, zone)) {
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
                        primaryAction = event.primaryAction,
                    )
                }
                // 该落点的所有提醒都已过期时不计入次数，否则会把名额浪费在过去
                if (added) occurrences++
            }
        }

        result += milestoneReminders(event, fromMillis, zone)

        // 槽位是有上界的：多出来的宁可排不进去，也不能生成调度器清不掉的闹钟
        return result.sortedBy { it.triggerAtMillis }.take(MAX_ALARMS)
    }

    /**
     * 里程碑提醒（「第 100 天」「第 300 天」）。
     *
     * 锚点是 `开始日 + N 天` 的提醒时刻，**绝对**且只发生一次：即便这是每年重复的生日，
     * 第 100 天也只有那么一天。已经过去的里程碑直接跳过，不做补偿提醒——
     * 「补发一条 40 天前的恭喜」只会让人困惑。
     *
     * 动作刻意是 [EventPrimaryAction.NONE]：里程碑没有「完成」这回事，
     * 给个按钮反而要解释「点完会发生什么」。
     */
    private fun milestoneReminders(
        event: CountdownEvent,
        fromMillis: Long,
        zone: ZoneId,
    ): List<ScheduledReminder> {
        val days = event.activeMilestones
        if (days.isEmpty()) return emptyList()
        val elapsed = java.time.Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
            .let { java.time.temporal.ChronoUnit.DAYS.between(event.dateOnly, it) }
        // 便宜的先剪枝：整批都在过去时不必逐条构造
        if (days.all { it.toLong() < elapsed }) return emptyList()
        val anchorTime = event.reminderAnchor.toLocalTime()
        return days.mapNotNull { day ->
            val at = event.dateOnly.plusDays(day.toLong()).atTime(anchorTime).toMillis(zone)
            if (at <= fromMillis) return@mapNotNull null
            ScheduledReminder(
                eventId = event.id,
                title = event.title,
                minutesBefore = 0,
                triggerAtMillis = at,
                targetAtMillis = at,
                isAllDay = event.isAllDay,
                primaryAction = com.morrowviento.morrow.domain.model.EventPrimaryAction.NONE,
                milestoneDay = day,
            )
        }
    }

    /**
     * 事件的所有候选目标时刻（升序）。
     *
     * 非重复事件只有一个；每年重复事件取「今年前后各若干年」的周年，
     * 多取一年是为了让 12 月 31 日这类跨年边界也有足够的未来落点。
     */
    private fun targetInstants(event: CountdownEvent, fromMillis: Long, zone: ZoneId): List<Long> {
        if (event.repeatType == RepeatType.NONE) return listOf(event.reminderAnchor.toMillis(zone))
        val today = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val time = event.reminderAnchor.toLocalTime()
        // 多一个候选：当天全部提醒已经过去时仍能排足两次未来落点。
        return RepeatRule.futureOccurrences(event, today).take(MAX_OCCURRENCES + 1)
            .map { it.atTime(time).toMillis(zone) }.toList()
    }

    fun isValidTarget(event: CountdownEvent, targetAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (event.isCompleted || targetAtMillis <= 0L) return false
        val date = Instant.ofEpochMilli(targetAtMillis).atZone(zone).toLocalDate()
        if (event.lastHandledOccurrence?.let { date <= it } == true) return false
        return RepeatRule.occurrencesBetween(event, date, date).isNotEmpty() &&
            date.atTime(event.reminderAnchor.toLocalTime()).toMillis(zone) == targetAtMillis
    }

    private fun LocalDateTime.toMillis(zone: ZoneId): Long =
        atZone(zone).toInstant().toEpochMilli()
}
