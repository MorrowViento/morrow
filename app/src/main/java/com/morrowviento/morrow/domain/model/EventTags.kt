package com.morrowviento.morrow.domain.model

/**
 * 事件的「多维标记」——一条事件可以同时挂多个标签，与单值的分类是两个维度：
 * 分类回答「这件事属于生活的哪一块」，标签回答「它在哪些场景里要出现」
 * （例如「出差」既是工作分类，又是「报销」「家人知会」两个标签）。
 *
 * 归一化在这里做一次，写库 / 备份 / 撤销三条路径都复用它，
 * 否则同一个标签会以「#报销」「报销 」「报销」三种形态各存一份。
 */
object EventTags {

    /** 单条事件最多挂几个标签。再多就选不出来，也挤不进卡片一行。 */
    const val MAX_COUNT = 5

    /** 单个标签最长几个字。 */
    const val MAX_LENGTH = 8

    /**
     * 归一化：去首尾空白与 `#` 前缀、把逗号换成空格（逗号是存库分隔符）、
     * 截断到 [MAX_LENGTH]、去重（忽略大小写）、最多保留 [MAX_COUNT] 个。
     *
     * 保持输入顺序：用户刚敲的那个标签应该出现在最后，而不是被排序挪到中间。
     */
    fun normalize(raw: Iterable<String>): List<String> {
        val out = LinkedHashMap<String, String>(MAX_COUNT)
        for (item in raw) {
            val cleaned = clean(item)
            if (cleaned.isEmpty()) continue
            val key = cleaned.lowercase()
            if (out.containsKey(key)) continue
            out[key] = cleaned
            if (out.size >= MAX_COUNT) break
        }
        return out.values.toList()
    }

    /** 单个标签清洗；返回空串表示这个标签无效（不该被保存）。 */
    fun clean(raw: String): String = raw
        .trim()
        .removePrefix("#")
        .trim()
        .replace(',', ' ')
        .replace('，', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_LENGTH)

    /** 存库格式：逗号分隔。标签内部已不可能含逗号（见 [clean]）。 */
    fun encode(tags: List<String>): String = tags.joinToString(",")

    /** 读库格式：逗号分隔，坏数据直接丢弃并由 [normalize] 收口。 */
    fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return normalize(raw.split(','))
    }

    /** 卡片/详情里的一行展示：「#报销 #出差」。 */
    fun display(tags: List<String>): String = if (tags.isEmpty()) "" else tags.joinToString(" ") { "#$it" }
}
