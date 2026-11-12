package com.morrowviento.morrow.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.morrowviento.morrow.MainActivity
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.time.ReminderPlan
import com.morrowviento.morrow.domain.time.ScheduledReminder
import com.morrowviento.morrow.reminder.ReminderReceiver

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

    /** 记下最近一次排期走的是哪条通道，诊断页要读它解释「图标为什么没出现」。 */
    private val trace = ScheduleTraceStore(appContext)

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
     * 排期。
     *
     * 落点在 [ALARM_CLOCK_HORIZON_MILLIS] 以内时优先 `setAlarmClock`：在系统眼里它和
     * 「用户自己设的闹钟」同级——Doze 不推迟、应用待机也照常唤醒，是目前唯一能扛住
     * 国产 ROM 后台冻结的公开 API。真机实测：精确闹钟权限齐备时
     * `setExactAndAllowWhileIdle` 仍会晚到一分钟，而这一分钟正是应用被冻结、
     * 广播排队到下一次唤醒窗口的量级。
     *
     * 代价是状态栏/锁屏会挂一个小闹钟图标，直到这次提醒触发为止。所以**只给临近的落点**
     * 登记成系统闹钟：一年后的纪念日现在就把图标挂上是纯粹的视觉污染，而那些落点也不差
     * 这一分钟。等时间推进到窗口内，冷启动自愈或上一条提醒触发后的重排会自动升级。
     *
     * 被拒（个别 ROM / 用户关闭闹钟权限）再退回精确闹钟，最后退到不精确闹钟。
     * 降级必须留日志：真机上「提前 10 分钟实际晚 4 分钟」就是这条路径的表现。
     */
    private fun setAt(atMillis: Long, pi: PendingIntent, origin: String) {
        val now = System.currentTimeMillis()
        val withinHorizon = withinAlarmClockHorizon(now, atMillis)
        if (withinHorizon) {
            val armed = runCatching {
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showAppIntent()), pi)
            }
            if (armed.isSuccess) {
                trace.record(SchedulePath.ALARM_CLOCK, atMillis, now)
                return
            }
            Log.w(TAG, "setAlarmClock 被拒，退回精确闹钟：$origin", armed.exceptionOrNull())
        }

        try {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                trace.record(SchedulePath.EXACT, atMillis, now)
            } else {
                Log.w(
                    TAG,
                    "无精确闹钟权限，降级为不精确闹钟（实际触发可能延后数分钟）：$origin at=$atMillis",
                )
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                trace.record(SchedulePath.INEXACT, atMillis, now)
            }
        } catch (e: SecurityException) {
            // 权限在授权后被用户撤销时可能走到这里
            Log.w(TAG, "精确闹钟被拒，退化为不精确闹钟：$origin", e)
            runCatching {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                trace.record(SchedulePath.INEXACT, atMillis, now)
            }
        }
    }

    /** 点状态栏那颗闹钟图标时打开朝夕；`setAlarmClock` 必需，不能传 null。 */
    private fun showAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            SHOW_APP_REQUEST,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
            // 标题与全天标记也带上：接收器冷启动时不必先开数据库就能发出通知
            .putExtra(ReminderReceiver.EXTRA_TITLE, item.title)
            .putExtra(ReminderReceiver.EXTRA_IS_ALL_DAY, item.isAllDay)

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
        private const val SHOW_APP_REQUEST = 9_100

        /**
         * 「登记成系统闹钟」的时间窗：只有 12 小时内的落点才用 `setAlarmClock`。
         *
         * 它会在状态栏挂闹钟图标，而远期提醒并不需要分钟级精度（差一分钟没人感知），
         * 却要为它挂好几天图标。窗口内的落点由冷启动自愈 / 提醒触发后重排自动升级。
         *
         * 评估过「把窗口缩到 2 小时以减少图标」，结论是**不动**：收益只是少挂几小时图标
         * （用户本来也只在待触发期间才看得到，触发即消失），代价却是升级路径变窄——
         * 用户得恰好在触发前 2 小时内打开过应用才会升级。真机上「提醒晚一分钟」的根因
         * 就是没走到这条通道，不该为了视觉再让一次。
         */
        const val ALARM_CLOCK_HORIZON_MILLIS = 12 * 60 * 60 * 1000L

        /**
         * 落点是否近到值得登记成「系统闹钟」。
         *
         * 抽成纯函数是为了能测：这条判断直接决定状态栏会不会出现图标，
         * 一旦回归成「所有排期都挂图标」，用户会以为应用坏了一直占着状态栏。
         */
        fun withinAlarmClockHorizon(nowMillis: Long, triggerAtMillis: Long): Boolean =
            triggerAtMillis - nowMillis <= ALARM_CLOCK_HORIZON_MILLIS

        /** 与 [ReminderPlan.MAX_ALARMS] 保持一致：5 条提醒 × 2 次落点。 */
        const val SLOT_COUNT = ReminderPlan.MAX_ALARMS

        const val SCHEME_REMINDER = "morrow://reminder"
        const val SCHEME_SNOOZE = "morrow://snooze"
    }
}
