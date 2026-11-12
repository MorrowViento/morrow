package com.morrowviento.morrow.domain.model

/**
 * 事件排序方式。枚举名与 Flutter 版 `EventSortMode { distance, targetDate, createdAt }` 一致，
 * DataStore 里存的就是原始字符串，故 [prefValue] 保留 Dart 的驼峰写法。
 */
enum class SortMode(val prefValue: String) {
    /** 距今天数：今天最近，已过事件排在最后且按「刚过去」优先。 */
    DISTANCE("distance"),

    /** 目标日期升序。 */
    TARGET_DATE("targetDate"),

    /** 创建时间倒序（新建在前）。 */
    CREATED_AT("createdAt");

    companion object {
        fun fromPref(raw: String?): SortMode =
            entries.firstOrNull { it.prefValue == raw } ?: DISTANCE
    }
}
