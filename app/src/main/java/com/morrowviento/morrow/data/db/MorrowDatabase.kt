package com.morrowviento.morrow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MorrowDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "morrow.db"

        fun build(context: Context): MorrowDatabase =
            Room.databaseBuilder(context, MorrowDatabase::class.java, NAME).build()
    }
}
