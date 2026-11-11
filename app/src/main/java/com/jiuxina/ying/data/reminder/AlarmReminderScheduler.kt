package com.jiuxina.ying.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.time.ReminderPlan
import com.jiuxina.ying.domain.time.ScheduledReminder
import com.jiuxina.ying.reminder.ReminderReceiver

/**
 * AlarmManager 实现。
 *
 * 槽位模型：每个事件固定占用 [SLOT_COUNT] 个 PendingIntent 槽位，`requestCode` 就是槽位下标，
 * `Intent.data` 带上事件 id 做区分。这样做有两个好处：
 * 1. 取消不需要额外持久化索引——槽位是纯函数，直接按 0..SLOT_COUNT-1 扫一遍即可全清；
 * 2. 事件之间不会互相覆盖——即使 requestCode 相同，`filterEquals` 也会因 data 不同而视为不同闹钟。
 *
 * 关闭某个提醒时，同步逻辑会把剩余槽位按序重排、并清掉尾部空槽，
 * 因此「删掉一条提醒」不需要知道它原来排在第几个槽。
 */
class AlarmReminderScheduler(context: Context) : ReminderScheduler {

    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    override fun sync(event: CountdownEvent) {
        val plan = ReminderPlan.upcoming(event, System.currentTimeMillis())
        plan.forEachIndexed { slot, item -> arm(event.id, slot, item) }
        for (slot in plan.size until SLOT_COUNT) cancelSlot(event.id, slot)
    }

    override fun syncAll(events: List<CountdownEvent>) {
        events.forEach { sync(it) }
    }

    override fun cancel(eventId: String) {
        for (slot in 0 until SLOT_COUNT) cancelSlot(eventId, slot)
        cancelSnooze(eventId)
    }

    override fun snooze(eventId: String, minutes: Int) {
        val pi = PendingIntent.getBroadcast(
            appContext,
            SNOOZE_REQUEST,
            snoozeIntent(eventId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAt(System.currentTimeMillis() + minutes * MINUTE_MILLIS, pi, "snooze:$eventId")
    }

    // ---- 内部 ----

    private fun arm(eventId: String, slot: Int, item: ScheduledReminder) {
        val pi = PendingIntent.getBroadcast(
            appContext,
            slot,
            fireIntent(eventId, item),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAt(item.triggerAtMillis, pi, "slot=$slot event=$eventId 提前${item.minutesBefore}分")
    }

    private fun cancelSlot(eventId: String, slot: Int) {
        val pi = PendingIntent.getBroadcast(
            appContext,
            slot,
            baseIntent(eventId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pi)
        // 不 cancel() 掉 PendingIntent 本身：槽位会被下一次 arm 复用，留着更省一次创建
    }

    private fun cancelSnooze(eventId: String) {
        val pi = PendingIntent.getBroadcast(
            appContext,
            SNOOZE_REQUEST,
            snoozeIntent(eventId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pi)
    }

    /**
     * 有精确闹钟权限就走 `setExactAndAllowWhileIdle`（Doze 下仍准时）；
     * 没有则退化到 `setAndAllowWhileIdle`——仍会响，但系统可能推迟几分钟。
     * 这个降级在提醒诊断页里会明确告知用户，不做静默处理。
     *
     * 降级分支必须留日志：真机上「提前 10 分钟实际晚 4 分钟」就是这条路径的表现，
     * 没有日志时只能靠猜。每次排期最多 10 个闹钟，不会把 logcat 刷爆。
     */
    private fun setAt(atMillis: Long, pi: PendingIntent, origin: String) {
        try {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                Log.w(
                    TAG,
                    "无精确闹钟权限，降级为不精确闹钟（实际触发可能延后数分钟）：$origin at=$atMillis",
                )
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            // 权限在授权后被用户撤销时可能走到这里
            Log.w(TAG, "精确闹钟被拒，退化为不精确闹钟：$origin", e)
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) }
        }
    }

    private fun baseIntent(eventId: String): Intent =
        Intent(appContext, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData("$SCHEME_REMINDER/$eventId".toUri())

    private fun fireIntent(eventId: String, item: ScheduledReminder): Intent =
        baseIntent(eventId)
            .putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            .putExtra(ReminderReceiver.EXTRA_MINUTES, item.minutesBefore)
            .putExtra(ReminderReceiver.EXTRA_TARGET, item.targetAtMillis)
            // 计划触发时刻随 intent 带到接收器，才能算出「系统推迟了多久」
            .putExtra(ReminderReceiver.EXTRA_TRIGGER_AT, item.triggerAtMillis)

    private fun snoozeIntent(eventId: String): Intent =
        Intent(appContext, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData("$SCHEME_SNOOZE/$eventId".toUri())
            .putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            .putExtra(ReminderReceiver.EXTRA_MINUTES, 0)

    companion object {
        private const val TAG = "ReminderScheduler"
        private const val MINUTE_MILLIS = 60_000L
        private const val SNOOZE_REQUEST = 9_000

        /** 与 [ReminderPlan.MAX_ALARMS] 保持一致：5 条提醒 × 2 次落点。 */
        const val SLOT_COUNT = ReminderPlan.MAX_ALARMS

        const val SCHEME_REMINDER = "ying://reminder"
        const val SCHEME_SNOOZE = "ying://snooze"
    }
}
