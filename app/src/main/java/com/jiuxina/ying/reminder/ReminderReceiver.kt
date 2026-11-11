package com.jiuxina.ying.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jiuxina.ying.YingApp
import com.jiuxina.ying.data.settings.LegacySettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 提醒投递与通知动作的统一接收器。
 *
 * 三个 action 共用一个接收器：闹钟触发（FIRE）、通知按钮「完成」（COMPLETE）、
 * 「稍后 10 分钟」（SNOOZE）。共用的好处是动作和投递走同一套事件读取逻辑，
 * 不会出现「通知说完成了但闹钟还排着」这类不一致。
 *
 * onReceive 里用 goAsync + IO 协程读数据库：广播回调在主线程且有 10 秒硬超时，
 * 直接在里面做 Room 查询有 ANR 风险。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 取到达时刻要尽量靠前：这是「系统实际投递」的唯一可信时间戳，
        // 后面任何一行代码的耗时会污染它
        val receivedAt = System.currentTimeMillis()
        val app = context.applicationContext as? YingApp ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> complete(app, eventId)
                    ACTION_SNOOZE -> snooze(app, eventId)
                    else -> fire(app, eventId, intent, receivedAt)
                }
            } catch (t: Throwable) {
                // 接收器里抛异常会让系统杀掉进程，这里必须兜住
                Log.w(TAG, "处理提醒失败: $eventId", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 投递提醒。
     *
     * 顺序刻意如此：**先发通知，再查数据库**。闹钟广播通常伴随进程冷启动，
     * 此时 Room 尚未打开，`findById` 要先建库连接——放前面等于让用户干等。
     * 而渲染通知需要的标题、是否全天、目标时刻在排期时已知，随 Intent 带来即可；
     * 数据库只在通知发出之后用于「已完成则撤销 / 重复事件重排」这类对账。
     */
    private suspend fun fire(app: YingApp, eventId: String, intent: Intent, receivedAt: Long) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val target = intent.getLongExtra(EXTRA_TARGET, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val isAllDay = intent.getBooleanExtra(EXTRA_IS_ALL_DAY, false)

        if (target > 0L && !title.isNullOrBlank()) {
            app.reminderNotifier.show(eventId, title, isAllDay, minutes, target, receivedAt)
        } else {
            // 兼容升级前已排进系统、Intent 里还没有标题的旧闹钟
            val event = app.eventRepository.findById(eventId) ?: return
            if (event.isCompleted) return
            val fallbackTarget = event.targetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.reminderNotifier.show(event, minutes, target.takeIf { it > 0L } ?: fallbackTarget, receivedAt)
        }

        val shownAt = System.currentTimeMillis()
        recordDelivery(app, eventId, intent.getLongExtra(EXTRA_TRIGGER_AT, 0L), receivedAt, shownAt)

        // 后置对账：已完成则撤销（并发场景下排期可能没来得及清），否则按「从现在起」重排，
        // 年度重复事件靠这一步自转，补上明年的落点
        val event = app.eventRepository.findById(eventId) ?: return
        if (event.isCompleted) {
            app.reminderNotifier.cancel(eventId)
            app.reminderScheduler.cancel(eventId)
        } else {
            app.reminderScheduler.sync(event)
        }
    }

    /**
     * 把「计划 / 系统投递 / 应用发出」三个时刻落盘，供诊断页显示。
     *
     * 三段拆分是为了让「晚了」这件事可归因：`received - planned` 是系统拖延（省电策略、
     * 不精确闹钟），`shown - received` 是应用自身的处理耗时。两者混在一起时，
     * 用户只能描述「感觉晚了」，无法判断该改哪里。
     */
    private suspend fun recordDelivery(
        app: YingApp,
        eventId: String,
        plannedAt: Long,
        receivedAt: Long,
        shownAt: Long,
    ) {
        // 稍后提醒的 Intent 没有计划触发时刻，不参与延迟统计
        if (plannedAt <= 0L) return
        Log.i(
            TAG,
            "提醒已投递 event=$eventId 计划=$plannedAt 收到=$receivedAt 发出=$shownAt " +
                "系统延迟=${receivedAt - plannedAt}ms 处理耗时=${shownAt - receivedAt}ms",
        )
        runCatching {
            app.settingsRepository.putStrings(
                mapOf(
                    LegacySettingsKeys.LAST_REMINDER_TRIGGER_AT to plannedAt.toString(),
                    LegacySettingsKeys.LAST_REMINDER_FIRED_AT to receivedAt.toString(),
                    LegacySettingsKeys.LAST_REMINDER_SHOWN_AT to shownAt.toString(),
                ),
            )
        }
    }

    private suspend fun complete(app: YingApp, eventId: String) {
        val event = app.eventRepository.findById(eventId) ?: return
        if (!event.isCompleted) {
            app.eventRepository.save(event.copy(isCompleted = true))
        }
        app.reminderScheduler.cancel(eventId)
        app.reminderNotifier.cancel(eventId)
    }

    private suspend fun snooze(app: YingApp, eventId: String) {
        val event = app.eventRepository.findById(eventId) ?: return
        if (event.isCompleted) return
        app.reminderNotifier.cancel(eventId)
        app.reminderScheduler.snooze(eventId, SNOOZE_MINUTES)
    }

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_FIRE = "com.jiuxina.ying.action.REMINDER_FIRE"
        const val ACTION_COMPLETE = "com.jiuxina.ying.action.REMINDER_COMPLETE"
        const val ACTION_SNOOZE = "com.jiuxina.ying.action.REMINDER_SNOOZE"

        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_MINUTES = "minutes_before"
        const val EXTRA_TARGET = "target_at"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_ALL_DAY = "is_all_day"

        const val SNOOZE_MINUTES = 10
    }
}
