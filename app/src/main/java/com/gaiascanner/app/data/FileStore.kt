package com.gaiascanner.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.engine.ConvertException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** App-private storage. Imports are copied in, so picker access can expire safely. */
class FileStore(private val context: Context) {

    data class SourceInfo(val displayName: String?, val sizeBytes: Long?, val mimeType: String?)

    val libraryRoot: File get() = File(context.filesDir, "library").apply { mkdirs() }

    fun newDocumentDir(): File = File(libraryRoot, UUID.randomUUID().toString()).apply { mkdirs() }

    /** Empty scratch folder for one job. Results move to the library when the job ends. */
    fun freshJobDir(jobId: Long): File =
        File(context.cacheDir, "jobs/$jobId").apply {
            deleteRecursively()
            mkdirs()
        }

    fun newCameraFile(): File =
        File(context.cacheDir, "camera").apply { mkdirs() }
            .let { File(it, "photo_${System.currentTimeMillis()}.jpg") }

    fun describe(uri: Uri): SourceInfo {
        var name: String? = null
        var size: Long? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
        } catch (_: Exception) {
            // Some providers do not support the query. The copy still works.
        }
        if (name == null) name = uri.lastPathSegment?.substringAfterLast('/')
        val mime = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        return SourceInfo(name, size, mime)
    }

    /** Copies [uri] to [target]. Throws [ConvertException] on failure. */
    fun copyIn(uri: Uri, target: File, expectedSize: Long?) {
        ensureSpace(expectedSize ?: 0L)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ConvertException(FailureReason.MISSING_FILE)
            input.use { inp -> FileOutputStream(target).use { out -> inp.copyTo(out, BUFFER) } }
        } catch (e: ConvertException) {
            target.delete()
            throw e
        } catch (e: SecurityException) {
            target.delete()
            throw ConvertException(FailureReason.MISSING_FILE, e)
        } catch (e: IOException) {
            target.delete()
            throw ConvertException(com.gaiascanner.app.engine.Failures.reasonOf(e).let {
                if (it == FailureReason.DAMAGED) FailureReason.MISSING_FILE else it
            }, e)
        }
    }

    fun ensureSpace(bytesNeeded: Long) {
        if (context.filesDir.usableSpace < bytesNeeded + SPACE_RESERVE) {
            throw ConvertException(FailureReason.OUT_OF_SPACE)
        }
    }

    /** Moves [source] into [destination] (same file system, so usually a rename). */
    fun moveFile(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (source.renameTo(destination)) return
        source.copyTo(destination, overwrite = true)
        source.delete()
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val SPACE_RESERVE = 50L * 1024 * 1024
    }
}
