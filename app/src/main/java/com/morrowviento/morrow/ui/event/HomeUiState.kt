package com.morrowviento.morrow.ui.event

import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.SortMode
import com.morrowviento.morrow.domain.time.RepeatRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * 首页卡片模型：把「每秒都要重算」的东西在映射时一次算好，UI 层只负责画。
 * 全部字段不可变，便于 Compose 跳过重组。
 */
data class EventCardModel(
    val id: String,
    val title: String,
    val category: String,
    val note: String,
    /** 解析后的下一次目标时刻（每年重复事件为当年/次年周年）。 */
    val target: LocalDateTime,
    val createdAt: LocalDateTime,
    /** 距目标的天数：0 = 今天，负 = 已过去。 */
    val dayDelta: Int,
    val isCountingUp: Boolean,
    val isPinned: Boolean,
    val isAllDay: Boolean,
    val isCompleted: Boolean,
    val repeatsYearly: Boolean,
    /** 创建→目标的进度，0f..1f；仅非重复事件有效（重复事件恒为 0f）。 */
    val progress: Float,
    /** 目标就在今天且未到时，形如 `03:24:15`；否则为 null。 */
    val remainingToday: String?,
) {
    val days: Int get() = abs(dayDelta)
}

/** 撤销条状态。[remainingSeconds] 由每秒时间源算出，用于展示剩余可撤销时间。 */
data class UndoUiState(
    val message: String,
    val remainingSeconds: Int,
)

/** 首页状态。加载中、空态、错误态都在这一个结构里，避免页面里散落 boolean。 */
data class HomeUiState(
    val active: List<EventCardModel> = emptyList(),
    val completed: List<EventCardModel> = emptyList(),
    val completedExpanded: Boolean = false,
    val sortMode: SortMode = SortMode.DISTANCE,
    val isLoading: Boolean = true,
    val error: String? = null,
    val undo: UndoUiState? = null,
) {
    val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty()
    val totalCount: Int get() = active.size + completed.size
}

/**
 * 纯函数展示逻辑（无 Android 依赖，可纯 JVM 单测）。
 *
 * 排序规则：置顶恒在最前；其余按 [SortMode]；同序时按标题稳定排序，
 * 避免 Room 返回顺序变化导致列表跳动。
 */
object EventPresentation {

    fun partition(
        events: List<CountdownEvent>,
        mode: SortMode,
        today: LocalDate,
        now: LocalDateTime,
    ): Pair<List<EventCardModel>, List<EventCardModel>> {
        val (doneEvents, activeEvents) = events.partition { it.isCompleted }
        val comparator = comparator(mode)
        return activeEvents.map { toCard(it, today, now) }.sortedWith(comparator) to
            doneEvents.map { toCard(it, today, now) }.sortedWith(comparator)
    }

    fun toCard(event: CountdownEvent, today: LocalDate, now: LocalDateTime): EventCardModel {
        val target = RepeatRule.upcomingTarget(event, today)
        val dayDelta = com.morrowviento.morrow.domain.time.CalendarMath.daysUntil(target.toLocalDate(), today)
        val progress = if (event.repeatsYearly) 0f else progressOf(event.createdAt, target, now)
        return EventCardModel(
            id = event.id,
            title = event.title,
            category = event.category,
            note = event.note,
            target = target,
            createdAt = event.createdAt,
            dayDelta = dayDelta,
            isCountingUp = event.direction == CountDirection.COUNTUP || dayDelta < 0,
            isPinned = event.isPinned,
            isAllDay = event.isAllDay,
            isCompleted = event.isCompleted,
            repeatsYearly = event.repeatsYearly,
            progress = progress,
            remainingToday = remainingToday(event, target, today, now),
        )
    }

    fun comparator(mode: SortMode): Comparator<EventCardModel> {
        val byMode = when (mode) {
            // 未来事件按临近程度升序；已过事件统一排到后面，且「刚过去」优先。
            SortMode.DISTANCE -> compareBy<EventCardModel> { if (it.dayDelta >= 0) it.dayDelta else PAST_OFFSET + abs(it.dayDelta) }
            SortMode.TARGET_DATE -> compareBy { it.target }
            SortMode.CREATED_AT -> compareByDescending { it.createdAt }
        }
        return compareByDescending<EventCardModel> { it.isPinned }
            .then(byMode)
            .thenBy { it.title }
    }

    /** 创建→目标的时间进度；边界一律夹在 0f..1f。 */
    fun progressOf(createdAt: LocalDateTime, target: LocalDateTime, now: LocalDateTime): Float {
        val total = Duration.between(createdAt, target).toMillis()
        if (total <= 0L) return if (now.isAfter(target)) 1f else 0f
        val elapsed = Duration.between(createdAt, now).toMillis()
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    private fun remainingToday(
        event: CountdownEvent,
        target: LocalDateTime,
        today: LocalDate,
        now: LocalDateTime,
    ): String? {
        if (event.isAllDay) return null
        if (target.toLocalDate() != today) return null
        if (!now.isBefore(target)) return null
        val seconds = Duration.between(now, target).seconds
        return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }

    private const val PAST_OFFSET = 1_000_000
}
