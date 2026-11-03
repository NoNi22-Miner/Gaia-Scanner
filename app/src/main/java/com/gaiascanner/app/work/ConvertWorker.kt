package com.gaiascanner.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.JobStatus
import com.gaiascanner.app.core.JobType
import com.gaiascanner.app.data.db.joinIds
import com.gaiascanner.app.engine.Failures
import com.gaiascanner.app.ui.messageFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ConvertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val jobs = container.database.jobs()
        val jobId = inputData.getLong(KEY_JOB_ID, -1L)
        val job = jobs.get(jobId) ?: return Result.success()
        if (job.status == JobStatus.CANCEL_REQUESTED) {
            jobs.update(job.copy(status = JobStatus.CANCELLED, finishedAt = now()))
            return Result.success()
        }
        // QUEUED normally. RUNNING if the app was killed mid-job and WorkManager retries it.
        if (job.status != JobStatus.QUEUED && job.status != JobStatus.RUNNING) return Result.success()

        val title = applicationContext.getString(
            if (job.type == JobType.PDF_TO_IMAGES) R.string.job_pdf_to_images else R.string.job_images_to_pdf,
        )
        tryForeground(title, null, 0)
        jobs.markRunning(jobId, now())

        var lastPercent = -1
        try {
            val outcome = container.conversionRunner.run(job) { percent, message ->
                jobs.updateProgress(jobId, percent.coerceIn(0, 100), message)
                if (percent != lastPercent) {
                    lastPercent = percent
                    tryForeground(title, message, percent)
                }
            }
            val failed = outcome.resultIds.isEmpty() && outcome.failures.isNotEmpty()
            val current = jobs.get(jobId) ?: return Result.success()
            jobs.update(
                current.copy(
                    status = if (failed) JobStatus.FAILED else JobStatus.DONE,
                    progress = 100,
                    message = null,
                    failures = outcome.failures.joinToString("\n").ifEmpty { null },
                    notes = outcome.notes.joinToString("\n").ifEmpty { null },
                    resultDocumentIds = joinIds(outcome.resultIds).ifEmpty { null },
                    finishedAt = now(),
                ),
            )
        } catch (e: ConversionRunner.CancelledByUser) {
            finish(jobId, JobStatus.CANCELLED, null)
        } catch (e: CancellationException) {
            // WorkManager stopped the work (for example the system needed resources).
            // WorkManager runs it again later, so leave the job queued.
            withContext(NonCancellable) {
                jobs.get(jobId)?.let { if (it.status == JobStatus.RUNNING) jobs.update(it.copy(status = JobStatus.QUEUED)) }
            }
            throw e
        } catch (e: Throwable) {
            val reason = Failures.reasonOf(e)
            finish(jobId, JobStatus.FAILED, applicationContext.getString(messageFor(reason)))
        }
        return Result.success()
    }

    private suspend fun finish(jobId: Long, status: JobStatus, error: String?) {
        val jobs = applicationContext.appContainer.database.jobs()
        val current = jobs.get(jobId) ?: return
        jobs.update(current.copy(status = status, error = error, message = null, finishedAt = now()))
    }

    /** Shows the progress notification. Starting it can fail if the app is in the background. */
    private suspend fun tryForeground(title: String, text: String?, percent: Int) {
        try {
            val notification = Notifications.progress(applicationContext, title, text, percent)
            setForeground(
                ForegroundInfo(Notifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Without the foreground service the work still runs, with the normal time limits.
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val TAG = "conversion"
    }
}
