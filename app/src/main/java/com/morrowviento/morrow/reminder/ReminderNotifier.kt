package com.morrowviento.morrow.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import androidx.core.net.toUri
import com.morrowviento.morrow.MainActivity
import com.morrowviento.morrow.R
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 提醒通知的构造与投递。
 *
 * 通知里的日期一律取「当次落点」[targetAtMillis] 而不是 `event.targetDate`：
 * 每年重复事件的首次目标可能是几年前，直接显示原始日期会给出错误的年份。
 */
class ReminderNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.reminder_channel_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(event: CountdownEvent, minutesBefore: Int, targetAtMillis: Long, nowMillis: Long) {
        show(event.id, event.title, event.isAllDay, minutesBefore, targetAtMillis, nowMillis)
    }

    /**
     * 不依赖 [CountdownEvent] 的投递入口。
     *
     * 闹钟广播触发时进程往往是冷启动，此刻去 Room 查事件要先打开数据库文件——
     * 真机实测这段能吃掉数百毫秒乃至更久（省电模式下更糟）。而标题、是否全天
     * 这些渲染通知必需的信息在排期时就已经知道，随 Intent 带过来即可，
     * 于是**通知不必等数据库**。数据库只在投递之后用于对账。
     */
    fun show(
        eventId: String,
        title: String,
        isAllDay: Boolean,
        minutesBefore: Int,
        targetAtMillis: Long,
        nowMillis: Long,
    ) {
        val target = LocalDateTime.ofInstant(Instant.ofEpochMilli(targetAtMillis), ZoneId.systemDefault())
        val body = buildString {
            append(target.format(if (isAllDay) DATE_ONLY else DATE_TIME))
            append(" · ")
            append(relativeText(target.toLocalDate(), nowMillis))
        }
        // 动作按钮不配图标：传 Icon 需要额外资源，而 null 字面量会在两个 Builder 重载间歧义
        val noIcon: Icon? = null
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSubText(EventReminder.labelForMinutes(minutesBefore))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(eventId))
            .addAction(
                Notification.Action.Builder(
                    noIcon,
                    appContext.getString(R.string.reminder_action_complete),
                    actionIntent(eventId, ReminderReceiver.ACTION_COMPLETE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    noIcon,
                    appContext.getString(R.string.reminder_action_snooze),
                    actionIntent(eventId, ReminderReceiver.ACTION_SNOOZE),
                ).build(),
            )
            .build()
        notify(notificationId(eventId), notification)
    }

    /** 诊断页的「发一条测试通知」：走真实渠道，用于确认权限与免打扰设置。 */
    fun test(nowMillis: Long = System.currentTimeMillis()) {
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.reminder_test_title))
            .setContentText(appContext.getString(R.string.reminder_test_body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // 测试通知同样要能点开：它存在的意义就是「确认这条链路是通的」，
            // 点了没反应会让人以为整个通知系统坏了（真机就这么报过）
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext,
                    TEST_ID,
                    Intent(appContext, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        notify(TEST_ID, notification)
    }

    fun cancel(eventId: String) {
        manager.cancel(notificationId(eventId))
    }

    /**
     * 投递入口。
     *
     * 自己做一次权限判断再 notify：既满足 lint 的 MissingPermission，
     * 也让「权限被关」变成一个明确的 early return 而不是依赖系统静默丢弃。
     */
    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        if (appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { manager.notify(id, notification) }
    }

    private fun openIntent(eventId: String): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            notificationId(eventId),
            Intent(appContext, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData("$SCHEME_EVENT/$eventId".toUri())
                .putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
                .putExtra(MainActivity.EXTRA_FROM_NOTIFICATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionIntent(eventId: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            notificationId(eventId),
            Intent(appContext, ReminderReceiver::class.java)
                .setAction(action)
                .setData("morrow://$action/${eventId.lowercase()}".toUri())
                .putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun relativeText(target: LocalDate, nowMillis: Long): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, target).toInt()
        return when {
            days > 0 -> "还有 $days 天"
            days == 0 -> "就是今天"
            else -> "已过去 ${-days} 天"
        }
    }

    private fun notificationId(eventId: String): Int = eventId.hashCode() and 0x7FFFFFFF

    companion object {
        private const val CHANNEL_ID = "reminders"
        private const val TEST_ID = 1_000_100
        private const val SCHEME_EVENT = "morrow://event"

        private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")
        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")
    }
}
