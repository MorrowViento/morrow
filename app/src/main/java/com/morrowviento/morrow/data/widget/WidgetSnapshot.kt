package com.morrowviento.morrow.data.widget

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.time.CalendarMath
import com.morrowviento.morrow.domain.time.RepeatRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * 小部件里的一行事件。
 *
 * 与 `EventCardModel` 分开：卡片带 `remainingToday`（秒级）、`progress`（依赖 now 的浮点）
 * 这类与小部件无关的字段；小部件是**冷启动时才刷新一次**的 RemoteViews，
 * 需要的是"离线也算得出来的静态文案"（天数、单位、元信息、百分比）。
 *
 * 完全是纯数据 + 纯函数，可 JVM 单测。
 */
data class WidgetRow(
    val id: String,
    val title: String,
    val category: String,
    /** 下一次目标时刻（每年重复事件为当年/次年周年）。 */
    val target: LocalDateTime,
    /** 距目标的天数：0 = 今天，负 = 已过去。 */
    val dayDelta: Int,
    val isCountingUp: Boolean,
    val isAllDay: Boolean,
    val isPinned: Boolean,
    val repeatsYearly: Boolean,
    /** 0..100；-1 表示不该画进度（重复事件或创建时间晚于目标）。 */
    val progressPercent: Int,
) {
    val days: Int get() = abs(dayDelta)
    val isToday: Boolean get() = dayDelta == 0

    /** 徽章主文案：今天显示"今天"，其余显示天数。 */
    val badgeText: String get() = if (isToday) "今天" else days.toString()

    /** 徽章副文案：今天留空，其余显示单位。 */
    val badgeUnit: String get() = when {
        isToday -> ""
        isCountingUp -> "已过"
        else -> "天"
    }

    /**
     * 元信息行：分类 · 日期 · 时刻/全天。
     *
     * 与首页卡片保持一致（`每年 3月31日`、全天不显示时分），
     * 区别只是日期与分类可以分别关掉。
     */
    fun metaLine(showCategory: Boolean, showDate: Boolean): String {
        val parts = mutableListOf<String>()
        if (showCategory && category.isNotBlank()) parts += category
        if (showDate) {
            val date = "%d月%d日".format(target.monthValue, target.dayOfMonth)
            parts += if (repeatsYearly) "每年 $date" else date
            parts += if (isAllDay) "全天" else "%02d:%02d".format(target.hour, target.minute)
        }
        return parts.joinToString(" · ")
    }
}

/** 一次刷新要用到的完整快照。 */
data class WidgetSnapshot(
    val rows: List<WidgetRow>,
    /** 未完成事件总数（用于头部"N 个事件"）；大于 rows.size 时说明被 limit 截断。 */
    val activeCount: Int,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** 头部副标题：`3 个事件`；一个都没有时给空态文案。 */
    val headerMeta: String get() = if (activeCount == 0) "暂无进行中" else "$activeCount 个事件"

    companion object {
        val EMPTY = WidgetSnapshot(emptyList(), 0)
    }
}

/**
 * 小部件内容构建（纯函数）。
 *
 * 排序刻意与首页一致：置顶优先 → 未来按临近升序、已过统一排队尾 → 标题兜底，
 * 这样桌面看到的第一条与打开 App 看到的第一条不会打架。
 */
object WidgetSnapshotBuilder {

    /** 已过事件统一排到未来事件之后，同时"刚过去"优先。 */
    private const val PAST_OFFSET = 1_000_000

    private val ROW_ORDER: Comparator<WidgetRow> =
        compareByDescending<WidgetRow> { it.isPinned }
            .thenBy { if (it.dayDelta >= 0) it.dayDelta else PAST_OFFSET + abs(it.dayDelta) }
            .thenBy { it.title }

    /**
     * 总览快照。
     *
     * @param limit 最多取几行（由样式预设决定：极简 1 / 标准 3 / 详细 5）
     * @param now 仅用于算进度百分比；传进来而不是内部取 `LocalDateTime.now()`，保证可单测
     */
    fun overview(
        events: List<CountdownEvent>,
        today: LocalDate,
        now: LocalDateTime,
        limit: Int,
    ): WidgetSnapshot {
        val active = events.filter { !it.isCompleted }
        val rows = active.map { toRow(it, today, now) }
            .sortedWith(ROW_ORDER)
            .take(limit.coerceAtLeast(1))
        return WidgetSnapshot(rows = rows, activeCount = active.size)
    }

    /** 单事件小部件：绑定的 id 找不到事件时返回 null（由 UI 显示"事件已删除"）。 */
    fun single(event: CountdownEvent?, today: LocalDate, now: LocalDateTime): WidgetRow? =
        event?.let { toRow(it, today, now) }

    fun toRow(event: CountdownEvent, today: LocalDate, now: LocalDateTime): WidgetRow {
        val target = RepeatRule.upcomingTarget(event, today)
        val dayDelta = CalendarMath.daysUntil(target.toLocalDate(), today)
        return WidgetRow(
            id = event.id,
            title = event.title,
            category = event.category,
            target = target,
            dayDelta = dayDelta,
            // 与首页 EventPresentation 同一条规则：显式正数 or AUTO 且已过期
            isCountingUp = event.direction == com.morrowviento.morrow.domain.model.CountDirection.COUNTUP || dayDelta < 0,
            isAllDay = event.isAllDay,
            isPinned = event.isPinned,
            repeatsYearly = event.repeatsYearly,
            progressPercent = progressPercent(event, target, now),
        )
    }

    /**
     * 创建 → 目标的百分比。重复事件恒为 -1（"上一周年到现在"没有展示意义）。
     */
    private fun progressPercent(event: CountdownEvent, target: LocalDateTime, now: LocalDateTime): Int {
        if (event.repeatsYearly) return -1
        val total = Duration.between(event.createdAt, target).toMillis()
        if (total <= 0L) return if (now.isAfter(target)) 100 else -1
        val elapsed = Duration.between(event.createdAt, now).toMillis()
        return ((elapsed.toDouble() / total) * 100).toInt().coerceIn(0, 100)
    }
}
