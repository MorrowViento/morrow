package com.jiuxina.ying.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 零点到达：刷新一次小部件，并把下一次零点排上。
 *
 * 如果此时桌面上已经没有小部件了，直接取消闹钟——闹钟不该比小部件活得久。
 */
class WidgetMidnightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MidnightRefreshScheduler.ACTION) return

        if (!MidnightRefreshScheduler.hasAnyWidget(context)) {
            MidnightRefreshScheduler.cancel(context)
            return
        }
        WidgetRefreshRunner.run(this, context) { app ->
            try {
                app.widgetRefresher.refreshAll()
            } finally {
                // 刷新失败也要把明天的闹钟排上，否则跨天刷新从此断链
                MidnightRefreshScheduler.schedule(context)
            }
        }
    }
}
