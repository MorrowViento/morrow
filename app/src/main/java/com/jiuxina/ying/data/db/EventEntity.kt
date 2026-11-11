package com.jiuxina.ying.data.db

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
    /** 创建时刻（本地时区毫秒） */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
