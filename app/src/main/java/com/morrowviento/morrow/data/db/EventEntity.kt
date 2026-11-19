package com.morrowviento.morrow.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 事件表。时间统一存为本地时区的「毫秒时间戳」，避免 Room 对 java.time 的额外依赖；
 * 提醒列表存 JSON（数量上限 5 条，独立建表收益不足）。
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** 目标时刻（本地时区毫秒） */
    @ColumnInfo(name = "target_at") val targetAt: Long,
    val category: String,
    val note: String,
    val icon: String,
    /** CountDirection.name */
    val direction: String,
    /** JSON 数组：[{id, minutesBefore, enabled}] */
    val reminders: String,
    @ColumnInfo(name = "is_all_day") val isAllDay: Boolean,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    /** RepeatType.name */
    @ColumnInfo(name = "repeat_type") val repeatType: String,
    @ColumnInfo(name = "repeat_month") val repeatMonth: Int?,
    @ColumnInfo(name = "repeat_day") val repeatDay: Int?,
    /** 重复间隔（每 N 天/周/月/年），4.2.0 起。 */
    @ColumnInfo(name = "repeat_interval", defaultValue = "1") val repeatInterval: Int = 1,
    /** 每周重复选中的星期，逗号分隔的 ISO 1..7；空串表示跟锚点同一天。 */
    @ColumnInfo(name = "repeat_weekdays", defaultValue = "''") val repeatWeekdays: String = "",
    /** 年循环是否按农历（repeat_month/repeat_day 此时是农历月日）。 */
    @ColumnInfo(name = "repeat_on_lunar", defaultValue = "0") val repeatOnLunar: Boolean = false,
    /** 重复结束日期（epochDay），null 表示永不结束。 */
    @ColumnInfo(name = "repeat_until") val repeatUntil: Long? = null,
    /** 持续事件结束日期（epochDay），null 表示单日。 */
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
    /** 持续事件结束日的具体时刻（分钟数 0..1439）；null 表示全天或沿用开始时刻。 */
    @ColumnInfo(name = "end_time") val endTime: Int? = null,
    /** 创建时刻（本地时区毫秒） */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(defaultValue = "'TODO'") val kind: String = "TODO",
    @ColumnInfo(name = "last_handled_occurrence") val lastHandledOccurrence: Long? = null,
    /** 多维标记，逗号分隔（标签本身不含逗号，见 EventTags.clean）。 */
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    /** 归档时刻（本地时区毫秒）；null 表示未归档。归档 ≠ 完成，见 CountdownEvent。 */
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    /** 预计时长（分钟），0 表示未指定；只影响时间块高度。 */
    @ColumnInfo(name = "duration_minutes", defaultValue = "0") val durationMinutes: Int = 0,
    /** 子任务清单，JSON 数组：[{id, title, done}]。空串表示没有子任务。 */
    @ColumnInfo(defaultValue = "''") val checklist: String = "",
    /** 纪念日的「第 N 天」里程碑，逗号分隔的整数（"100,300"）。空串表示没有。 */
    @ColumnInfo(defaultValue = "''") val milestones: String = "",
)
