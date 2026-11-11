package com.jiuxina.ying.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class YingDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "ying.db"

        fun build(context: Context): YingDatabase =
            Room.databaseBuilder(context, YingDatabase::class.java, NAME).build()
    }
}
