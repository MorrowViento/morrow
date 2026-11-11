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
    private val powerManager = appContext.getSystemService(android.os.PowerManager::class.java)

    /** 通知总开关（含渠道被用户关掉的情况由系统统一反映在这里）。 */
    fun notificationsAllowed(): Boolean = notificationManager.areNotificationsEnabled()

    /** 是否允许精确闹钟（Android 14 起新装应用默认拒绝）。 */
    fun exactAlarmsAllowed(): Boolean = alarmManager.canScheduleExactAlarms()

    /**
     * 是否已豁免电池优化。
     *
     * 国产 ROM（ColorOS / HyperOS / EMUI 等）会在这之上再叠一层自家的「后台冻结」，
     * 即便精确闹钟权限齐备，被冻结的进程也会延迟接收广播——真机上「晚了 20 秒」
     * 就是这一类的典型表现。所以这一项要和精确闹钟分开显示，不能合并成「权限已开」。
     */
    fun batteryUnrestricted(): Boolean = powerManager.isIgnoringBatteryOptimizations(appContext.packageName)

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

    /** 打开系统的「电池优化」列表页，由用户自行把本应用设为「不受限制」。 */
    fun openBatterySettings() {
        launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
