package com.jiuxina.ying.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.reminder.ReminderPermissions
import com.jiuxina.ying.data.reminder.ReminderScheduler
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.EventReminder
import com.jiuxina.ying.domain.time.ReminderPlan
import com.jiuxina.ying.domain.time.ScheduledReminder
import com.jiuxina.ying.reminder.ReminderNotifier
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

    val state: StateFlow<ReminderUiState> = combine(
        events.observeAll(),
        refreshTick,
        ticker,
    ) { list, _, now -> build(list, now) }
        .catch { emit(ReminderUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState())

    fun refresh() {
        refreshTick.value = System.currentTimeMillis()
    }

    fun openNotificationSettings() = permissions.openNotificationSettings()

    fun openExactAlarmSettings() = permissions.openExactAlarmSettings()

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

    private fun build(list: List<CountdownEvent>, nowMillis: Long): ReminderUiState {
        val planned = list
            .flatMap { ReminderPlan.upcoming(it, nowMillis) }
            .sortedBy { it.triggerAtMillis }
        val zone = ZoneId.systemDefault()
        return ReminderUiState(
            notificationsAllowed = permissions.notificationsAllowed(),
            exactAlarmsAllowed = permissions.exactAlarmsAllowed(),
            rows = planned.take(MAX_ROWS).map { it.toRow(zone, nowMillis) },
            alarmCount = planned.size,
            eventCount = list.count { event ->
                !event.isCompleted && event.reminders.any { it.enabled }
            },
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
            permissions: ReminderPermissions,
            notifier: ReminderNotifier,
            scheduler: ReminderScheduler,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReminderViewModel(events, permissions, notifier, scheduler) as T
        }
    }
}
