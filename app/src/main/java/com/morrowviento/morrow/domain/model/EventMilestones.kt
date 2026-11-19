package com.morrowviento.morrow.domain.model

/**
 * 纪念日的「第 N 天」里程碑提醒。
 *
 * 用户的原话是「距离第一天过去 100 天提醒、300 天提醒」——它和 [CountdownEvent.reminders]
 * 的「提前几分钟」是两个维度：
 *
 * - **提醒**回答「这一次还有多久开始」，锚点随重复规则一起往后走；
 * - **里程碑**回答「从第一天算起走到第几天了」，锚点是**绝对**的，第 100 天一辈子只有一次。
 *
 * 因此里程碑不参与重复推导，也不该被 `RepeatRule.upcomingTarget` 推动。
 *
 * 存成逗号串而不是 JSON：内容是纯整数，没有 [EventTags] 那种「清洗时把分隔符吃掉」的风险，
 * 而逗号串在数据库里肉眼可读、迁移也简单。
 */
object EventMilestones {

    /** 单条纪念日最多几个里程碑。够用（100 / 300 / 365 / 1000 …），又不至于把提醒排满。 */
    const val MAX_COUNT = 6

    /** 上限 100 年。再远的日子提醒到了也没意义，还会把 `plusDays` 推出可用范围。 */
    const val MAX_DAY = 36_500

    /** 表单里给的常用档位。用户举例的 100 与 300 在最前面。 */
    val PRESETS = listOf(100, 300, 365, 1_000)

    /** 丢非正数、超上限、重复值，升序，截断。**不重排用户已选的先后**——升序本身就是排序。 */
    fun normalize(days: Iterable<Int>): List<Int> = days
        .filter { it in 1..MAX_DAY }
        .distinct()
        .sorted()
        .take(MAX_COUNT)

    fun encode(days: List<Int>): String = normalize(days).joinToString(",")

    /**
     * 宽松解码：脏数据只丢这一项，不让整条事件读不出来。
     *
     * 与 tags 的处理保持一致——宁可少几个里程碑，也不能因为一列坏掉就让用户丢事件。
     */
    fun decode(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        return normalize(
            raw.split(',').mapNotNull { token -> token.trim().toIntOrNull() },
        )
    }

    /** 展示文案，例如「第 100 天」。 */
    fun label(day: Int): String = "第 $day 天"
}
