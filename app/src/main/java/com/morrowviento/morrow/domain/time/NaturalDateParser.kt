package com.morrowviento.morrow.domain.time

import com.morrowviento.morrow.domain.model.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 自然语言输入的解析结果。
 *
 * 只描述「解析到了什么」，不直接产出 [com.morrowviento.morrow.domain.model.CountdownEvent]：
 * 表单还要保留用户手改的余地，所以这里给的是可回填的字段。
 */
data class ParsedEventInput(
    /** 原文去掉时间片段后剩下的部分，作为标题候选（可能为空）。 */
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** 只说了日期没说时刻 → 全天。 */
    val isAllDay: Boolean = false,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatWeekdays: Set<Int> = emptySet(),
    /** 每周重复时是否明确点了星期；false 表示「跟锚点同一天」。 */
    val weekdaysExplicit: Boolean = false,
    val durationMinutes: Int = 0,
    /** 是否至少解析出一个字段。false 时调用方应提示「没看懂」。 */
    val matched: Boolean = false,
)

/**
 * 中文自然语言 → 日期/时刻/重复/时长。纯函数、无 IO、可单测。
 *
 * 设计取舍：
 * - **只做确定性规则**，不猜。看不懂就返回 `matched = false`，让用户改用手填，
 *   而不是把「下个月找个时间」解析成某个具体日期。
 * - 按「先长后短」匹配并**从工作串里删掉**已匹配片段：先吃掉「2026年1月1日」，
 *   才不会把里面的「1日」当成时长或农历日；剩下的字自然成为标题。
 * - 不处理「工作日 / 节假日」——那需要一份会过期的调休表（与 [DateMath] 同一边界）。
 */
object NaturalDateParser {

    private val WEEKDAY_CHARS = mapOf(
        '一' to DayOfWeek.MONDAY,
        '二' to DayOfWeek.TUESDAY,
        '三' to DayOfWeek.WEDNESDAY,
        '四' to DayOfWeek.THURSDAY,
        '五' to DayOfWeek.FRIDAY,
        '六' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY,
        '天' to DayOfWeek.SUNDAY,
    )

    /** 时段词 → 小时偏移规则（返回 0..23 的小时，null 表示这个词不影响小时）。 */
    private val PERIODS = listOf("凌晨", "早上", "早晨", "上午", "中午", "下午", "傍晚", "晚上", "夜里", "晚间")

    private val RELATIVE_DAYS = mapOf(
        "今天" to 0, "今日" to 0, "明天" to 1, "明日" to 1,
        "后天" to 2, "大后天" to 3, "昨天" to -1, "前天" to -2,
    )

    private val CN_DIGITS = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    private val NUMBER = """([0-9]{1,4}|[零〇一二三四五六七八九十两]{1,4})"""

    private val RE_ABS_DATE = Regex("""([0-9]{4})\s*[年/-]\s*([0-9]{1,2})\s*[月/-]\s*([0-9]{1,2})\s*[日号]?""")
    private val RE_MONTH_DAY = Regex("""([0-9]{1,2})\s*月\s*([0-9]{1,2})\s*[日号]""")
    private val RE_RELATIVE = Regex("""(大后天|今天|今日|明天|明日|后天|昨天|前天)""")
    private val RE_RELATIVE_SPAN = Regex("""$NUMBER\s*(天|日|周|星期|个?月|年)\s*(?:以?后|之后)""")
    private val RE_WEEKDAY = Regex("""(下下|下|这|本|上)?\s*(?:周|星期|礼拜)\s*([一二三四五六日天])((?:\s*[、和及]?\s*[一二三四五六日天])*)""")
    private val RE_LUNAR = Regex("""农历\s*([正冬腊一二三四五六七八九十]{1,2})\s*月\s*([初十廿][一二三四五六七八九十]{0,2}|[一二三四五六七八九十]{1,2})""")
    private val RE_REPEAT_WORD = Regex("""(每天|每日|每周|每星期|每月|每年)""")
    /**
     * 「每周一三五」：重复词 + 星期连写。
     *
     * 必须单独认：先匹配 `每周` 会把后面的「一」变成孤儿（变成「每周」+ 标题「一三五」），
     * 先匹配 `周一` 又会把「每」留下（重复类型丢失）。
     */
    private val RE_WEEKLY_LIST = Regex("""每\s*(?:周|星期)\s*([一二三四五六日天](?:\s*[、和及]?\s*[一二三四五六日天])*)""")
    private val RE_CLOCK = Regex("""(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|晚间)?\s*([0-9]{1,2}|[零〇一二三四五六七八九十两]{1,3})\s*[点時时]\s*(半|一刻|三刻|[0-9]{1,2}\s*分?|[零〇一二三四五六七八九十两]{1,3}\s*分)?""")
    private val RE_COLON_TIME = Regex("""\b([0-9]{1,2}):([0-9]{2})\b""")
    private val RE_DURATION = Regex("""([0-9]+|[零〇一二三四五六七八九十两]{1,3})\s*(?:个)?\s*(小时|钟头|分钟|分)""")

    fun parse(raw: String, now: LocalDate = LocalDate.now(), nowTime: LocalTime = LocalTime.now()): ParsedEventInput {
        var work = raw.trim()
        if (work.isEmpty()) return ParsedEventInput(title = "", matched = false)

        var matched = false
        var date: LocalDate? = null
        var time: LocalTime? = null
        var explicitTime = false
        var repeat = RepeatType.NONE
        var weekdays = emptySet<Int>()
        var weekdaysExplicit = false
        var duration = 0

        fun consume(match: MatchResult?) {
            if (match == null) return
            matched = true
            work = work.removeRange(match.range)
        }

        // 1) 绝对日期（2026年1月1日 / 2026-01-01）
        RE_ABS_DATE.find(work)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val day = m.groupValues[3].toIntOrNull() ?: return@let
            date = CalendarMath.dateClamped(year, month, day)
            consume(m)
        }

        // 2) 只有月日（1月1日）：取今年，已过则明年——「1月1日」说出来通常指下一次
        if (date == null) {
            RE_MONTH_DAY.find(work)?.let { m ->
                val month = m.groupValues[1].toIntOrNull() ?: return@let
                val day = m.groupValues[2].toIntOrNull() ?: return@let
                val thisYear = CalendarMath.dateClamped(now.year, month, day)
                date = if (thisYear.isBefore(now)) CalendarMath.dateClamped(now.year + 1, month, day) else thisYear
                consume(m)
            }
        }

        // 3) 相对日（今天 / 后天）
        if (date == null) {
            RE_RELATIVE.find(work)?.let { m ->
                val offset = RELATIVE_DAYS[m.value] ?: return@let
                date = now.plusDays(offset.toLong())
                consume(m)
            }
        }

        // 4) 相对跨度（三天后 / 2 周后 / 一个月后）
        if (date == null) {
            RE_RELATIVE_SPAN.find(work)?.let { m ->
                val amount = parseNumber(m.groupValues[1]) ?: return@let
                date = when (m.groupValues[2]) {
                    "天", "日" -> now.plusDays(amount.toLong())
                    "周", "星期" -> now.plusWeeks(amount.toLong())
                    "月", "个月" -> now.plusMonths(amount.toLong())
                    "年" -> now.plusYears(amount.toLong())
                    else -> now
                }
                consume(m)
            }
        }

        // 5) 农历（农历八月十五）
        if (date == null) {
            RE_LUNAR.find(work)?.let { m ->
                val month = parseLunarMonth(m.groupValues[1])
                val day = parseLunarDay(m.groupValues[2])
                if (month != null && day != null) {
                    val lunarYear = LunarCalendar.fromSolar(now)?.year ?: now.year
                    // 今年这个农历日已过就取下一年——生日类输入几乎都指下一次
                    val thisYear = LunarCalendar.toSolar(lunarYear, month, day)
                    date = if (thisYear != null && thisYear.isBefore(now)) {
                        LunarCalendar.toSolar(lunarYear + 1, month, day) ?: thisYear
                    } else {
                        thisYear
                    }
                    consume(m)
                }
            }
        }

        // 6) 「每周一三五」：重复 + 星期连写，必须整体认
        RE_WEEKLY_LIST.find(work)?.let { m ->
            val days = m.groupValues[1].mapNotNull { WEEKDAY_CHARS[it] }.distinct()
            if (days.isNotEmpty()) {
                repeat = RepeatType.WEEKLY
                weekdays = days.map { it.value }.toSortedSet()
                weekdaysExplicit = true
                matched = true
                work = work.removeRange(m.range)
                if (date == null) date = nextWeekday(now, days.first(), null)
            }
        }

        // 7) 单个星期（下周一 / 周三 / 这周五）
        RE_WEEKDAY.find(work)?.let { m ->
            val modifier = m.groupValues[1]
            val first = WEEKDAY_CHARS[m.groupValues[2].firstOrNull()]
            if (first != null) {
                val extra = m.groupValues[3].mapNotNull { WEEKDAY_CHARS[it] }
                val all = (listOf(first) + extra).distinct()
                weekdays = all.map { it.value }.toSortedSet()
                weekdaysExplicit = true
                matched = true
                work = work.removeRange(m.range)
                if (date == null) date = nextWeekday(now, first, modifier)
            }
        }

        // 8) 重复词（每天 / 每周 / 每月 / 每年）
        RE_REPEAT_WORD.find(work)?.let { m ->
            repeat = when (m.value) {
                "每天", "每日" -> RepeatType.DAILY
                "每周", "每星期" -> RepeatType.WEEKLY
                "每月" -> RepeatType.MONTHLY
                else -> RepeatType.YEARLY
            }
            matched = true
            work = work.removeRange(m.range)
            if (date == null && repeat == RepeatType.WEEKLY) {
                date = nextWeekday(now, now.dayOfWeek, null)
            }
        }

        // 9) 时刻（下午三点半 / 9:30 / 晚上八点）
        RE_COLON_TIME.find(work)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull()
            val minute = m.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                time = LocalTime.of(hour, minute)
                explicitTime = true
                matched = true
                work = work.removeRange(m.range)
            }
        }
        if (time == null) {
            RE_CLOCK.find(work)?.let { m ->
                val rawHour = parseNumber(m.groupValues[2]) ?: return@let
                val period = m.groupValues[1]
                val hour = applyPeriod(rawHour, period) ?: return@let
                val minute = parseMinutePart(m.groupValues[3])
                time = LocalTime.of(hour, minute)
                explicitTime = true
                matched = true
                work = work.removeRange(m.range)
            }
        }

        // 10) 时长（1 小时 / 半小时 / 45 分钟）
        RE_DURATION.find(work)?.let { m ->
            val amount = parseNumber(m.groupValues[1]) ?: return@let
            duration += when (m.groupValues[2]) {
                "小时", "钟头" -> amount * 60
                else -> amount
            }
            matched = true
            work = work.removeRange(m.range)
        }
        // 「半小时」没有数字，单独认
        if ("半小时" in work) {
            duration += 30
            matched = true
            work = work.replace("半小时", " ")
        }

        // 标题：去掉残留的连接词与多余标点
        val title = work
            .replace(Regex("""^[\s,，。、:：在于是的于]+|[\s,，。、:：]+$"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        return ParsedEventInput(
            title = title,
            date = date,
            time = time,
            // 没说时刻就当成全天——包括「每周一三五」「每天」这类重复输入：
            // 用户没给钟点，替他挑一个「现在几点」反而要在表单里再改一次。
            // （时间地点都没说的重复，走的是「重复词 + 没日期」分支，date 仍为 null）
            isAllDay = !explicitTime,
            repeatType = repeat,
            repeatWeekdays = if (repeat == RepeatType.WEEKLY) weekdays else emptySet(),
            weekdaysExplicit = repeat == RepeatType.WEEKLY && weekdaysExplicit,
            durationMinutes = duration.coerceIn(0, 24 * 60),
            matched = matched,
        )
    }

    /**
     * 最近的某个星期几。
     *
     * 「下周三」以**今天为基准加一周**再取下一个周三，而不是「本周三 + 7 天」——
     * 后者在今天是周五时会落到下下周三。
     */
    private fun nextWeekday(now: LocalDate, target: DayOfWeek, modifier: String?): LocalDate {
        val base = when (modifier) {
            "下" -> now.plusWeeks(1)
            "下下" -> now.plusWeeks(2)
            "上" -> now.minusWeeks(1)
            else -> now
        }
        return base.with(TemporalAdjusters.nextOrSame(target))
    }

    /** 中文数字 / 阿拉伯数字 → Int（支持 1..99 的常见写法：十五、二十、二十三）。 */
    fun parseNumber(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        text.toIntOrNull()?.let { return it }
        if (text.any { it.isDigit() }) return text.filter { it.isDigit() }.toIntOrNull()
        // 纯中文：十 / 十五 / 二十 / 二十三
        if ('十' in text) {
            val parts = text.split('十')
            val tens = if (parts[0].isEmpty()) 1 else CN_DIGITS[parts[0].firstOrNull()] ?: return null
            val ones = if (parts.size > 1) (parts[1].firstOrNull()?.let { CN_DIGITS[it] } ?: 0) else 0
            return tens * 10 + ones
        }
        return CN_DIGITS[text.firstOrNull()]?.takeIf { text.length == 1 }
    }

    /** 时段词修正小时：「下午三点」→ 15，「晚上十二点」→ 0。 */
    private fun applyPeriod(hour: Int, period: String?): Int? {
        if (hour !in 0..24) return null
        val h = if (hour == 24) 0 else hour
        return when (period) {
            "凌晨" -> if (h <= 6) h else null
            "早上", "早晨", "上午" -> if (h in 0..11) h else null
            "中午" -> if (h == 12 || h in 1..2) (if (h < 12) 12 + h else h) else null
            "下午", "傍晚", "晚上", "夜里", "晚间" ->
                if (h in 1..11) h + 12 else h
            else -> h
        }
    }

    private fun parseMinutePart(raw: String): Int {
        val text = raw.trim()
        if (text.isEmpty()) return 0
        return when {
            text == "半" -> 30
            text == "一刻" -> 15
            text == "三刻" -> 45
            else -> parseNumber(text.removeSuffix("分"))?.coerceIn(0, 59) ?: 0
        }
    }

    private fun parseLunarMonth(raw: String): Int? = when (raw) {
        "正" -> 1
        "冬" -> 11
        "腊" -> 12
        else -> parseNumber(raw)?.takeIf { it in 1..12 }
    }

    private fun parseLunarDay(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text == "初一") return 1
        // 「初十」要单独判：`drop(1)` 拿到的是「十」，而 CN_DIGITS 里没有十字
        if (text == "初十") return 10
        if (text.startsWith("初")) return CN_DIGITS[text.drop(1).firstOrNull()]?.takeIf { it in 1..10 }
        if (text.startsWith("廿")) {
            val rest = text.drop(1).firstOrNull()?.let { CN_DIGITS[it] } ?: return 20
            return 20 + rest
        }
        if (text.startsWith("十")) {
            val rest = text.drop(1).firstOrNull()?.let { CN_DIGITS[it] } ?: return 10
            return 10 + rest
        }
        return parseNumber(text)?.takeIf { it in 1..30 }
    }
}
