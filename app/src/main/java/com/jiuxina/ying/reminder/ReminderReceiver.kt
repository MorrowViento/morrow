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
        val app = context.applicationContext as? YingApp ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> complete(app, eventId)
                    ACTION_SNOOZE -> snooze(app, eventId)
                    else -> fire(app, eventId, intent)
                }
            } catch (t: Throwable) {
                // 接收器里抛异常会让系统杀掉进程，这里必须兜住
                Log.w(TAG, "处理提醒失败: $eventId", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(app: YingApp, eventId: String, intent: Intent) {
        val event = app.eventRepository.findById(eventId) ?: return
        // 用户在别处已完成/删除后，残留闹钟应当安静退出
        if (event.isCompleted) return

        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val fallbackTarget = event.targetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val target = intent.getLongExtra(EXTRA_TARGET, 0L).takeIf { it > 0L } ?: fallbackTarget
        val now = System.currentTimeMillis()
        app.reminderNotifier.show(event, minutes, target, now)

        // 记下「系统实际比计划晚/早了多少」，供诊断页显示。
        // 没有触发时刻的调用方（稍后提醒）不记，否则会把延迟数据搅乱
        val planned = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
        if (planned > 0L) {
            runCatching {
                app.settingsRepository.putStrings(
                    mapOf(
                        LegacySettingsKeys.LAST_REMINDER_FIRED_AT to now.toString(),
                        LegacySettingsKeys.LAST_REMINDER_TRIGGER_AT to planned.toString(),
                    ),
                )
            }
            Log.i(TAG, "提醒已投递 event=$eventId 计划=$planned 实际=$now 延迟=${now - planned}ms")
        }

        // 年度重复事件靠这一步自转：触发后立即按「从现在起」重排，补上明年的落点
        app.reminderScheduler.sync(event)
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

        const val SNOOZE_MINUTES = 10
    }
}
