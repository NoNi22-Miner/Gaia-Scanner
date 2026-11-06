package com.gaiascanner.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Folders, trash, restore and the 30-day purge, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class LibraryFoldersTest {

    private lateinit var db: AppDatabase
    private lateinit var library: LibraryRepository
    private val files = FileStore(TestFiles.context)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(TestFiles.context, AppDatabase::class.java).build()
        library = LibraryRepository(db, files)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newPdf(name: String, folderId: Long?): Long {
        val dir = files.newDocumentDir()
        val pdf = File(dir, "$name.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(1)) }
        return library.addPdfFile(pdf, name, folderId)
    }

    @Test
    fun trashFolderTakesSubtreeAndRestoreBringsBackOnlyThat() = runBlocking<Unit> {
        val a = library.createFolder("A", null)
        val b = library.createFolder("B", a)
        val inA = newPdf("inA", a)
        val inB = newPdf("inB", b)
        val trashedEarlier = newPdf("early", b)
        library.trashDocuments(listOf(trashedEarlier))
        Thread.sleep(5) // a different trash time than the folder's

        library.trashFolder(a)
        assertNotNull(db.documents().get(inA)!!.trashedAt)
        assertNotNull(db.documents().get(inB)!!.trashedAt)
        assertNotNull(db.folders().get(b)!!.trashedAt)

        library.restoreFolder(a)
        assertNull(db.folders().get(a)!!.trashedAt)
        assertNull(db.folders().get(b)!!.trashedAt)
        assertNull(db.documents().get(inA)!!.trashedAt)
        assertNull(db.documents().get(inB)!!.trashedAt)
        assertNotNull("trashed before the folder, stays in trash", db.documents().get(trashedEarlier)!!.trashedAt)
    }

    @Test
    fun restoredDocumentGoesToRootWhenItsFolderIsTrashed() = runBlocking<Unit> {
        val a = library.createFolder("A", null)
        val doc = newPdf("doc", a)
        library.trashFolder(a)
        library.restoreDocument(doc)
        val restored = db.documents().get(doc)!!
        assertNull(restored.trashedAt)
        assertNull(restored.folderId)
    }

    @Test
    fun folderCannotMoveIntoItsOwnSubtree() = runBlocking<Unit> {
        val a = library.createFolder("A", null)
        val b = library.createFolder("B", a)
        assertFalse(library.moveFolder(a, b))
        assertFalse(library.moveFolder(a, a))
        assertTrue(library.moveFolder(b, null))
        assertNull(db.folders().get(b)!!.parentId)
    }

    @Test
    fun purgeDeletesOnlyOldTrashAndItsFiles() = runBlocking<Unit> {
        val old = newPdf("old", null)
        val recent = newPdf("recent", null)
        val kept = newPdf("kept", null)
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        db.documents().trash(listOf(old), now - 31 * day)
        db.documents().trash(listOf(recent), now - 29 * day)
        val oldFile = File(db.documents().get(old)!!.localPath)
        assertTrue(oldFile.exists())

        assertEquals(1, library.purgeOldTrash(now))
        assertNull(db.documents().get(old))
        assertFalse(oldFile.exists())
        assertNotNull(db.documents().get(recent))
        assertNull(db.documents().get(kept)!!.trashedAt)
        library.deleteDocument(recent)
        library.deleteDocument(kept)
    }

    @Test
    fun newDocumentsGoToTheOpenFolderUnlessItWasTrashed() = runBlocking<Unit> {
        val a = library.createFolder("A", null)
        library.targetFolderId = a
        val first = newPdf("first", library.targetFolderId)
        assertEquals(a, db.documents().get(first)!!.folderId)
        library.trashFolder(a)
        val second = newPdf("second", library.targetFolderId)
        assertNull(db.documents().get(second)!!.folderId)
        library.emptyTrash()
        assertNull(db.documents().get(first))
        assertNull(db.folders().get(a))
        library.deleteDocument(second)
    }

    @Test
    fun blankPageGoesAfterTheChosenPageWithItsShape() = runBlocking<Unit> {
        val tmp = TestFiles.tempDir("blank")
        val sources = listOf(400 to 300, 300 to 400).mapIndexed { i, (w, h) ->
            File(tmp, "img$i.jpg").also { TestFiles.solidImage(it, w, h, android.graphics.Color.RED) }
        }
        val docId = library.importImages(sources.map { android.net.Uri.fromFile(it) }).documentIds.single()
        val before = library.getPages(docId)

        val blankId = library.addBlankPage(docId, afterPageId = before[0].id)!!
        val after = library.getPages(docId)
        assertEquals(listOf(before[0].id, blankId, before[1].id), after.map { it.id })
        val blank = after[1]
        assertEquals(1754, blank.width)
        assertEquals(1316, blank.height)
        assertTrue(File(blank.imagePath).exists())
        assertEquals(3, db.documents().get(docId)!!.pageCount)

        val endId = library.addBlankPage(docId, afterPageId = null)!!
        assertEquals(endId, library.getPages(docId).last().id)
        library.deleteDocument(docId)
        tmp.deleteRecursively()
    }
}
