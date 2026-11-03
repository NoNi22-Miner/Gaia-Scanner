package com.gaiascanner.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.JobStatus
import com.gaiascanner.app.core.JobType
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption

/**
 * A library item. For a PDF, [localPath] is the PDF file. For an image set,
 * [localPath] is the folder that holds the page images.
 */
@Entity(tableName = "documents", indices = [Index("folderId"), Index("trashedAt")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: DocumentKind,
    val pageCount: Int,
    val sizeBytes: Long,
    val localPath: String,
    /** Folders arrive in phase 3. */
    val folderId: Long? = null,
    /** Drive sync arrives in phase 4. */
    val driveFileId: String? = null,
    /** PDF that needs a password before it can be rendered. */
    val locked: Boolean = false,
    val createdAt: Long,
    val modifiedAt: Long,
    /** Trash arrives in phase 3. */
    val trashedAt: Long? = null,
)

/** One image of an image-set document. [width] x [height] is upright (EXIF applied). */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val sortIndex: Int,
    val imagePath: String,
    val width: Int,
    val height: Int,
    /** Extra clockwise turn chosen by the user: 0, 90, 180 or 270. */
    val rotation: Int = 0,
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: JobType,
    /** Comma-separated document ids. */
    val inputDocumentIds: String,
    val imageFormat: ImageFormat? = null,
    val dpi: Int? = null,
    val jpegQuality: Int? = null,
    val pageSize: PageSizeOption? = null,
    val margin: MarginOption? = null,
    val status: JobStatus = JobStatus.QUEUED,
    /** 0 to 100. */
    val progress: Int = 0,
    val message: String? = null,
    /** Why the whole job failed. */
    val error: String? = null,
    /** One line per input file that failed. */
    val failures: String? = null,
    /** Things the user should know, such as pages rendered at a lower DPI. */
    val notes: String? = null,
    /** Comma-separated ids of the documents this job created. */
    val resultDocumentIds: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
)

fun parseIds(value: String?): List<Long> =
    value.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

fun joinIds(ids: List<Long>): String = ids.joinToString(",")

fun JobEntity.inputIds(): List<Long> = parseIds(inputDocumentIds)

fun JobEntity.resultIds(): List<Long> = parseIds(resultDocumentIds)
