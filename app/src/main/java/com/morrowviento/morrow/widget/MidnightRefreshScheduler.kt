package com.morrowviento.morrow.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

/**
 * 跨天刷新：在每个本地零点触发一次小部件刷新，然后再排下一次。
 *
 * 为什么不用 `updatePeriodMillis`：系统的周期更新最短 30 分钟且对齐到"上次刷新时刻"，
 * 跨天那一刻必然对不齐，"明天 → 今天"的天数会错到下一个周期才纠正。
 * 也不用 WorkManager：它同样不保证准时（M4 已因此放弃 work-runtime）。
 *
 * 权限处理与 M4 一致：有精确闹钟权限走 `setExactAndAllowWhileIdle`，
 * 否则退化为 `setAndAllowWhileIdle`（可能延后几分钟，但不会不触发）。
 */
object MidnightRefreshScheduler {

    private const val TAG = "WidgetMidnight"
    private const val REQUEST_CODE = 7101
    const val ACTION = "com.morrowviento.morrow.action.WIDGET_MIDNIGHT"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        // 今天之后的第一个本地零点；用 atStartOfDay 而不是 00:00 的 LocalDateTime，
        // 这样夏令时跳变的国家也不会落在"不存在的时刻"上
        val triggerAt = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val pending = pendingIntent(appContext)

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            // canScheduleExactAlarms() 与实际调用之间权限可能被撤回，兜底成不精确闹钟
            Log.w(TAG, "精确闹钟不可用，退化为不精确", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        Log.i(TAG, "已排下一次跨天刷新: $triggerAt")
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(appContext))
    }

    /** 桌面上是否还有任意一个小部件（没有就没必要占着闹钟）。 */
    fun hasAnyWidget(context: Context): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(context.applicationContext)
        return manager.getAppWidgetIds(
            android.content.ComponentName(context.applicationContext, MorrowWidgetProvider::class.java),
        ).isNotEmpty() || manager.getAppWidgetIds(
            android.content.ComponentName(context.applicationContext, MorrowDetailWidgetProvider::class.java),
        ).isNotEmpty()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetMidnightReceiver::class.java).apply {
            action = ACTION
            // data 唯一：与 M4 的 PendingIntent 身份规则一致，避免与其他闹钟互相覆盖
            data = "morrow://widget/midnight".toUri()
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
