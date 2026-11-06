package com.gaiascanner.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.core.PageFilter
import com.gaiascanner.app.data.db.AppDatabase
import com.gaiascanner.app.data.db.FolderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds an old database from Room's own current table SQL, minus the columns
 * that version did not have, then opens it with the migrations.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val v3Columns = listOf(", `filter` TEXT", ", `brightness` INTEGER", ", `contrast` INTEGER", ", `erase` TEXT")
    private val v2Columns = listOf(", `crop` TEXT")

    private fun currentSql(name: String): List<String> {
        val ctx = TestFiles.context
        ctx.deleteDatabase(name)
        val fresh = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        val statements = mutableListOf<String>()
        fresh.openHelper.writableDatabase.query(
            "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT LIKE 'android_%' AND name != 'room_master_table' ORDER BY rowid",
        ).use { c -> while (c.moveToNext()) statements += c.getString(0) }
        fresh.close()
        ctx.deleteDatabase(name)
        return statements
    }

    private fun buildOld(name: String, version: Int, dropColumns: List<String>, pageColumns: String, pageValues: String) {
        val ctx = TestFiles.context
        val statements = currentSql(name)
        val pagesSql = statements.single { it.contains("TABLE `pages`") }
        dropColumns.forEach { assertTrue("pages SQL has $it: $pagesSql", pagesSql.contains(it)) }
        val old = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        // Tables and indexes that did not exist at that version (folders arrived in version 4).
        statements.filterNot { it.contains("`folders`") }.forEach { sql -> old.execSQL(dropColumns.fold(sql) { s, col -> s.replace(col, "") }) }
        old.execSQL(
            "INSERT INTO documents (id, name, kind, pageCount, sizeBytes, localPath, locked, createdAt, modifiedAt) " +
                "VALUES (1, 'old', 'IMAGES', 1, 10, '/x', 0, 1, 1)",
        )
        old.execSQL("INSERT INTO pages ($pageColumns) VALUES ($pageValues)")
        old.version = version
        old.close()
    }

    private fun open(name: String) = Room.databaseBuilder(TestFiles.context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .build()

    @Test
    fun migration3To4AddsFoldersAndKeepsDocuments() = runBlocking<Unit> {
        val name = "migration-3-test.db"
        buildOld(
            name, 3, emptyList(),
            "id, documentId, sortIndex, imagePath, width, height, rotation",
            "1, 1, 0, '/x/a.jpg', 400, 300, 0",
        )
        val db = open(name)
        assertEquals("old", db.documents().get(1)!!.name)
        val folderId = db.folders().insert(FolderEntity(name = "Taxes", createdAt = 1, modifiedAt = 1))
        db.documents().moveTo(listOf(1), folderId, 2)
        assertEquals(folderId, db.documents().get(1)!!.folderId)
        db.close()
        TestFiles.context.deleteDatabase(name)
    }

    @Test
    fun migration1To3KeepsPages() = runBlocking<Unit> {
        val name = "migration-1-test.db"
        buildOld(
            name, 1, v2Columns + v3Columns,
            "id, documentId, sortIndex, imagePath, width, height, rotation",
            "1, 1, 0, '/x/a.jpg', 400, 300, 90",
        )
        val db = open(name)
        val page = db.pages().getForDocument(1).single()
        assertEquals(90, page.rotation)
        assertNull(page.crop)
        assertNull(page.filter)
        db.close()
        TestFiles.context.deleteDatabase(name)
    }

    @Test
    fun migration2To3KeepsCropAndAddsEnhance() = runBlocking<Unit> {
        val name = "migration-2-test.db"
        buildOld(
            name, 2, v3Columns,
            "id, documentId, sortIndex, imagePath, width, height, rotation, crop",
            "1, 1, 0, '/x/a.jpg', 400, 300, 0, '0.1,0.1,0.9,0.1,0.9,0.9,0.1,0.9'",
        )
        val db = open(name)
        val page = db.pages().getForDocument(1).single()
        assertEquals("0.1,0.1,0.9,0.1,0.9,0.9,0.1,0.9", page.crop)
        assertNull(page.brightness)
        db.pages().update(page.copy(filter = PageFilter.DOCS, brightness = 20))
        assertEquals(PageFilter.DOCS, db.pages().get(1)!!.filter)
        db.close()
        TestFiles.context.deleteDatabase(name)
    }
}
