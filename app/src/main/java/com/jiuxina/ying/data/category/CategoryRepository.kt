package com.jiuxina.ying.data.category

import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.domain.model.EventCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 分类列表项。
 *
 * [isOrphan] 表示该分类只存在于旧事件里、当前列表并未定义它——UI 应当照常展示，
 * 用户一旦编辑分类列表就会被正式收编，避免旧数据「凭空消失」。
 */
data class CategoryItem(
    val name: String,
    val usage: Int,
    val isOrphan: Boolean,
)

/** 删除分类的结果。 */
sealed interface CategoryDelete {
    /** 删除成功；[migratedEvents] 为被改挂到目标分类的事件数（无占用时为 0）。 */
    data class Ok(val migratedEvents: Int) : CategoryDelete

    /** 分类被事件占用，必须先指定迁移目标。 */
    data class NeedsTarget(val eventCount: Int, val candidates: List<String>) : CategoryDelete

    data object LastOne : CategoryDelete
    data object NotFound : CategoryDelete
    data object InvalidTarget : CategoryDelete
}

/**
 * 分类仓库（M2b2）。
 *
 * 存储：DataStore 单键 JSON（[KEY_CATEGORIES]），未写入过时回落到 [EventCategory.PRESETS]。
 * 事件侧仍以分类**名字**存储，重命名 / 删除迁移通过 [EventRepository.renameCategory] 级联完成。
 *
 * 一致性说明：DataStore 与 Room 无法纳入同一事务，写入顺序固定为「先改事件、再改列表」。
 * 若第二步失败，结果是事件已改名而列表仍是旧名——此时旧名会以 orphan 形式重新出现在列表里，
 * 数据不丢且可再次操作；反过来先写列表则会出现「列表有、事件挂着已消失的旧名」的隐性不一致。
 */
class CategoryRepository(
    private val settings: SettingsRepository,
    private val events: EventRepository,
) {

    /** 已保存的分类列表（忽略旧事件里的孤儿分类）。 */
    private val stored: Flow<List<String>> = settings.rawString(KEY_CATEGORIES).map { raw ->
        decodeOrPresets(raw)
    }

    /** 管理页 / 表单实际使用的完整列表：已保存分类 + 孤儿分类，带使用次数。 */
    fun observeItems(): Flow<List<CategoryItem>> =
        combine(stored, events.observeCategoryUsage()) { list, usage ->
            val merged = CategoryRules.merge(list, usage.keys.toList())
            merged.map { name ->
                CategoryItem(name = name, usage = usage[name] ?: 0, isOrphan = !list.contains(name))
            }
        }

    suspend fun add(name: String): CategoryEdit {
        val current = currentMerged()
        val result = CategoryRules.add(current, name)
        if (result is CategoryEdit.Ok) persist(result.list)
        return result
    }

    /** 重命名；成功时级联把该分类下的事件一并改名（历史事件显示新名）。 */
    suspend fun rename(from: String, to: String): CategoryEdit {
        val current = currentMerged()
        val result = CategoryRules.rename(current, from, to)
        if (result is CategoryEdit.Ok) {
            val newName = to.trim()
            if (newName != from) events.renameCategory(from, newName)
            persist(result.list)
        }
        return result
    }

    /**
     * 删除分类。
     *
     * @param target 分类被事件占用时的迁移目标；为 null 且存在占用则返回 [CategoryDelete.NeedsTarget]，
     *   由 UI 提示用户选择，绝不静默删除事件或让事件挂到不存在的分类上。
     */
    suspend fun delete(name: String, target: String?): CategoryDelete {
        val current = currentMerged()
        if (!current.contains(name)) return CategoryDelete.NotFound

        val used = events.countByCategory(name)
        if (used > 0) {
            val candidates = current.filterNot { it == name }
            if (target == null) return CategoryDelete.NeedsTarget(used, candidates)
            if (target == name || !current.contains(target)) return CategoryDelete.InvalidTarget
            events.renameCategory(name, target)
        }

        return when (val next = CategoryRules.remove(current, name)) {
            is CategoryEdit.Ok -> {
                persist(next.list)
                CategoryDelete.Ok(used)
            }
            CategoryEdit.LastOne -> CategoryDelete.LastOne
            else -> CategoryDelete.NotFound
        }
    }

    // ---- 内部 ----

    /** 当前「已保存 + 孤儿」的合并列表；所有编辑都基于它，避免与孤儿分类重名。 */
    private suspend fun currentMerged(): List<String> {
        val used = events.observeCategoryUsage().first().keys.toList()
        return CategoryRules.merge(stored.first(), used)
    }

    private suspend fun persist(list: List<String>) {
        settings.putString(KEY_CATEGORIES, CategoryCodec.encode(list))
    }

    /** 解码失败或内容为空（例如被清空）时回落到预置分类，保证表单永远有可选项。 */
    private fun decodeOrPresets(raw: String?): List<String> {
        val decoded = CategoryCodec.decode(raw) ?: return EventCategory.PRESETS
        val normalized = CategoryRules.normalize(decoded)
        return normalized.ifEmpty { EventCategory.PRESETS }
    }

    companion object {
        /** 新键（旧版无分类概念，故此键不存在迁移问题）。 */
        const val KEY_CATEGORIES = "category_list_v1"
    }
}
