package com.jiuxina.ying.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY is_pinned DESC, target_at ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY is_pinned DESC, target_at ASC")
    suspend fun loadAll(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun findById(id: String): EventEntity?

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EventEntity>)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM events")
    suspend fun clear()

    /** 迁移专用：整表替换，保证一次性原子提交。 */
    @Transaction
    suspend fun replaceAll(entities: List<EventEntity>) {
        clear()
        upsertAll(entities)
    }
}
