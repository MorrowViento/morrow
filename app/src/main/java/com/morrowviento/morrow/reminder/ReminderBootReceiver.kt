package com.morrowviento.morrow.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.morrowviento.morrow.MorrowApp
import com.morrowviento.morrow.widget.MidnightRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟重建接收器。
 *
 * AlarmManager 的闹钟在关机后全部丢失，系统时间/时区变更也会让已排的绝对时刻偏移，
 * 因此这四种广播都要重新对账一次。对账是幂等的（sync 先清后设），重复触发无害。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MorrowApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val events = app.eventRepository.loadAll()
                app.reminderScheduler.syncAll(events)
                Log.i(TAG, "已重建 ${events.size} 个事件的提醒排期（${intent.action}）")

                // M5：同一个自愈窗口顺手把小部件与跨天闹钟也对一遍——
                // 重启/改时间后小部件上的天数与"明天零点"都可能已经过期
                if (MidnightRefreshScheduler.hasAnyWidget(context)) {
                    app.widgetRefresher.refreshAll()
                    MidnightRefreshScheduler.schedule(context)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "重建提醒排期失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderBoot"
    }
}
