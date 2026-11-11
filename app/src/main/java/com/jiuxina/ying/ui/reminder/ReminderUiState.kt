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
    /**
     * 上一条提醒被系统推迟了多久（毫秒，可正可负）。
     *
     * 这是用户唯一能自己看见的「系统是否在拖延」的证据：精确闹钟未授权时会明显为正，
     * 授权后应接近 0。为空表示还没投递过（或该次调用方没带计划时刻，例如稍后提醒）。
     */
    val lastDelayMillis: Long? = null,
    val isLoading: Boolean = true,
)

data class ReminderActions(
    val refresh: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
    val sendTest: () -> Unit,
    val resync: () -> Unit,
)
