package com.gaiascanner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, PageEntity::class, JobEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
    abstract fun pages(): PageDao
    abstract fun jobs(): JobDao

    companion object {
        /** Version 2 adds the per-page perspective crop. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN crop TEXT")
            }
        }

        /** Version 3 adds the per-page Enhance settings (filter, brightness, contrast, eraser). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN filter TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN brightness INTEGER")
                db.execSQL("ALTER TABLE pages ADD COLUMN contrast INTEGER")
                db.execSQL("ALTER TABLE pages ADD COLUMN erase TEXT")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gaia.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
