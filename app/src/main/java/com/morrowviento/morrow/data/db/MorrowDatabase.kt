package com.morrowviento.morrow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EventEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class MorrowDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "morrow.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN kind TEXT NOT NULL DEFAULT 'TODO'")
                db.execSQL("ALTER TABLE events ADD COLUMN last_handled_occurrence INTEGER")
            }
        }

        /**
         * 4.2.0：完整重复规则 + 农历循环 + 持续事件。
         *
         * 全部给默认值，旧行升级后语义不变——仍然「不重复、单日」。
         * 间隔与星期、农历标记、结束日期都是新增列，没有一张表需要重建。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN repeat_interval INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE events ADD COLUMN repeat_weekdays TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN repeat_on_lunar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN repeat_until INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN end_date INTEGER")
            }
        }

        /**
         * 4.2.0 第二批：多维标记、归档、时长。
         *
         * 同样全部带默认值：旧行升级后「没有标签、未归档、未指定时长」，
         * 时间块按默认一小时画，行为与升级前一致。归档列刻意可空——
         * 用 0 表示「未归档」会让「1970 年归档」与「未归档」分不开。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN archived_at INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 4.2.x 补丁：持续事件的结束日增加具体时刻（结束时间）。
         *
         * 只加一列 `end_time`（分钟数），旧行的结束时间沿用开始时刻（null 即可），
         * 不改 `end_date` 的 epochDay 语义，不重建表。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN end_time INTEGER")
            }
        }

        fun build(context: Context): MorrowDatabase =
            Room.databaseBuilder(context, MorrowDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
