package com.gaiascanner.app.work

import android.content.Context
import android.net.Uri
import com.gaiascanner.app.R
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.JobStatus
import com.gaiascanner.app.core.JobType
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.Quad
import com.gaiascanner.app.data.Exporter
import com.gaiascanner.app.data.FileStore
import com.gaiascanner.app.data.LibraryRepository
import com.gaiascanner.app.data.SettingsRepository
import com.gaiascanner.app.data.db.AppDatabase
import com.gaiascanner.app.data.db.JobEntity
import com.gaiascanner.app.data.db.inputIds
import com.gaiascanner.app.engine.ConvertException
import com.gaiascanner.app.engine.Failures
import com.gaiascanner.app.engine.ImagesToPdf
import com.gaiascanner.app.engine.PdfToImages
import com.gaiascanner.app.ui.messageFor
import java.io.File
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Runs one job: reads its inputs, converts, and adds the results to the library. */
class ConversionRunner(
    private val context: Context,
    private val db: AppDatabase,
    private val files: FileStore,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val exporter: Exporter,
) {
    /** Thrown when the user asked to cancel. */
    class CancelledByUser : Exception()

    data class Outcome(val resultIds: List<Long>, val failures: List<String>, val notes: List<String>)

    suspend fun run(job: JobEntity, onProgress: suspend (percent: Int, message: String) -> Unit): Outcome {
        val checkCancelled: suspend () -> Unit = {
            if (db.jobs().status(job.id) == JobStatus.CANCEL_REQUESTED) throw CancelledByUser()
        }
        val workDir = files.freshJobDir(job.id)
        try {
            return when (job.type) {
                JobType.PDF_TO_IMAGES -> pdfToImages(job, workDir, checkCancelled, onProgress)
                JobType.IMAGES_TO_PDF -> imagesToPdf(job, workDir, checkCancelled, onProgress)
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun pdfToImages(
        job: JobEntity,
        workDir: File,
        checkCancelled: suspend () -> Unit,
        onProgress: suspend (Int, String) -> Unit,
    ): Outcome {
        val format = job.imageFormat ?: ImageFormat.JPG
        val dpi = job.dpi ?: ConvertDefaults.DPI
        val quality = job.jpegQuality ?: ConvertDefaults.JPEG_QUALITY
        val ids = job.inputIds()
        val results = mutableListOf<Long>()
        val failures = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val outputFolder = settings.current().outputFolderUri

        ids.forEachIndexed { fileIndex, docId ->
            val doc = library.getDocument(docId)
            val label = doc?.name ?: "#$docId"
            if (doc == null || doc.kind != DocumentKind.PDF) {
                failures += line(label, FailureReason.MISSING_FILE)
                return@forEachIndexed
            }
            val base = FileNames.baseName(doc.name)
            val stage = File(workDir, fileIndex.toString())
            try {
                val pages = PdfToImages().convert(
                    pdf = File(doc.localPath),
                    outputDir = stage,
                    baseName = base,
                    format = format,
                    dpi = dpi,
                    jpegQuality = quality,
                    checkCancelled = checkCancelled,
                ) { done, total ->
                    val percent = ((fileIndex + done / total.toFloat()) / ids.size * 100).roundToInt()
                    onProgress(percent, context.getString(R.string.progress_page_of, doc.name, done, total))
                }
                val dir = files.newDocumentDir()
                val moved = pages.map { page ->
                    val target = File(dir, page.file.name)
                    files.moveFile(page.file, target)
                    Triple(target, page.width, page.height)
                }
                results += library.addImageDocument(dir, base, moved)
                val lowered = pages.withIndex().filter { it.value.capped }.map { it.index + 1 }
                if (lowered.isNotEmpty()) {
                    notes += context.getString(R.string.note_lower_dpi, doc.name, lowered.joinToString(", "))
                }
                copyToOutputFolder(outputFolder, base, moved.map { it.first })?.let { notes += it }
            } catch (e: CancelledByUser) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                failures += line(doc.name, Failures.reasonOf(e))
            } finally {
                stage.deleteRecursively()
            }
        }
        return Outcome(results, failures, notes)
    }

    private suspend fun imagesToPdf(
        job: JobEntity,
        workDir: File,
        checkCancelled: suspend () -> Unit,
        onProgress: suspend (Int, String) -> Unit,
    ): Outcome {
        val docId = job.inputIds().firstOrNull()
        val doc = docId?.let { library.getDocument(it) }
        if (doc == null || doc.kind != DocumentKind.IMAGES) {
            throw ConvertException(FailureReason.MISSING_FILE)
        }
        val pages = library.getPages(doc.id)
        if (pages.isEmpty()) throw ConvertException(FailureReason.MISSING_FILE)

        val base = FileNames.baseName(doc.name)
        val name = FileNames.pdfName(base, LocalDateTime.now())
        val staged = File(workDir, name)
        ImagesToPdf().convert(
            inputs = pages.map { ImagesToPdf.Input(File(it.imagePath), it.rotation, Quad.parse(it.crop)) },
            output = staged,
            pageSize = job.pageSize ?: PageSizeOption.FIT,
            margin = job.margin ?: MarginOption.NONE,
            checkCancelled = checkCancelled,
        ) { done, total ->
            onProgress((done * 100f / total).roundToInt(), context.getString(R.string.progress_page_of, doc.name, done, total))
        }
        val dir = files.newDocumentDir()
        val target = File(dir, name)
        files.moveFile(staged, target)
        val id = try {
            library.addPdfFile(target, FileNames.baseName(name))
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
        val notes = listOfNotNull(copyToOutputFolder(settings.current().outputFolderUri, null, listOf(target)))
        return Outcome(listOf(id), emptyList(), notes)
    }

    /** Copies results to the folder set in Settings. Returns a note if that failed. */
    private fun copyToOutputFolder(treeUri: String?, subfolder: String?, outputs: List<File>): String? {
        if (treeUri == null) return null
        return try {
            val uri = Uri.parse(treeUri)
            if (!exporter.canWriteTo(uri)) throw ConvertException(FailureReason.MISSING_FILE)
            exporter.copyToTree(uri, subfolder, outputs, Exporter::mimeTypeOf)
            null
        } catch (e: Exception) {
            context.getString(R.string.note_output_folder_failed)
        }
    }

    private fun line(name: String, reason: FailureReason) =
        context.getString(R.string.failure_line, name, context.getString(messageFor(reason)))
}
