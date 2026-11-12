package com.morrowviento.morrow.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.reminder.ReminderPermissions
import com.morrowviento.morrow.data.reminder.ReminderScheduler
import com.morrowviento.morrow.data.settings.LegacySettingsKeys
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.time.ReminderPlan
import com.morrowviento.morrow.domain.time.ScheduledReminder
import com.morrowviento.morrow.reminder.ReminderNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 提醒诊断 ViewModel。
 *
 * 列表不是从 AlarmManager 反查（系统不提供枚举接口），而是用同一套 [ReminderPlan]
 * 纯函数重新推导——推导结果就是调度器排期时的输入，因此页面显示与实际闹钟一致。
 */
class ReminderViewModel(
    private val events: EventRepository,
    private val settings: SettingsRepository,
    private val permissions: ReminderPermissions,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0L)

    /** 相对时间（「3 小时后」）需要随时间推移刷新，但 30 秒粒度足够。 */
    private val ticker: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    /** 上一次投递的「系统延迟 → 应用耗时」；任一时刻缺失时为 null。 */
    private val lastDelivery: Flow<Pair<Long, Long>?> = combine(
        settings.rawString(LegacySettingsKeys.LAST_REMINDER_TRIGGER_AT),
        settings.rawString(LegacySettingsKeys.LAST_REMINDER_FIRED_AT),
        settings.rawString(LegacySettingsKeys.LAST_REMINDER_SHOWN_AT),
    ) { plannedPref, firedPref, shownPref ->
        val planned = plannedPref?.toLongOrNull()
        val fired = firedPref?.toLongOrNull()
        val shown = shownPref?.toLongOrNull()
        if (planned == null || fired == null || shown == null) null else (fired - planned) to (shown - fired)
    }

    val state: StateFlow<ReminderUiState> = combine(
        events.observeAll(),
        refreshTick,
        ticker,
        lastDelivery,
    ) { list, _, now, delivery -> build(list, now, delivery) }
        .catch { emit(ReminderUiState(isLoading = false)) }
        // build() 里全是 Binder 调用（通知/精确闹钟/电池策略/下一个系统闹钟），
        // 30 秒一次落在主线程上会和绘制抢帧；挪到后台线程算，结果照样是状态流。
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState())

    fun refresh() {
        refreshTick.value = System.currentTimeMillis()
    }

    fun openNotificationSettings() = permissions.openNotificationSettings()

    fun openExactAlarmSettings() = permissions.openExactAlarmSettings()

    fun openBatterySettings() = permissions.openBatterySettings()

    fun sendTest() {
        runCatching {
            notifier.ensureChannel()
            notifier.test()
        }
    }

    /** 手动重新排期：用户怀疑闹钟丢了（换机、清缓存、系统清理）时的兜底按钮。 */
    fun resync() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { scheduler.syncAll(events.loadAll()) }
            refresh()
        }
    }

    private fun build(
        list: List<CountdownEvent>,
        nowMillis: Long,
        delivery: Pair<Long, Long>?,
    ): ReminderUiState {
        val planned = list
            .flatMap { ReminderPlan.upcoming(it, nowMillis) }
            .sortedBy { it.triggerAtMillis }
        val zone = ZoneId.systemDefault()
        return ReminderUiState(
            notificationsAllowed = permissions.notificationsAllowed(),
            exactAlarmsAllowed = permissions.exactAlarmsAllowed(),
            batteryUnrestricted = permissions.batteryUnrestricted(),
            systemAlarm = permissions.nextSystemAlarm(),
            lastSchedule = permissions.lastSchedule(),
            rows = planned.take(MAX_ROWS).map { it.toRow(zone, nowMillis) },
            alarmCount = planned.size,
            eventCount = list.count { event ->
                !event.isCompleted && event.reminders.any { it.enabled }
            },
            lastDelayMillis = delivery?.first,
            lastHandleMillis = delivery?.second,
            isLoading = false,
        )
    }

    private fun ScheduledReminder.toRow(zone: ZoneId, nowMillis: Long): ReminderRow {
        val trigger = LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMillis), zone)
        return ReminderRow(
            id = "$eventId#$triggerAtMillis",
            title = title,
            label = EventReminder.labelForMinutes(minutesBefore),
            dateText = trigger.format(DATE_TIME),
            relativeText = relativeText(triggerAtMillis - nowMillis),
        )
    }

    private fun relativeText(diffMillis: Long): String = when {
        diffMillis <= 0L -> "即将触发"
        diffMillis < HOUR_MILLIS -> "${diffMillis / MINUTE_MILLIS} 分钟后"
        diffMillis < DAY_MILLIS -> "${diffMillis / HOUR_MILLIS} 小时后"
        else -> "${diffMillis / DAY_MILLIS} 天后"
    }

    companion object {
        private const val MAX_ROWS = 12
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 3_600_000L
        private const val DAY_MILLIS = 86_400_000L

        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")

        fun factory(
            events: EventRepository,
            settings: SettingsRepository,
            permissions: ReminderPermissions,
            notifier: ReminderNotifier,
            scheduler: ReminderScheduler,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReminderViewModel(events, settings, permissions, notifier, scheduler) as T
        }
    }
}
