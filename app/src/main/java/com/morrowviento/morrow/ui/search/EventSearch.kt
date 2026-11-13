package com.morrowviento.morrow.ui.search

import androidx.compose.runtime.Immutable
import com.morrowviento.morrow.ui.event.EventCardModel

/**
 * 分类筛选的三种取值。
 *
 * 用密封接口而不是 `String?`：`null` 既可能表示「不筛」也可能表示「未分类」，
 * 传错一次就会出现「选了‘未分类’却看到全部」这种难查的 bug。
 */
sealed interface CategoryFilter {
    /** 全部，不按分类筛。 */
    data object All : CategoryFilter

    /** 只保留没有分类的事件。 */
    data object Uncategorized : CategoryFilter

    /** 只保留该分类的事件。 */
    data class Named(val name: String) : CategoryFilter
}

/** 一次筛选的结果：进行中与已完成分开，页面按两段渲染。 */
@Immutable
data class SearchResult(
    val active: List<EventCardModel> = emptyList(),
    val completed: List<EventCardModel> = emptyList(),
) {
    val count: Int get() = active.size + completed.size
    val isEmpty: Boolean get() = count == 0
}

/**
 * 搜索与分类筛选（纯函数，无 Android 依赖，可纯 JVM 单测）。
 *
 * 匹配规则刻意保持**字面包含**：
 * - 标题 / 备注 / 分类名三处任一命中即可，忽略大小写（对中文无影响，对英文与数字有用）；
 * - 查询串先 `trim`，空串视为「不过滤」，因此清空输入框就回到全部；
 * - 不做分词、不做模糊匹配：倒数日的标题通常很短，分词只会带来「搜‘生日’却搜不到
 *   ‘生日聚会’」这类反直觉结果。
 */
object EventSearch {

    fun matchesQuery(card: EventCardModel, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return card.title.contains(needle, ignoreCase = true) ||
            card.note.contains(needle, ignoreCase = true) ||
            card.category.contains(needle, ignoreCase = true)
    }

    fun matchesFilter(card: EventCardModel, filter: CategoryFilter): Boolean = when (filter) {
        CategoryFilter.All -> true
        CategoryFilter.Uncategorized -> card.category.isBlank()
        is CategoryFilter.Named -> card.category == filter.name
    }

    fun apply(
        active: List<EventCardModel>,
        completed: List<EventCardModel>,
        query: String,
        filter: CategoryFilter,
    ): SearchResult = SearchResult(
        active = active.filter { matchesQuery(it, query) && matchesFilter(it, filter) },
        completed = completed.filter { matchesQuery(it, query) && matchesFilter(it, filter) },
    )

    /**
     * 分类计数：只返回**确实有事件**的分类，按 [order]（分类设置的顺序）排，
     * 不在 [order] 里的（旧数据里的孤儿分类）按名字排在后面。
     *
     * 只列有内容的分类：筛选条是给「缩小范围」用的，摆一排点下去必然空结果的
     * 分类只会让人以为坏了。
     */
    fun categoryCounts(cards: List<EventCardModel>, order: List<String>): List<Pair<String, Int>> {
        if (cards.isEmpty()) return emptyList()
        val counts = LinkedHashMap<String, Int>(order.size)
        for (name in order) counts[name] = 0
        var unknown: MutableList<String>? = null
        for (card in cards) {
            val name = card.category
            if (name.isBlank()) continue
            if (counts.containsKey(name)) {
                counts[name] = counts.getValue(name) + 1
            } else {
                val list = unknown ?: ArrayList<String>().also { unknown = it }
                if (!list.contains(name)) list.add(name)
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        unknown?.sort()
        return counts.entries
            .filter { it.value > 0 }
            .map { it.key to it.value }
    }

    /** 没有分类的事件数；为 0 时页面不显示「未分类」这一格。 */
    fun uncategorizedCount(cards: List<EventCardModel>): Int = cards.count { it.category.isBlank() }
}
