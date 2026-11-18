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
    val repeatType: com.morrowviento.morrow.domain.model.RepeatType,
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
    val kind: com.morrowviento.morrow.domain.model.EventKind = com.morrowviento.morrow.domain.model.EventKind.TODO,
    val primaryAction: com.morrowviento.morrow.domain.model.EventPrimaryAction = com.morrowviento.morrow.domain.model.EventPrimaryAction.COMPLETE,
    /** 持续事件进行中（此时徽章显示「还剩几天」而不是「已经过了几天」）。 */
    val isInProgress: Boolean = false,
    /** 进行中的持续事件距结束还有几天（今天结束为 0）。 */
    val daysLeftInSpan: Int = 0,
    /** 重复规则的完整描述：「每 2 周 周一、周三，到 2027 年 1 月 1 日」。 */
    val repeatLabel: String = "不重复",
    /** 持续事件的跨度文案，单日事件为 null。 */
    val spanLabel: String? = null,
    /** 多维标记的原始值（搜索要高亮/精确比较时用）。 */
    val tags: List<String> = emptyList(),
    /** 多维标记的展示行：「#报销 #出差」；没有标签为空串。 */
    val tagLine: String = "",
    /** 归档时刻；非 null 即已归档（归档 ≠ 完成，见 CountdownEvent.archivedAt）。 */
    val archivedAt: LocalDateTime? = null,
    /** 归档箱里显示的时间文案；未归档为 null。 */
    val archivedLabel: String? = null,
    /** 预计时长文案（「1 小时」）；未指定为 null。 */
    val durationLabel: String? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
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
    /**
     * 已归档的事件：首页不显示、日历不显示、提醒与小部件都不排，
     * 只在归档箱里出现。它与 [completed] 是两件事，因此单独一段。
     */
    val archived: List<EventCardModel> = emptyList(),
    val completedExpanded: Boolean = false,
    val sortMode: SortMode = SortMode.DISTANCE,
    val isLoading: Boolean = true,
    val error: String? = null,
    val undo: UndoUiState? = null,
) {
    val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty()
    /** 归档箱不参与「总数」：它是收起来的东西，不是待处理的东西。 */
    val totalCount: Int get() = active.size + completed.size
    val archivedCount: Int get() = archived.size
}

/** 一次分区的结果。用命名结构而不是 Pair：加归档后三元组再解构很容易错位。 */
@Immutable
data class EventPartition(
    val active: List<EventCardModel> = emptyList(),
    val completed: List<EventCardModel> = emptyList(),
    val archived: List<EventCardModel> = emptyList(),
)

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
    ): EventPartition {
        // 先剥归档：归档的事件既不算「进行中」也不算「已完成」，
        // 它只是被收进抽屉。剩下的一批再按完成与否分两段。
        val (archivedEvents, visible) = events.partition { it.isArchived }
        val (doneEvents, activeEvents) = visible.partition { it.isCompleted }
        val comparator = comparator(mode)
        return EventPartition(
            active = activeEvents.map { toCard(it, today, now) }.sortedWith(comparator),
            completed = doneEvents.map { toCard(it, today, now) }.sortedWith(comparator),
            // 归档箱按归档时刻倒序：最近收起来的在最上面（用时刻而不是文案排序）
            archived = archivedEvents
                .map { toCard(it, today, now) }
                .sortedWith(compareByDescending<EventCardModel> { it.archivedAt }.thenBy { it.title }),
        )
    }

    fun toCard(event: CountdownEvent, today: LocalDate, now: LocalDateTime): EventCardModel {
        // 持续事件进行中：目标取「这一次的开始日」，不是下一次。
        // 否则 9/1–9/5 的出差在 9/3 会把卡片画成「还有 362 天」（下一次），像换了件事。
        val coveringStart = if (event.isMultiDay) RepeatRule.coveringStart(event, today) else null
        val target = coveringStart?.atTime(event.targetDate.toLocalTime())
            ?: RepeatRule.upcomingTarget(event, today)
        val dayDelta = com.morrowviento.morrow.domain.time.CalendarMath.daysUntil(target.toLocalDate(), today)
        val progress = if ((event.repeatType != com.morrowviento.morrow.domain.model.RepeatType.NONE)) 0f else progressOf(event.createdAt, target, now)
        // 进行中：按「这次到哪天结束」算剩余天数，卡片不再说「已经过了 N 天」
        val coveringEnd = if (event.isMultiDay) RepeatRule.coveringEnd(event, today) else null
        return EventCardModel(
            id = event.id,
            title = event.title,
            category = event.category,
            note = event.note,
            target = target,
            createdAt = event.createdAt,
            dayDelta = dayDelta,
            isCountingUp = coveringEnd == null && (
                event.direction == CountDirection.COUNTUP ||
                    (event.direction == CountDirection.AUTO && dayDelta < 0)
                ),
            isPinned = event.isPinned,
            isAllDay = event.isAllDay,
            isCompleted = event.isCompleted,
            repeatType = event.repeatType,
            kind = event.kind,
            primaryAction = event.primaryAction,
            progress = progress,
            remainingToday = remainingTodayOf(target, event.isAllDay, today, now),
            meta = metaLine(
                category = event.category,
                target = target,
                repeat = com.morrowviento.morrow.domain.time.RepeatText.short(event),
                span = com.morrowviento.morrow.domain.time.RepeatText.span(event),
                isAllDay = event.isAllDay,
            ),
            isInProgress = coveringEnd != null,
            daysLeftInSpan = coveringEnd?.let {
                com.morrowviento.morrow.domain.time.CalendarMath.daysUntil(it, today)
            } ?: 0,
            repeatLabel = com.morrowviento.morrow.domain.time.RepeatText.of(event),
            spanLabel = com.morrowviento.morrow.domain.time.RepeatText.span(event),
            tags = event.tags,
            tagLine = com.morrowviento.morrow.domain.model.EventTags.display(event.tags),
            archivedAt = event.archivedAt,
            archivedLabel = event.archivedAt?.let { archivedLabelOf(it, today) },
            durationLabel = durationLabelOf(event),
        )
    }

    /**
     * 时长文案。只给「非全天且指定了时长」的事件：
     * 全天事件的时长永远是被「持续」表达，写一个「24 小时」只会添乱。
     */
    fun durationLabelOf(event: CountdownEvent): String? {
        if (event.isAllDay || event.durationMinutes <= 0) return null
        val minutes = event.durationMinutes
        return when {
            minutes % 60 == 0 -> "${minutes / 60} 小时"
            minutes < 60 -> "$minutes 分钟"
            else -> "${minutes / 60} 小时 ${minutes % 60} 分"
        }
    }

    /** 归档时间：同一天只写时刻，今年写月日，跨年带年份。 */
    private fun archivedLabelOf(at: LocalDateTime, today: LocalDate): String {
        val date = at.toLocalDate()
        val clock = "%02d:%02d".format(at.hour, at.minute)
        return when {
            date == today -> "今天 $clock"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
            else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        }
    }

    /** 同一天内只复制进度或秒数确实改变的卡片，排序和其它实例保持不变。 */
    fun refreshClock(cards: List<EventCardModel>, today: LocalDate, now: LocalDateTime): List<EventCardModel> {
        var patched: MutableList<EventCardModel>? = null
        for (index in cards.indices) {
            val card = cards[index]
            val progress = if (card.repeatType != com.morrowviento.morrow.domain.model.RepeatType.NONE) 0f else progressOf(card.createdAt, card.target, now)
            val remaining = remainingTodayOf(card.target, card.isAllDay, today, now)
            if (progress == card.progress && remaining == card.remainingToday) continue
            if (patched == null) patched = cards.toMutableList()
            patched[index] = card.copy(progress = progress, remainingToday = remaining)
        }
        return patched ?: cards
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

    /** 元信息行：分类 · 重复 · 日期（持续事件带跨度）· 全天/具体时刻。 */
    private fun metaLine(
        category: String,
        target: LocalDateTime,
        repeat: String,
        span: String?,
        isAllDay: Boolean,
    ): String = buildString(32) {
        if (category.isNotBlank()) {
            append(category)
            append(" · ")
        }
        // RepeatText 的「不重复」不占地方：绝大多数事件都是单次，写出来只是噪音
        if (repeat != "不重复") {
            append(repeat)
            append(' ')
        }
        appendPadded(target.monthValue, 1)
        append("月")
        appendPadded(target.dayOfMonth, 1)
        append("日")
        span?.let {
            append(" · ")
            append(it)
        }
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
