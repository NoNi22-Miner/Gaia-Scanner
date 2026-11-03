package com.gaiascanner.app.work

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.gaiascanner.app.TestFiles
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.JobStatus
import com.gaiascanner.app.core.JobType
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.data.db.JobEntity
import com.gaiascanner.app.data.db.joinIds
import com.gaiascanner.app.data.db.resultIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs ConvertWorker directly against the app's real database and files. */
@RunWith(AndroidJUnit4::class)
class ConvertWorkerTest {

    private val context = TestFiles.context
    private val container = context.appContainer

    @Test
    fun pdfToImagesJobCreatesImageDocument() = runBlocking {
        val dir = container.files.newDocumentDir()
        val pdf = File(dir, "report.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(3)) }
        val pdfId = container.library.addPdfFile(pdf, "report")

        val jobId = container.database.jobs().insert(
            JobEntity(
                type = JobType.PDF_TO_IMAGES,
                inputDocumentIds = joinIds(listOf(pdfId)),
                imageFormat = ImageFormat.JPG,
                dpi = 72,
                jpegQuality = 90,
                createdAt = System.currentTimeMillis(),
            ),
        )
        assertEquals(ListenableWorker.Result.success(), runWorker(jobId))

        val job = container.database.jobs().get(jobId)!!
        assertEquals(JobStatus.DONE, job.status)
        val result = container.library.getDocument(job.resultIds().single())!!
        assertEquals(DocumentKind.IMAGES, result.kind)
        assertEquals(3, result.pageCount)
        val pages = container.library.getPages(result.id)
        assertEquals(listOf("report_p001.jpg", "report_p002.jpg", "report_p003.jpg"), pages.map { File(it.imagePath).name })
        assertTrue(pages.all { File(it.imagePath).exists() })

        container.library.deleteDocument(result.id)
        container.library.deleteDocument(pdfId)
    }

    @Test
    fun imagesToPdfJobUsesPageOrderAndRotation() = runBlocking {
        val tmp = TestFiles.tempDir("job-images")
        val sources = listOf(Color.RED, Color.BLUE).mapIndexed { i, c ->
            File(tmp, "img$i.jpg").also { TestFiles.solidImage(it, 400, 200, c) }
        }
        val docId = container.library.importImages(sources.map { android.net.Uri.fromFile(it) }).documentIds.single()
        val pages = container.library.getPages(docId)
        container.library.rotatePage(pages[1].id)
        container.library.movePage(docId, pages[1].id, -1)

        val jobId = container.database.jobs().insert(
            JobEntity(
                type = JobType.IMAGES_TO_PDF,
                inputDocumentIds = joinIds(listOf(docId)),
                pageSize = PageSizeOption.FIT,
                margin = MarginOption.NONE,
                createdAt = System.currentTimeMillis(),
            ),
        )
        runWorker(jobId)
        val job = container.database.jobs().get(jobId)!!
        assertEquals(JobStatus.DONE, job.status)
        val pdf = container.library.getDocument(job.resultIds().single())!!
        assertEquals(DocumentKind.PDF, pdf.kind)
        assertEquals(2, pdf.pageCount)
        assertTrue(File(pdf.localPath).name.endsWith(".pdf"))

        container.library.deleteDocument(pdf.id)
        container.library.deleteDocument(docId)
        tmp.deleteRecursively()
    }

    @Test
    fun cancelRequestStopsJob() = runBlocking {
        val dir = container.files.newDocumentDir()
        val pdf = File(dir, "long.pdf").also { TestFiles.makePdf(it, TestFiles.a4Pages(5)) }
        val pdfId = container.library.addPdfFile(pdf, "long")
        val jobId = container.database.jobs().insert(
            JobEntity(
                type = JobType.PDF_TO_IMAGES,
                inputDocumentIds = joinIds(listOf(pdfId)),
                imageFormat = ImageFormat.JPG,
                dpi = 72,
                jpegQuality = 90,
                status = JobStatus.RUNNING,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // The runner checks for this before every page.
        container.database.jobs().requestCancel(jobId)
        runWorker(jobId)
        assertEquals(JobStatus.CANCELLED, container.database.jobs().get(jobId)!!.status)
        container.library.deleteDocument(pdfId)
    }

    @Test
    fun missingInputFailsJobWithoutCrashing() = runBlocking {
        val jobId = container.database.jobs().insert(
            JobEntity(
                type = JobType.PDF_TO_IMAGES,
                inputDocumentIds = joinIds(listOf(Long.MAX_VALUE)),
                imageFormat = ImageFormat.JPG,
                dpi = 72,
                jpegQuality = 90,
                createdAt = System.currentTimeMillis(),
            ),
        )
        assertEquals(ListenableWorker.Result.success(), runWorker(jobId))
        val job = container.database.jobs().get(jobId)!!
        assertEquals(JobStatus.FAILED, job.status)
        assertTrue(job.failures!!.isNotBlank())
    }

    private suspend fun runWorker(jobId: Long): ListenableWorker.Result =
        TestListenableWorkerBuilder<ConvertWorker>(context)
            .setInputData(workDataOf(ConvertWorker.KEY_JOB_ID to jobId))
            .build()
            .doWork()
}
