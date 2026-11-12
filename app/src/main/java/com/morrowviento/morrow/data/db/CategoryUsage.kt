package com.morrowviento.morrow.data.db

import androidx.room.ColumnInfo

/** 分类使用统计（Room 投影）：`COUNT(*)` 分组结果。 */
data class CategoryUsage(
    val category: String,
    @ColumnInfo(name = "event_count") val eventCount: Int,
)
