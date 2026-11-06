package com.gaiascanner.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.data.LibraryRepository
import com.gaiascanner.app.ui.importSummary

/** Files shared to the app or opened with it. */
object IncomingFiles {

    fun uris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
    }

    /** Imports PDFs one per document and images together as one document. Returns a summary. */
    suspend fun import(container: AppContainer, uris: List<Uri>): String {
        val pdfs = mutableListOf<Uri>()
        val images = mutableListOf<Uri>()
        for (uri in uris) {
            val info = container.files.describe(uri)
            val isPdf = info.mimeType == "application/pdf" || FileNames.extension(info.displayName) == "pdf"
            if (isPdf) pdfs += uri else images += uri
        }
        val results = buildList {
            if (pdfs.isNotEmpty()) add(container.library.importPdfs(pdfs))
            if (images.isNotEmpty()) add(container.library.importImages(images))
        }
        val merged = LibraryRepository.ImportResult(
            documentIds = results.flatMap { it.documentIds },
            imported = results.sumOf { it.imported },
            failures = results.flatMap { it.failures },
        )
        return importSummary(container.appContext, merged)
    }
}
