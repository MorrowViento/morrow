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

    /** 批量删除（撤销条的场景需要一次性删掉一批已完成事件）。 */
    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("DELETE FROM events")
    suspend fun clear()

    // ---- 分类统计与级联（M2b2）----

    /**
     * 按分类计数，并按「该分类下最早创建时刻」排序。
     *
     * 排序是为了让「旧事件里出现过但未定义的分类」在列表末尾保持首次出现的先后顺序，
     * GROUP BY 本身不保证顺序。
     */
    @Query(
        "SELECT category AS category, COUNT(*) AS event_count FROM events " +
            "GROUP BY category ORDER BY MIN(created_at) ASC",
    )
    fun observeCategoryUsage(): Flow<List<CategoryUsage>>

    @Query("SELECT COUNT(*) FROM events WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    /** 分类重命名 / 删除迁移：把一个分类下的所有事件改挂到另一个分类。 */
    @Query("UPDATE events SET category = :to WHERE category = :from")
    suspend fun renameCategory(from: String, to: String): Int

    /** 迁移专用：整表替换，保证一次性原子提交。 */
    @Transaction
    suspend fun replaceAll(entities: List<EventEntity>) {
        clear()
        upsertAll(entities)
    }
}
