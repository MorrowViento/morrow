package com.jiuxina.ying.ui.reminder

/** 排期列表的一行。 */
data class ReminderRow(
    /** 事件 id + 触发时刻，保证同一事件的多个提醒各自有稳定 key。 */
    val id: String,
    val title: String,
    /** 「提前 3 天」这类相对目标的描述。 */
    val label: String,
    /** 闹钟实际触发时刻。 */
    val dateText: String,
    /** 距现在多久。 */
    val relativeText: String,
)

/**
 * 提醒诊断页状态。
 *
 * 把「权限」与「排期」放在同一屏，是因为用户遇到「没提醒」时几乎无法自己区分原因：
 * 是没授权通知、没授权精确闹钟，还是压根没设提醒。三者必须同屏可见。
 */
data class ReminderUiState(
    val notificationsAllowed: Boolean = false,
    val exactAlarmsAllowed: Boolean = false,
    val rows: List<ReminderRow> = emptyList(),
    /** 排期中的提醒总数（可能大于 [rows] 展示条数）。 */
    val alarmCount: Int = 0,
    /** 设有启用提醒且未完成的事件数。 */
    val eventCount: Int = 0,
    val isLoading: Boolean = true,
)

data class ReminderActions(
    val refresh: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
    val sendTest: () -> Unit,
    val resync: () -> Unit,
)
