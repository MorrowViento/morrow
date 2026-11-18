package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType

/**
 * 重复规则的人话文案。
 *
 * 集中一处的原因：首页卡片、日历行、详情页、桌面小部件都要写「这条怎么重复」，
 * 4.1.x 时四处各自 `when (repeatType) { YEARLY -> "每年 "; MONTHLY -> "每月 " }`，
 * 加两档重复就要改四遍，漏一处就会出现「卡片写每周、详情写不重复」。
 */
object RepeatText {

    private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 短档位名：「每天」「每周」…，用于日历这类空间紧张的地方。 */
    fun short(event: CountdownEvent): String {
        val type = event.repeatType
        if (type == RepeatType.NONE) return "不重复"
        val interval = event.repeatInterval.coerceAtLeast(1)
        val head = if (interval == 1) {
            when (type) {
                RepeatType.DAILY -> "每天"
                RepeatType.WEEKLY -> "每周"
                RepeatType.MONTHLY -> "每月"
                RepeatType.YEARLY -> "每年"
                RepeatType.NONE -> "不重复"
            }
        } else {
            when (type) {
                RepeatType.DAILY -> "每 $interval 天"
                RepeatType.WEEKLY -> "每 $interval 周"
                RepeatType.MONTHLY -> "每 $interval 个月"
                RepeatType.YEARLY -> "每 $interval 年"
                RepeatType.NONE -> "不重复"
            }
        }
        return if (type == RepeatType.YEARLY && event.repeatOnLunar) "$head·农历" else head
    }

    /** 完整描述：短档位 + 周几 + 结束日期。 */
    fun of(event: CountdownEvent): String {
        if (event.repeatType == RepeatType.NONE) return "不重复"
        val builder = StringBuilder(short(event))
        if (event.repeatType == RepeatType.WEEKLY) {
            val days = event.repeatWeekdays.filter { it in 1..7 }.sorted()
            if (days.isNotEmpty()) {
                builder.append(" ").append(days.joinToString("、") { "周" + WEEKDAY_LABELS[it - 1] })
            }
        }
        event.repeatUntil?.let {
            builder.append("，到 ").append(it.year).append(" 年 ")
                .append(it.monthValue).append(" 月 ").append(it.dayOfMonth).append(" 日")
        }
        return builder.toString()
    }

    /** 持续事件的跨度文案；单日事件返回 null。 */
    fun span(event: CountdownEvent): String? =
        if (!event.isMultiDay) null else "共 ${event.spanDays + 1} 天"
}
