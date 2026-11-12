package com.morrowviento.morrow.data.category

import com.morrowviento.morrow.domain.model.EventCategory

/** 分类编辑结果（纯数据，便于单测与 UI 映射提示语）。 */
sealed interface CategoryEdit {
    data class Ok(val list: List<String>) : CategoryEdit
    data object Blank : CategoryEdit
    data object TooLong : CategoryEdit
    data object Duplicate : CategoryEdit
    data object NotFound : CategoryEdit
    data object LastOne : CategoryEdit
}

/**
 * 分类列表的纯函数规则层（不依赖 Context / DataStore / Room，可直接单测）。
 *
 * 列表语义：顺序即展示顺序；不允许空名、不允许重名、至少保留一项。
 */
object CategoryRules {

    /**
     * 归一化：去首尾空白、丢空名、按首次出现去重。
     *
     * 刻意**不**按长度过滤——旧数据里可能存在超长分类名，过滤会让用户在列表里看不到自己的数据；
     * 长度限制只作用于用户新输入的名称。
     */
    fun normalize(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * 合并「已保存分类」与「旧事件里出现过但未定义的分类」。
     *
     * 后者追加在末尾，保证旧数据里的分类在表单里仍可选、在管理页仍可见（不可丢）。
     */
    fun merge(stored: List<String>, usedInEvents: List<String>): List<String> {
        val base = normalize(stored).ifEmpty { normalize(EventCategory.PRESETS) }
        val extra = normalize(usedInEvents).filterNot { base.contains(it) }
        return base + extra
    }

    /** 名称合法性校验；返回 null 表示合法。 */
    fun validate(name: String): CategoryEdit? = when {
        name.isBlank() -> CategoryEdit.Blank
        name.trim().length > EventCategory.MAX_NAME_LENGTH -> CategoryEdit.TooLong
        else -> null
    }

    fun add(current: List<String>, name: String): CategoryEdit {
        validate(name)?.let { return it }
        val trimmed = name.trim()
        if (current.contains(trimmed)) return CategoryEdit.Duplicate
        return CategoryEdit.Ok(current + trimmed)
    }

    /** 重命名：保持原位置；新旧同名视为无变化。 */
    fun rename(current: List<String>, from: String, to: String): CategoryEdit {
        val index = current.indexOf(from)
        if (index < 0) return CategoryEdit.NotFound
        validate(to)?.let { return it }
        val trimmed = to.trim()
        if (trimmed == from) return CategoryEdit.Ok(current)
        if (current.contains(trimmed)) return CategoryEdit.Duplicate
        return CategoryEdit.Ok(current.toMutableList().also { it[index] = trimmed })
    }

    /** 删除：不允许删到空列表，否则表单将无从选择。 */
    fun remove(current: List<String>, name: String): CategoryEdit {
        if (!current.contains(name)) return CategoryEdit.NotFound
        if (current.size <= 1) return CategoryEdit.LastOne
        return CategoryEdit.Ok(current.filterNot { it == name })
    }
}
