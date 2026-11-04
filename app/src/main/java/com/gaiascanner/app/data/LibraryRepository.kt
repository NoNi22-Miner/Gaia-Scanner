package com.gaiascanner.app.data

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.EraseStrokes
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.core.PageEdit
import com.gaiascanner.app.core.PageFilter
import com.gaiascanner.app.core.Rotation
import com.gaiascanner.app.data.db.AppDatabase
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.PageEntity
import com.gaiascanner.app.engine.ConvertException
import com.gaiascanner.app.engine.Failures
import com.gaiascanner.app.engine.ImageDecoding
import com.gaiascanner.app.engine.PdfFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class LibraryRepository(private val db: AppDatabase, private val files: FileStore) {

    /** A file that failed to import, with its name and the reason. */
    data class ImportFailure(val name: String, val reason: FailureReason)

    data class ImportResult(
        val documentIds: List<Long>,
        val imported: Int,
        val failures: List<ImportFailure>,
        /** Ids of the image pages this import added, in order. */
        val pageIds: List<Long> = emptyList(),
    )

    val documents: Flow<List<DocumentEntity>> = db.documents().observeAll()

    fun document(id: Long): Flow<DocumentEntity?> = db.documents().observe(id)

    fun pages(documentId: Long): Flow<List<PageEntity>> = db.pages().observeForDocument(documentId)

    suspend fun getDocument(id: Long): DocumentEntity? = db.documents().get(id)

    suspend fun getPages(documentId: Long): List<PageEntity> = db.pages().getForDocument(documentId)

    suspend fun getPage(pageId: Long): PageEntity? = db.pages().get(pageId)

    /** Each PDF becomes its own document. */
    suspend fun importPdfs(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val failures = mutableListOf<ImportFailure>()
        for (uri in uris) {
            val info = files.describe(uri)
            val base = FileNames.baseName(info.displayName)
            val dir = files.newDocumentDir()
            val target = File(dir, "$base.pdf")
            try {
                files.copyIn(uri, target, info.sizeBytes)
                ids += addPdfFile(target, base)
            } catch (e: Exception) {
                dir.deleteRecursively()
                failures += ImportFailure(info.displayName ?: base, Failures.reasonOf(e))
            }
        }
        ImportResult(ids, ids.size, failures)
    }

    /** Registers a PDF file that is already in its own library folder. */
    suspend fun addPdfFile(file: File, name: String): Long {
        val (pageCount, locked) = try {
            PdfFiles.pageCount(file) to false
        } catch (e: ConvertException) {
            if (e.reason == FailureReason.PASSWORD) 0 to true else throw e
        }
        val now = System.currentTimeMillis()
        return db.documents().insert(
            DocumentEntity(
                name = name,
                kind = DocumentKind.PDF,
                pageCount = pageCount,
                sizeBytes = file.length(),
                localPath = file.path,
                locked = locked,
                createdAt = now,
                modifiedAt = now,
            ),
        )
    }

    /**
     * Imports images as pages. They join document [intoDocumentId] if given,
     * otherwise they form one new image document.
     */
    suspend fun importImages(uris: List<Uri>, intoDocumentId: Long? = null): ImportResult =
        withContext(Dispatchers.IO) {
            val sources = uris.map { uri ->
                val info = files.describe(uri)
                ImageSource(info.displayName ?: "image", info.mimeType) { target -> files.copyIn(uri, target, info.sizeBytes) }
            }
            addImages(sources, intoDocumentId, newName = null)
        }

    /**
     * Imports photos from the in-app camera, in shooting order. The files are
     * moved, not copied. They join [intoDocumentId] if given, otherwise they
     * form one new document named after the time.
     */
    suspend fun importPhotos(photos: List<File>, intoDocumentId: Long?): ImportResult = withContext(Dispatchers.IO) {
        val stamp = FileNames.stamp(java.time.LocalDateTime.now())
        val sources = photos.mapIndexed { i, photo ->
            ImageSource("Photo_${stamp}_${i + 1}.jpg", "image/jpeg") { target -> files.moveFile(photo, target) }
        }
        val result = addImages(sources, intoDocumentId, newName = "Photo_$stamp")
        photos.forEach { it.delete() }
        result
    }

    /**
     * Saves everything set in the page editor. A full-frame crop is stored as none.
     * Eraser strokes are dropped if the crop or rotation changed, because they are
     * placed in page coordinates after crop and rotation.
     */
    suspend fun setPageEdit(pageId: Long, edit: PageEdit) {
        val page = db.pages().get(pageId) ?: return
        val crop = edit.crop?.takeUnless { it.isFullFrame }?.serialize()
        val rotation = Rotation.normalize(edit.rotation)
        val geometryChanged = crop != page.crop || rotation != page.rotation
        val strokes = if (geometryChanged && page.erase != null && edit.strokes == EraseStrokes.parse(page.erase)) {
            emptyList()
        } else {
            edit.strokes
        }
        db.pages().update(
            page.copy(
                crop = crop,
                rotation = rotation,
                filter = edit.filter.takeUnless { it == PageFilter.ORIGINAL },
                brightness = edit.tone.brightness.takeUnless { it == 0 },
                contrast = edit.tone.contrast.takeUnless { it == 0 },
                erase = EraseStrokes.serialize(strokes),
            ),
        )
        touch(page.documentId)
    }

    /**
     * Removes pages (Discard / Discard All in the editor). An image document left
     * with no pages is deleted too. Returns true if the document was deleted.
     */
    suspend fun removePages(documentId: Long, pageIds: List<Long>): Boolean {
        val removed = mutableListOf<String>()
        db.withTransaction {
            for (id in pageIds) {
                val page = db.pages().get(id) ?: continue
                if (page.documentId != documentId) continue
                db.pages().delete(page)
                removed += page.imagePath
            }
            refreshImageDocument(documentId)
        }
        withContext(Dispatchers.IO) { removed.forEach { File(it).delete() } }
        if (db.pages().getForDocument(documentId).isEmpty()) {
            deleteDocument(documentId)
            return true
        }
        return false
    }

    private class ImageSource(val displayName: String, val mimeType: String?, val writeTo: (File) -> Unit)

    private suspend fun addImages(sources: List<ImageSource>, intoDocumentId: Long?, newName: String?): ImportResult {
        val existing = intoDocumentId?.let { db.documents().get(it) }?.takeIf { it.kind == DocumentKind.IMAGES }
        val dir = existing?.let { File(it.localPath).apply { mkdirs() } } ?: files.newDocumentDir()
        var nextIndex = existing?.let { db.pages().maxSortIndex(it.id) + 1 } ?: 0
        val pages = mutableListOf<PageEntity>()
        val failures = mutableListOf<ImportFailure>()

        for (source in sources) {
            val base = FileNames.baseName(source.displayName)
            val ext = FileNames.extension(source.displayName).ifEmpty { extensionFor(source.mimeType) }
            val name = FileNames.unique("$base.$ext") { File(dir, it).exists() }
            val target = File(dir, name)
            try {
                source.writeTo(target)
                val info = ImageDecoding.readInfo(target)
                    ?: throw ConvertException(FailureReason.UNSUPPORTED_FORMAT)
                val (w, h) = info.uprightSize()
                pages += PageEntity(documentId = 0, sortIndex = nextIndex++, imagePath = target.path, width = w, height = h)
            } catch (e: Exception) {
                target.delete()
                failures += ImportFailure(source.displayName, Failures.reasonOf(e))
            }
        }

        if (pages.isEmpty()) {
            if (existing == null) dir.deleteRecursively()
            return ImportResult(emptyList(), 0, failures)
        }
        var pageIds: List<Long> = emptyList()

        val now = System.currentTimeMillis()
        val documentId = db.withTransaction {
            val id = existing?.id ?: db.documents().insert(
                DocumentEntity(
                    name = newName ?: FileNames.baseName(sources.first().displayName),
                    kind = DocumentKind.IMAGES,
                    pageCount = 0,
                    sizeBytes = 0,
                    localPath = dir.path,
                    createdAt = now,
                    modifiedAt = now,
                ),
            )
            pageIds = db.pages().insertAll(pages.map { it.copy(documentId = id) })
            refreshImageDocument(id)
            id
        }
        return ImportResult(listOf(documentId), pages.size, failures, pageIds)
    }

    /** Registers image files already in their own library folder as a new document. */
    suspend fun addImageDocument(dir: File, name: String, images: List<Triple<File, Int, Int>>): Long {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val id = db.documents().insert(
                DocumentEntity(
                    name = name,
                    kind = DocumentKind.IMAGES,
                    pageCount = 0,
                    sizeBytes = 0,
                    localPath = dir.path,
                    createdAt = now,
                    modifiedAt = now,
                ),
            )
            db.pages().insertAll(
                images.mapIndexed { i, (file, w, h) ->
                    PageEntity(documentId = id, sortIndex = i, imagePath = file.path, width = w, height = h)
                },
            )
            refreshImageDocument(id)
            id
        }
    }

    /** Moves a page up (delta -1) or down (delta +1). */
    suspend fun movePage(documentId: Long, pageId: Long, delta: Int) {
        db.withTransaction {
            val pages = db.pages().getForDocument(documentId).toMutableList()
            val from = pages.indexOfFirst { it.id == pageId }
            val to = from + delta
            if (from < 0 || to !in pages.indices) return@withTransaction
            pages.add(to, pages.removeAt(from))
            db.pages().updateAll(pages.mapIndexed { i, p -> p.copy(sortIndex = i) })
            touch(documentId)
        }
    }

    suspend fun rotatePage(pageId: Long) {
        val page = db.pages().get(pageId) ?: return
        db.pages().update(page.copy(rotation = Rotation.rotateClockwise(page.rotation)))
        touch(page.documentId)
    }

    suspend fun removePage(pageId: Long) {
        val page = db.pages().get(pageId) ?: return
        db.withTransaction {
            db.pages().delete(page)
            refreshImageDocument(page.documentId)
        }
        withContext(Dispatchers.IO) { File(page.imagePath).delete() }
    }

    /** Deletes the document and its files. Trash replaces this in phase 3. */
    suspend fun deleteDocument(id: Long) {
        val doc = db.documents().get(id) ?: return
        db.documents().delete(doc)
        withContext(Dispatchers.IO) {
            val path = File(doc.localPath)
            val folder = if (doc.kind == DocumentKind.PDF) path.parentFile else path
            if (folder != null && folder.parentFile == files.libraryRoot) folder.deleteRecursively() else path.delete()
        }
    }

    /** Files in page order: the PDF itself, or the page images. */
    suspend fun documentFiles(doc: DocumentEntity): List<File> = when (doc.kind) {
        DocumentKind.PDF -> listOf(File(doc.localPath))
        DocumentKind.IMAGES -> db.pages().getForDocument(doc.id).map { File(it.imagePath) }
    }

    /** Replaces a locked PDF with an unlocked copy. API 35+ only. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    suspend fun unlockPdf(id: Long, password: String) = withContext(Dispatchers.IO) {
        val doc = db.documents().get(id) ?: throw ConvertException(FailureReason.MISSING_FILE)
        val original = File(doc.localPath)
        val temp = File(original.parentFile, "${original.name}.unlocking")
        try {
            PdfFiles.writeUnlockedCopy(original, password, temp)
            val count = PdfFiles.pageCount(temp)
            files.moveFile(temp, original)
            db.documents().update(
                doc.copy(locked = false, pageCount = count, sizeBytes = original.length(), modifiedAt = System.currentTimeMillis()),
            )
        } finally {
            temp.delete()
        }
    }

    /** Recounts pages and size. Runs inside Room transactions, on Room's thread. */
    private suspend fun refreshImageDocument(id: Long) {
        val doc = db.documents().get(id) ?: return
        val pages = db.pages().getForDocument(id)
        val size = pages.sumOf { File(it.imagePath).length() }
        db.documents().update(doc.copy(pageCount = pages.size, sizeBytes = size, modifiedAt = System.currentTimeMillis()))
    }

    private suspend fun touch(id: Long) {
        val doc = db.documents().get(id) ?: return
        db.documents().update(doc.copy(modifiedAt = System.currentTimeMillis()))
    }

    private fun extensionFor(mimeType: String?): String = when (mimeType?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "jpg"
    }
}
