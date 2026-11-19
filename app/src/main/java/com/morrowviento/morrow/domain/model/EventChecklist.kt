package com.morrowviento.morrow.domain.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 子任务（checklist）单项。
 *
 * 与 [EventTags] 的区别：标签是**检索维度**（一条事件可挂多个、无序、不许含逗号），
 * 子任务是**执行清单**（有序、可勾选、标题里什么字符都可能出现）。
 * 因此子任务不能照抄标签的逗号串编码，必须走 JSON——见 [EventChecklist.encode]。
 */
data class ChecklistItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
)

/**
 * 子任务清单的编解码与归一化。
 *
 * 存法对齐 [EventReminder]：事件表一列 JSON（`events.checklist`），不建子表。
 * 理由与提醒一致——数量上限 20 条，独立建表的收益不足以抵消 `replaceAll()` 的孤儿风险。
 */
object EventChecklist {
    const val MAX_COUNT = 20
    const val MAX_TITLE = 60

    private val gson = Gson()
    private val listType = object : TypeToken<List<ItemJson>>() {}.type

    private data class ItemJson(
        val id: String? = null,
        val title: String? = null,
        val done: Boolean? = null,
    )

    /** 标题清理：压平换行、去首尾空白、截断到 [MAX_TITLE]。空串表示这一项不算数。 */
    fun clean(raw: String): String =
        raw.replace('\n', ' ').replace('\r', ' ').trim().take(MAX_TITLE)

    /**
     * 归一化：清标题 → 丢空项 → 按标题去重（忽略大小写，保留先出现的）→ 截到 [MAX_COUNT]。
     *
     * 刻意**不重排**：子任务是清单，顺序由用户决定，勾选不该让条目跳位置。
     */
    fun normalize(items: List<ChecklistItem>): List<ChecklistItem> {
        val seen = HashSet<String>()
        val result = ArrayList<ChecklistItem>(items.size)
        for (item in items) {
            val title = clean(item.title)
            if (title.isEmpty()) continue
            if (!seen.add(title.lowercase())) continue
            result += item.copy(title = title, done = item.done)
            if (result.size >= MAX_COUNT) break
        }
        return result
    }

    fun encode(items: List<ChecklistItem>): String =
        gson.toJson(items.map { ItemJson(id = it.id, title = it.title, done = it.done) })

    /**
     * 宽松解码：坏 JSON、缺字段、坏元素都只丢那一项，不让整天事件挂掉。
     *
     * `done` 缺省按 false：旧备份里没有这个字段（或手改库漏了）时，
     * 把子任务当成未完成比当成已完成安全——用户顶多再勾一次。
     */
    fun decode(raw: String): List<ChecklistItem> {
        if (raw.isBlank()) return emptyList()
        val parsed: List<ItemJson>? = runCatching {
            gson.fromJson<List<ItemJson>?>(raw, listType)
        }.getOrNull()
        val values = parsed.orEmpty().mapNotNull { json ->
            val title = clean(json.title.orEmpty())
            if (title.isEmpty()) return@mapNotNull null
            ChecklistItem(
                id = json.id?.takeIf { it.isNotBlank() } ?: "c-${title.hashCode()}",
                title = title,
                done = json.done ?: false,
            )
        }
        return normalize(values)
    }

    /** 完成度（0..1）；空清单返回 0，调用方据此决定要不要画子任务进度。 */
    fun progress(items: List<ChecklistItem>): Float {
        if (items.isEmpty()) return 0f
        return items.count { it.done }.toFloat() / items.size
    }
}
