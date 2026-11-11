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
}
