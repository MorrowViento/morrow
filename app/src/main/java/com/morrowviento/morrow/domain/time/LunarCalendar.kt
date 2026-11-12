package com.morrowviento.morrow.domain.time

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 农历日期。
 *
 * [isLeapMonth] 为 true 时表示闰月，同一个 [month] 在该年会出现两次
 * （例：2023 年有二月与闰二月），比对时不能只看 month。
 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean = false,
) {
    /** 形如「正月」「闰四月」「腊月」。 */
    val monthLabel: String get() = (if (isLeapMonth) "闰" else "") + LunarCalendar.monthName(month)

    /** 形如「初一」「廿三」「三十」。 */
    val dayLabel: String get() = LunarCalendar.dayName(day)

    /** 日历格子副标题：初一显示月名（换月更醒目），其余显示日名。 */
    val cellLabel: String get() = if (day == 1) monthLabel else dayLabel
}

/**
 * 农历换算（1900–2100）。
 *
 * 采用通行的压缩年表：每年一个 17 位整数，低 4 位为闰月月份（0 表示无闰月），
 * 第 5–16 位依次表示正月至十二月是否 30 天，第 17 位表示闰月是否 30 天。
 *
 * 选择自实现而非引入依赖：`android.icu.util.ChineseCalendar` 无法在纯 JVM 单测里跑，
 * 第三方库则会为这点功能带上整套干支/八字表。年表数据本身仅 201 个 int（约 1.6 KB）。
 *
 * 已用 2019–2031 年春节公历日期与 6 个闰月年份交叉校验。
 */
object LunarCalendar {

    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100

    /** 农历 1900 年正月初一。 */
    private val BASE: LocalDate = LocalDate.of(1900, 1, 31)

    /**
     * 超出 [MIN_YEAR]..[MAX_YEAR] 范围返回 null，调用方按「无农历」处理。
     *
     * 年份用**累计天数表 + 二分**定位，不再从 1900 年逐年累加：月视图一次要算 42 格
     * （且每格还要判节日），逐年累加是 126 次 × 12 个位运算，一屏就是十几万次运算，
     * 全都落在打开日历的那一帧上。二分之后每格只需 ~8 次比较 + 至多 13 次月循环。
     */
    fun fromSolar(date: LocalDate): LunarDate? {
        val offset = ChronoUnit.DAYS.between(BASE, date)
        if (offset < 0L) return null
        val starts = YEAR_STARTS
        // starts 的末位是「MAX_YEAR 结束后的累计天数」，超出即年表之外
        if (offset >= starts[starts.size - 1]) return null

        // 找最后一个 starts[i] <= offset：i 就是农历年相对 MIN_YEAR 的下标
        var lo = 0
        var hi = starts.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        val year = MIN_YEAR + lo
        var rest = offset.toInt() - starts[lo]

        val leap = leapMonth(year)
        var month = 1
        var isLeap = false
        while (month <= 12) {
            val days = if (isLeap) leapDays(year) else monthDays(year, month)
            if (rest < days) break
            rest -= days
            if (isLeap) {
                isLeap = false
                month++
            } else if (leap == month) {
                // 闰月紧跟在同序号月之后，下一个月序号不变
                isLeap = true
            } else {
                month++
            }
        }
        return LunarDate(year = year, month = month, day = rest + 1, isLeapMonth = isLeap)
    }

    /** 日历格子副标题：节日优先，其次初一显示月名，其余显示日名。 */
    fun cellLabel(date: LocalDate): String? {
        val lunar = fromSolar(date) ?: return null
        return festival(lunar) ?: lunar.cellLabel
    }

    /** 农历节日名；无节日返回 null。闰月不重复过节。 */
    fun festival(date: LocalDate): String? = fromSolar(date)?.let { festival(it) }

    /**
     * 格子需要的两份信息（副标题 + 是否节日）一次算完。
     *
     * 日历月视图每格原本要调 `cellLabel` 与 `festival` 各一次，等于把同一格换算两遍；
     * 这里共用同一份 [LunarDate]。`isFestival` 与「副标题是节日名」互不冲突——
     * 有节日时副标题本身就是节日名，粗体标记仍在。
     */
    fun cell(date: LocalDate): Pair<String?, Boolean> {
        val lunar = fromSolar(date) ?: return null to false
        val festivalName = festival(lunar)
        return (festivalName ?: lunar.cellLabel) to (festivalName != null)
    }

    fun festival(lunar: LunarDate): String? {
        if (lunar.isLeapMonth) return null
        return when (lunar.month to lunar.day) {
            1 to 1 -> "春节"
            1 to 15 -> "元宵"
            5 to 5 -> "端午"
            7 to 7 -> "七夕"
            7 to 15 -> "中元"
            8 to 15 -> "中秋"
            9 to 9 -> "重阳"
            12 to 8 -> "腊八"
            // 腊月最后一天；月大有三十，月小只有廿九
            12 to monthDays(lunar.year, 12) -> "除夕"
            else -> null
        }
    }

    fun monthName(month: Int): String = MONTH_NAMES.getOrElse(month - 1) { month.toString() } + "月"

    fun dayName(day: Int): String = when (day) {
        10 -> "初十"
        20 -> "二十"
        30 -> "三十"
        in 1..29 -> TENS[(day - 1) / 10] + DIGITS[(day - 1) % 10]
        else -> day.toString()
    }

    /** 该农历年闰几月；0 表示无闰月。 */
    fun leapMonth(year: Int): Int = info(year) and 0xF

    /** 闰月天数；无闰月返回 0。 */
    fun leapDays(year: Int): Int = when {
        leapMonth(year) == 0 -> 0
        info(year) and 0x10000 != 0 -> 30
        else -> 29
    }

    /** 该年 [month] 月（不含闰月）的天数。 */
    fun monthDays(year: Int, month: Int): Int =
        if (info(year) and (0x10000 shr month) != 0) 30 else 29

    /** 该农历年的总天数（354–385）。 */
    fun yearDays(year: Int): Int {
        var sum = 348 // 12 个月各按 29 天起算
        for (bit in 0..11) {
            if (info(year) and (0x8000 shr bit) != 0) sum++
        }
        return sum + leapDays(year)
    }

    private fun info(year: Int): Int {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw IllegalArgumentException("农历年表范围为 $MIN_YEAR..$MAX_YEAR，收到 $year")
        }
        return LUNAR_INFO[year - MIN_YEAR]
    }

    private val MONTH_NAMES = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val DIGITS = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    private val TENS = arrayOf("初", "十", "廿")

    /**
     * 每个农历年正月初一相对 [BASE] 的累计天数（长度 = 年数 + 1，末位是总量）。
     *
     * 只在首次换算时建一次（201 次 [yearDays]），之后每次定位年份都是二分。
     * 建表放在 `by lazy` 里而不是 `init`：小部件、提醒通知这些路径只算一两个日期，
     * 不值得在 `Application.onCreate` 里多付这份初始化。
     */
    private val YEAR_STARTS: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val years = MAX_YEAR - MIN_YEAR + 1
        val starts = IntArray(years + 1)
        var sum = 0
        for (i in 0 until years) {
            starts[i] = sum
            sum += yearDays(MIN_YEAR + i)
        }
        starts[years] = sum
        starts
    }

    /** 1900–2100 年压缩年表，每行 10 年。 */
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520, // 2100
    )
}
