package com.gaiascanner.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.JobType
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.data.db.AppDatabase
import com.gaiascanner.app.data.db.JobEntity
import com.gaiascanner.app.data.db.joinIds
import kotlinx.coroutines.flow.Flow

/**
 * Jobs run one at a time in a single WorkManager chain. A failed or cancelled
 * job still ends its work as "success", so the jobs queued after it keep running.
 */
class JobRepository(private val context: Context, private val db: AppDatabase) {

    val recent: Flow<List<JobEntity>> = db.jobs().observeRecent()

    suspend fun enqueuePdfToImages(documentIds: List<Long>, format: ImageFormat, dpi: Int, jpegQuality: Int): Long {
        val id = db.jobs().insert(
            JobEntity(
                type = JobType.PDF_TO_IMAGES,
                inputDocumentIds = joinIds(documentIds),
                imageFormat = format,
                dpi = dpi,
                jpegQuality = jpegQuality,
                createdAt = System.currentTimeMillis(),
            ),
        )
        schedule(id)
        return id
    }

    suspend fun enqueueImagesToPdf(documentId: Long, pageSize: PageSizeOption, margin: MarginOption): Long {
        val id = db.jobs().insert(
            JobEntity(
                type = JobType.IMAGES_TO_PDF,
                inputDocumentIds = joinIds(listOf(documentId)),
                pageSize = pageSize,
                margin = margin,
                createdAt = System.currentTimeMillis(),
            ),
        )
        schedule(id)
        return id
    }

    /** A queued job is cancelled at once. A running job stops after its current page. */
    suspend fun cancel(jobId: Long) {
        if (db.jobs().cancelQueued(jobId, System.currentTimeMillis()) == 0) {
            db.jobs().requestCancel(jobId)
        }
    }

    private fun schedule(jobId: Long) {
        val request = OneTimeWorkRequestBuilder<ConvertWorker>()
            .setInputData(workDataOf(ConvertWorker.KEY_JOB_ID to jobId))
            .addTag(ConvertWorker.TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_CHAIN, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val UNIQUE_CHAIN = "conversions"
    }
}
