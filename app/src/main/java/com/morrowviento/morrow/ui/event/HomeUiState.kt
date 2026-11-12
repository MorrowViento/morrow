package com.morrowviento.morrow.ui.event

import androidx.compose.runtime.Immutable
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
 *
 * `@Immutable`：字段里混着 `LocalDateTime`（JDK 类型，编译器无法证明其稳定），
 * 不加注解时 Compose 会把整个参数判为不稳定、每次重组都要再比一遍；标上之后
 * 只要**实例没变**就直接跳过——每秒刷新时卡片能真正不重组。
 */
@Immutable
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
    /**
     * 卡片的元信息行（分类 · 日期 · 重复/全天）。
     *
     * 在映射时算好，不在 UI 里算：`String.format` 每次都要解析格式串、建 `Formatter`，
     * 滚列表时每帧每张卡各来两遍，是最容易看见的掉帧来源之一。
     */
    val meta: String,
) {
    val days: Int get() = abs(dayDelta)
}

/** 撤销条状态。[remainingSeconds] 由每秒时间源算出，用于展示剩余可撤销时间。 */
@Immutable
data class UndoUiState(
    val message: String,
    val remainingSeconds: Int,
)

/** 首页状态。加载中、空态、错误态都在这一个结构里，避免页面里散落 boolean。 */
@Immutable
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
            remainingToday = remainingTodayOf(target, event.isAllDay, today, now),
            meta = metaLine(event.category, target, event.repeatsYearly, event.isAllDay),
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

    /**
     * 创建→目标的时间进度；边界一律夹在 0f..1f。
     *
     * 量化到 1/256：进度环在 62dp 上画，千分之一的变化连一个像素都不到，
     * 但不量化的话它的浮点值**每秒都在变**——`HomeUiState` 因此每秒都不相等，
     * 整页每秒重组一次，只为把一圈弧线推进 0.0000017 弧度。
     */
    fun progressOf(createdAt: LocalDateTime, target: LocalDateTime, now: LocalDateTime): Float {
        val total = Duration.between(createdAt, target).toMillis()
        if (total <= 0L) return if (now.isAfter(target)) 1f else 0f
        val elapsed = Duration.between(createdAt, now).toMillis()
        val raw = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        return (raw * PROGRESS_STEPS).toInt() / PROGRESS_STEPS
    }

    /**
     * 「今日剩余」时分秒；不属于今天、全天事件或已经过点则返回 null。
     *
     * 单独抽出来是因为它**必须每秒重算**（其余字段不用）：首页每秒刷新时只重算这几条，
     * 其它卡片的实例原样复用，列表里的 item 因此不会被无谓地重组。
     */
    fun remainingTodayOf(
        target: LocalDateTime,
        isAllDay: Boolean,
        today: LocalDate,
        now: LocalDateTime,
    ): String? {
        if (isAllDay) return null
        if (target.toLocalDate() != today) return null
        if (!now.isBefore(target)) return null
        val seconds = Duration.between(now, target).seconds
        return buildString(8) {
            appendPadded(seconds / 3600, 2)
            append(':')
            appendPadded(seconds % 3600 / 60, 2)
            append(':')
            appendPadded(seconds % 60, 2)
        }
    }

    /** 元信息行：分类 · 日期（重复事件带「每年」）· 全天/具体时刻。 */
    private fun metaLine(
        category: String,
        target: LocalDateTime,
        repeatsYearly: Boolean,
        isAllDay: Boolean,
    ): String = buildString(24) {
        if (category.isNotBlank()) {
            append(category)
            append(" · ")
        }
        if (repeatsYearly) append("每年 ")
        appendPadded(target.monthValue, 1)
        append("月")
        appendPadded(target.dayOfMonth, 1)
        append("日")
        if (isAllDay) {
            append(" · 全天")
        } else {
            append(" · ")
            appendPadded(target.hour, 2)
            append(':')
            appendPadded(target.minute, 2)
        }
    }

    /**
     * 两位补零，语义同 `%02d`，但不用 `String.format`：
     * 后者每次调用都要解析格式串 + 查 Locale，在滚动的列表里是纯浪费。
     */
    private fun StringBuilder.appendPadded(value: Long, width: Int) {
        val text = value.toString()
        repeat(width - text.length) { append('0') }
        append(text)
    }

    private fun StringBuilder.appendPadded(value: Int, width: Int) = appendPadded(value.toLong(), width)

    private const val PAST_OFFSET = 1_000_000

    /** 进度量化的格数（1/256 远小于一个像素）。 */
    private const val PROGRESS_STEPS = 256f
}
