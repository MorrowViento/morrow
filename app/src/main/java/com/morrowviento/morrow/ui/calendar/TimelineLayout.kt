package com.morrowviento.morrow.ui.calendar

import androidx.compose.runtime.Immutable
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.time.RepeatRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 时间块视图（日历第四视图）的布局模型与算法。
 *
 * 与月视图的区别：月视图回答「哪一天有事情」，时间块回答「这一天的时间被什么占着」。
 * 因此这里需要**分钟级**的定位，而不是「有没有」。
 *
 * 全部是纯函数，可在 JVM 单测里钉死重叠分列这类容易写错的逻辑。
 */
@Immutable
data class TimelineBlock(
    val id: String,
    val title: String,
    /** 开始分钟（0..1439）。 */
    val startMinute: Int,
    /** 结束分钟（1..1440）。 */
    val endMinute: Int,
    /** 重叠时占第几列（0 起）。 */
    val lane: Int,
    /** 所在重叠簇一共有几列；1 表示这一小时里没有别的事。 */
    val laneCount: Int,
    val isCompleted: Boolean,
)

/** 全天 / 跨天的一段：画在时间轴上方，不参与分列。 */
@Immutable
data class TimelineAllDay(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    /** 跨天事件在中间日显示「第 2/5 天」；单日为 null。 */
    val dayIndexLabel: String?,
)

@Immutable
data class TimelineDay(
    val date: LocalDate,
    val allDay: List<TimelineAllDay> = emptyList(),
    val blocks: List<TimelineBlock> = emptyList(),
) {
    val isEmpty: Boolean get() = allDay.isEmpty() && blocks.isEmpty()

    /** 时间轴需要滚到哪一分钟（最早的事件前一格）；空的一天停在 08:00。 */
    val scrollToMinute: Int
        get() {
            val first = blocks.minOfOrNull { it.startMinute } ?: return DEFAULT_SCROLL_MINUTE
            // 对齐到 15 分钟，滚动后第一条网格线正好落在顶部
            return TimelineLayout.snapClamped(first - TimelineLayout.SNAP_MINUTES)
        }

    private companion object {
        const val DEFAULT_SCROLL_MINUTE = 8 * 60
    }
}

/**
 * 重叠分列。
 *
 * 把一天里互相重叠的块归成一簇：簇内每块分到「最早空出来的列」，
 * 簇内所有块共用同一个列数（`laneCount`），于是同一簇的块等宽并排，
 * 不同簇的块各自占满整行——不做簇宽统一的话，上午挤成三条窄块，
 * 下午独一份的事也只有三分之一宽。
 */
object TimelineLayout {

    const val MINUTES_PER_DAY = 24 * 60

    /**
     * 时间轴的吸附粒度：15 分钟。
     *
     * 拖拽创建、拖边缘改时长都以它为步长——比 15 分钟更细的话，一小时只有 56dp 高，
     * 一像素已经接近 1 分钟，手指根本点不准；比它更粗又会让「九点半」这种常见时间放不下。
     */
    const val SNAP_MINUTES = 15

    /** 画出来的块最短 15 分钟（= 一格），否则短会会变成一条不可点的线。 */
    const val MIN_BLOCK_MINUTES = SNAP_MINUTES

    /** 把分钟吸附到 15 的整数倍（向下取整，落在格线上）。 */
    fun snapMinute(minute: Int): Int {
        val clamped = minute.coerceIn(0, MINUTES_PER_DAY - SNAP_MINUTES)
        return clamped / SNAP_MINUTES * SNAP_MINUTES
    }

    /** 吸附后再夹进一天：拖到 23:58 也落在 23:45，不会溢出到次日。 */
    fun snapClamped(minute: Int, min: Int = 0): Int =
        snapMinute(minute).coerceIn(snapMinute(min), MINUTES_PER_DAY - SNAP_MINUTES)

    @Immutable
    data class LaneAssignment(val lane: Int, val laneCount: Int)

    fun build(
        events: List<CountdownEvent>,
        date: LocalDate,
    ): TimelineDay {
        val allDay = ArrayList<TimelineAllDay>()
        val timed = ArrayList<CountdownEvent>()
        for (event in events) {
            if (!RepeatRule.coversOn(event, date)) continue
            // 全天与跨天都进顶部条：跨天的事没法在一条 24 小时轴上画出「跨过去」这一段
            if (event.isAllDay || event.isMultiDay) {
                val start = RepeatRule.coveringStart(event, date) ?: date
                val total = event.spanDays + 1
                val index = ChronoUnit.DAYS.between(start, date).toInt() + 1
                allDay += TimelineAllDay(
                    id = event.id,
                    title = event.title,
                    isCompleted = event.isCompleted,
                    dayIndexLabel = if (total > 1) "第 $index/$total 天" else null,
                )
            } else {
                timed += event
            }
        }

        val ordered = timed.sortedWith(compareBy({ it.targetDate.toLocalTime() }, { it.title }))
        val ranges = ordered.map { event ->
            val start = event.targetDate.hour * 60 + event.targetDate.minute
            val end = (start + event.blockMinutes.coerceAtLeast(MIN_BLOCK_MINUTES))
                .coerceAtMost(MINUTES_PER_DAY)
            // 23:58 开始、60 分钟的事：结束夹到 24:00，起点也得往回退一点，否则高度为 0
            start.coerceAtMost(MINUTES_PER_DAY - MIN_BLOCK_MINUTES) to end
        }
        val lanes = assignLanes(ranges)
        val blocks = ordered.mapIndexed { index, event ->
            TimelineBlock(
                id = event.id,
                title = event.title,
                startMinute = ranges[index].first,
                endMinute = ranges[index].second,
                lane = lanes[index].lane,
                laneCount = lanes[index].laneCount,
                isCompleted = event.isCompleted,
            )
        }
        return TimelineDay(date = date, allDay = allDay, blocks = blocks)
    }

    /**
     * 给一组 [start, end) 区间分配列。
     *
     * 返回值与输入同序。区间按开始时间排序（同一开始时间时长的在前，
     * 因为长块更像是「占着这条道」的那一个）。
     */
    fun assignLanes(intervals: List<Pair<Int, Int>>): List<LaneAssignment> {
        if (intervals.isEmpty()) return emptyList()
        val order = intervals.indices.sortedWith(
            compareBy({ intervals[it].first }, { -intervals[it].second }),
        )
        val result = MutableList(intervals.size) { LaneAssignment(0, 1) }
        // 当前簇内每个 lane 的「占用到哪一分钟」
        var laneEnds = ArrayList<Int>()
        var clusterStart = 0

        fun flush(upTo: Int) {
            val width = laneEnds.size.coerceAtLeast(1)
            for (pos in clusterStart until upTo) {
                val index = order[pos]
                result[index] = LaneAssignment(result[index].lane, width)
            }
            laneEnds = ArrayList()
            clusterStart = upTo
        }

        for (pos in order.indices) {
            val index = order[pos]
            val start = intervals[index].first
            val end = intervals[index].second
            // 所有已占用的列都在这个块开始前结束了 → 上一簇到此为止
            if (laneEnds.isNotEmpty() && laneEnds.all { it <= start }) flush(pos)
            val free = laneEnds.indexOfFirst { it <= start }
            if (free >= 0) {
                laneEnds[free] = end
                result[index] = LaneAssignment(free, 1)
            } else {
                laneEnds.add(end)
                result[index] = LaneAssignment(laneEnds.size - 1, 1)
            }
        }
        flush(order.size)
        return result
    }
}
