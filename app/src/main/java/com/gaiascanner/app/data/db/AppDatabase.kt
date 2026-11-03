package com.gaiascanner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, PageEntity::class, JobEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
    abstract fun pages(): PageDao
    abstract fun jobs(): JobDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gaia.db").build()
    }
}
