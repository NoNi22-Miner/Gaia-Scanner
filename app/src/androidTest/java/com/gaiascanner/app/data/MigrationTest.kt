package com.gaiascanner.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Version 1 had no pages.crop column. This builds a version 1 database from
 * Room's own table SQL (minus that column), then opens it with the migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migration1To2KeepsPagesAndAddsEmptyCrop() = runBlocking<Unit> {
        val ctx = TestFiles.context
        val name = "migration-test.db"
        ctx.deleteDatabase(name)

        // 1. Let Room create the current schema and read back its SQL.
        val fresh = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        val statements = mutableListOf<String>()
        fresh.openHelper.writableDatabase.query(
            "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT LIKE 'android_%' AND name != 'room_master_table' ORDER BY rowid",
        ).use { c -> while (c.moveToNext()) statements += c.getString(0) }
        fresh.close()
        ctx.deleteDatabase(name)

        // 2. Build the version 1 database.
        val pagesSql = statements.single { it.contains("TABLE `pages`") }
        assertTrue("pages SQL has the crop column: $pagesSql", pagesSql.contains(", `crop` TEXT"))
        val v1 = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        statements.forEach { v1.execSQL(it.replace(", `crop` TEXT", "")) }
        v1.execSQL(
            "INSERT INTO documents (id, name, kind, pageCount, sizeBytes, localPath, locked, createdAt, modifiedAt) " +
                "VALUES (1, 'old', 'IMAGES', 1, 10, '/x', 0, 1, 1)",
        )
        v1.execSQL(
            "INSERT INTO pages (id, documentId, sortIndex, imagePath, width, height, rotation) " +
                "VALUES (1, 1, 0, '/x/a.jpg', 400, 300, 90)",
        )
        v1.version = 1
        v1.close()

        // 3. Open with Room and the migration. Room checks the schema after migrating.
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        val pages = db.pages().getForDocument(1)
        assertEquals(1, pages.size)
        assertEquals(90, pages[0].rotation)
        assertNull(pages[0].crop)
        db.close()
        ctx.deleteDatabase(name)
    }
}
