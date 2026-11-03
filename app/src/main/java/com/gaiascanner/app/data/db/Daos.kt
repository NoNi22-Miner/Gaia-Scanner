package com.gaiascanner.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gaiascanner.app.core.JobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE trashedAt IS NULL ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY sortIndex, id")
    fun observeForDocument(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY sortIndex, id")
    suspend fun getForDocument(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun get(id: Long): PageEntity?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM pages WHERE documentId = :documentId")
    suspend fun maxSortIndex(documentId: Long): Int

    @Insert
    suspend fun insertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Update
    suspend fun updateAll(pages: List<PageEntity>)

    @Delete
    suspend fun delete(page: PageEntity)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun observeRecent(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: Long): JobEntity?

    @Query("SELECT status FROM jobs WHERE id = :id")
    suspend fun status(id: Long): JobStatus?

    @Insert
    suspend fun insert(job: JobEntity): Long

    @Update
    suspend fun update(job: JobEntity)

    @Query("UPDATE jobs SET progress = :progress, message = :message WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, message: String?)

    @Query("UPDATE jobs SET status = 'RUNNING', startedAt = :now, progress = 0, error = NULL WHERE id = :id")
    suspend fun markRunning(id: Long, now: Long)

    /** Cancels a job that has not started. Returns 1 if it was queued. */
    @Query("UPDATE jobs SET status = 'CANCELLED', finishedAt = :now WHERE id = :id AND status = 'QUEUED'")
    suspend fun cancelQueued(id: Long, now: Long): Int

    /** Asks a running job to stop after its current page. */
    @Query("UPDATE jobs SET status = 'CANCEL_REQUESTED' WHERE id = :id AND status = 'RUNNING'")
    suspend fun requestCancel(id: Long): Int
}
