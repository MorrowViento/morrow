package com.jiuxina.ying.data

import com.jiuxina.ying.data.db.EventDao
import com.jiuxina.ying.data.db.EventMapper
import com.jiuxina.ying.domain.model.CountdownEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 事件仓库：对上层只暴露领域模型与 Flow，Room 细节不外泄。
 */
class EventRepository(private val dao: EventDao) {

    fun observeAll(): Flow<List<CountdownEvent>> =
        dao.observeAll().map { rows -> rows.map { EventMapper.toDomain(it) } }

    suspend fun findById(id: String): CountdownEvent? =
        dao.findById(id)?.let { EventMapper.toDomain(it) }

    suspend fun save(event: CountdownEvent) {
        dao.upsert(EventMapper.toEntity(event))
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()

    // ---- 分类（M2b2）----

    /**
     * 分类使用统计。返回值用 `associate` 构造，迭代顺序即 DAO 的
     * 「首次创建时刻升序」，供分类列表拼接旧分类时保持稳定顺序。
     */
    fun observeCategoryUsage(): Flow<Map<String, Int>> =
        dao.observeCategoryUsage().map { rows -> rows.associate { it.category to it.eventCount } }

    suspend fun countByCategory(category: String): Int = dao.countByCategory(category)

    /** 分类改名 / 删除迁移：把旧分类名下的事件统一改挂到新名字。 */
    suspend fun renameCategory(from: String, to: String): Int = dao.renameCategory(from, to)
}
