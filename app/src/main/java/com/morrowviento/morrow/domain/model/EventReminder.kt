package com.morrowviento.morrow.domain.model

/**
 * 单个提醒项。对应 Flutter 版 `EventReminder`。
 *
 * @param minutesBefore 提前分钟数，0 表示事件发生时
 */
data class EventReminder(
    val id: String,
    val minutesBefore: Int,
    val enabled: Boolean = true,
) {
    companion object {
        const val MAX_COUNT = 5

        /**
         * 归一化：过滤负数、按分钟数去重（保留首个）、按分钟数降序、最多 [MAX_COUNT] 条。
         * 与 Flutter 版 `EventReminder.normalize` 行为一致。
         */
        fun normalize(values: Iterable<EventReminder>): List<EventReminder> {
            val byMinutes = LinkedHashMap<Int, EventReminder>()
            for (value in values) {
                if (value.minutesBefore < 0) continue
                byMinutes.putIfAbsent(value.minutesBefore, value)
            }
            return byMinutes.values
                .sortedByDescending { it.minutesBefore }
                .take(MAX_COUNT)
        }

        /** 提醒文案，例如「事件发生时」「提前 3 天」「提前 30 分钟」。 */
        fun labelForMinutes(minutes: Int): String = when {
            minutes == 0 -> "事件发生时"
            minutes >= 1440 && minutes % 1440 == 0 -> "提前 ${minutes / 1440} 天"
            minutes >= 60 && minutes % 60 == 0 -> "提前 ${minutes / 60} 小时"
            else -> "提前 $minutes 分钟"
        }
    }
}
