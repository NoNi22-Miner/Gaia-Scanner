package com.gaiascanner.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.gaiascanner.app.core.FailureReason
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.engine.ConvertException
import java.io.File

/** Saving to folders the user picked (Storage Access Framework) and sharing. */
class Exporter(private val context: Context) {

    /** Copies one file to a document the user created with the system picker. */
    fun copyToUri(file: File, uri: Uri) {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw ConvertException(FailureReason.MISSING_FILE)
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    /**
     * Copies [files] into the folder [treeUri], inside a new subfolder named
     * [subfolder] when given. Existing names get " (2)" and so on.
     */
    fun copyToTree(treeUri: Uri, subfolder: String?, files: List<File>, mimeType: (File) -> String) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.exists() || !root.canWrite()) throw ConvertException(FailureReason.MISSING_FILE)
        val target = if (subfolder == null) {
            root
        } else {
            val name = FileNames.unique(subfolder) { root.findFile(it) != null }
            root.createDirectory(name) ?: throw ConvertException(FailureReason.MISSING_FILE)
        }
        for (file in files) {
            val name = FileNames.unique(file.name) { target.findFile(it) != null }
            val doc = target.createFile(mimeType(file), name) ?: throw ConvertException(FailureReason.MISSING_FILE)
            val out = context.contentResolver.openOutputStream(doc.uri, "wt")
                ?: throw ConvertException(FailureReason.MISSING_FILE)
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
        }
    }

    /** True if the app still holds write access to the folder picked in Settings. */
    fun canWriteTo(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }

    fun shareIntent(files: List<File>, mimeType: String): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mimeType
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant for every file.
        val clip = ClipData.newRawUri(null, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        return Intent.createChooser(intent, null)
    }

    companion object {
        fun mimeTypeOf(file: File): String = when (FileNames.extension(file.name)) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> "image/jpeg"
        }
    }
}
