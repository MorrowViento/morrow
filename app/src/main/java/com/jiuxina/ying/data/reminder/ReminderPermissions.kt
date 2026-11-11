package com.jiuxina.ying.data.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * 提醒所需的两项系统能力探测与跳转。
 *
 * 与调度器分开：权限状态要能在 UI 线程按需读取（每次进入诊断页、每次回到前台），
 * 而调度器的调用会写系统闹钟。
 */
class ReminderPermissions(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(android.app.NotificationManager::class.java)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** 通知总开关（含渠道被用户关掉的情况由系统统一反映在这里）。 */
    fun notificationsAllowed(): Boolean = notificationManager.areNotificationsEnabled()

    /** 是否允许精确闹钟（Android 14 起新装应用默认拒绝）。 */
    fun exactAlarmsAllowed(): Boolean = alarmManager.canScheduleExactAlarms()

    fun openNotificationSettings() {
        launch(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName),
        )
    }

    fun openExactAlarmSettings() {
        launch(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData("package:${appContext.packageName}".toUri()),
        )
    }

    private fun launch(intent: Intent) {
        // 部分 ROM 裁掉了这些设置页，加 NEW_TASK 并兜住 ActivityNotFoundException
        runCatching {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData("package:${appContext.packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
