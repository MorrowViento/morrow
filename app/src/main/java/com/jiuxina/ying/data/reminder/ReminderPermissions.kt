package com.jiuxina.ying.data.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * 状态栏那颗小闹钟图标目前由谁登记。
 *
 * 「萤」用 `setAlarmClock` 排临近提醒时会登记成系统闹钟，图标因此出现；
 * 但同一颗图标也可能来自系统时钟或其他应用，必须能区分，否则用户会把它当成应用的 bug。
 */
enum class SystemAlarmOwner {
    /** 当前没有任何闹钟，状态栏不会有图标。 */
    NONE,

    /** 萤自己排的。 */
    THIS_APP,

    /** 其他应用排的（系统时钟等），与萤无关。 */
    OTHER_APP,
}

/** 系统「下一个闹钟」的快照；[owner] 为 [SystemAlarmOwner.NONE] 时时刻无意义。 */
data class SystemAlarm(
    val owner: SystemAlarmOwner = SystemAlarmOwner.NONE,
    val triggerAtMillis: Long = 0L,
)

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

    /**
     * 系统当前登记的「下一个闹钟」。
     *
     * 状态栏/锁屏那颗小闹钟图标不是应用自己画的，而是系统对 AlarmClock 的统一展示，
     * 任何应用都能登记（系统时钟、其他倒数日应用）。用户看到图标时的第一反应是
     * 「这是谁排的、什么时候消失」，所以这里连归属一起读出来：`showIntent` 的
     * creatorPackage 就是登记方的包名。
     *
     * 读不到（ROM 裁掉了接口 / 权限异常）时按「无」处理，不阻塞诊断页。
     */
    fun nextSystemAlarm(): SystemAlarm {
        val info = runCatching { alarmManager.nextAlarmClock }.getOrNull()
            ?: return SystemAlarm()
        val owner = if (info.showIntent?.creatorPackage == appContext.packageName) {
            SystemAlarmOwner.THIS_APP
        } else {
            SystemAlarmOwner.OTHER_APP
        }
        return SystemAlarm(owner = owner, triggerAtMillis = info.triggerTime)
    }

    /**
     * 最近一次排期走的是哪条通道。
     *
     * 与 [nextSystemAlarm] 互补：前者解释「现在有没有图标」，这一条解释「为什么有 / 没有」。
     * 落点在 12 小时之外的排期根本不会登记成系统闹钟，用户却只能看到「没有图标」这个结果。
     */
    fun lastSchedule(): ScheduleTrace? = ScheduleTraceStore(appContext).last()

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
