package com.morrowviento.morrow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EventEntity::class],
    version = 2,
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

        fun build(context: Context): MorrowDatabase =
            Room.databaseBuilder(context, MorrowDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
