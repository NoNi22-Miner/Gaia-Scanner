package com.gaiascanner.app.ui.document

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.R
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.data.AppSettings
import com.gaiascanner.app.data.Exporter
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.PageEntity
import com.gaiascanner.app.engine.Failures
import com.gaiascanner.app.ui.importSummary
import com.gaiascanner.app.ui.messageFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentViewModel(private val container: AppContainer, private val documentId: Long) : ViewModel() {

    /** [loaded] is false until the database answers. [document] is null if it was deleted. */
    data class State(val loaded: Boolean = false, val document: DocumentEntity? = null)

    data class Message(val text: String, val offerJobs: Boolean = false)

    private val context get() = container.appContext

    val state: StateFlow<State> = container.library.document(documentId)
        .map { State(loaded = true, document = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    val pages: StateFlow<List<PageEntity>> = container.library.pages(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val messages: SharedFlow<Message> = _messages

    val canUnlock: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    fun convertToImages(format: ImageFormat, dpi: Int, quality: Int) = viewModelScope.launch {
        container.settings.update { it.copy(imageFormat = format, dpi = dpi, jpegQuality = quality) }
        container.jobs.enqueuePdfToImages(listOf(documentId), format, dpi, quality)
        _messages.emit(Message(context.getString(R.string.job_started), offerJobs = true))
    }

    fun makePdf(pageSize: PageSizeOption, margin: MarginOption) = viewModelScope.launch {
        container.settings.update { it.copy(pageSize = pageSize, margin = margin) }
        container.jobs.enqueueImagesToPdf(documentId, pageSize, margin)
        _messages.emit(Message(context.getString(R.string.job_started), offerJobs = true))
    }

    fun movePage(pageId: Long, delta: Int) = viewModelScope.launch { container.library.movePage(documentId, pageId, delta) }

    fun rotatePage(pageId: Long) = viewModelScope.launch { container.library.rotatePage(pageId) }

    fun removePage(pageId: Long) = viewModelScope.launch { container.library.removePage(pageId) }

    fun addImages(uris: List<Uri>) = viewModelScope.launch {
        _messages.emit(Message(importSummary(context, container.library.importImages(uris, documentId))))
    }

    fun addPhoto(file: File) = viewModelScope.launch {
        _messages.emit(Message(importSummary(context, container.library.importPhoto(file, documentId))))
    }

    /** The screen closes itself when the document disappears from the database. */
    fun delete() = viewModelScope.launch {
        container.library.deleteDocument(documentId)
    }

    fun unlock(password: String) = viewModelScope.launch {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return@launch
        val text = try {
            container.library.unlockPdf(documentId, password)
            context.getString(R.string.unlocked)
        } catch (e: Exception) {
            context.getString(messageFor(Failures.reasonOf(e)))
        }
        _messages.emit(Message(text))
    }

    /** Saves the PDF to a file the user created with the system picker. */
    fun saveTo(uri: Uri) = viewModelScope.launch {
        val doc = state.value.document ?: return@launch
        report {
            val file = container.library.documentFiles(doc).first()
            withContext(Dispatchers.IO) { container.exporter.copyToUri(file, uri) }
        }
    }

    /** Saves all files into a new subfolder of the folder the user picked. */
    fun saveToFolder(treeUri: Uri) = viewModelScope.launch {
        val doc = state.value.document ?: return@launch
        report {
            val files = container.library.documentFiles(doc)
            val sub = if (doc.kind == DocumentKind.IMAGES) FileNames.baseName(doc.name) else null
            withContext(Dispatchers.IO) { container.exporter.copyToTree(treeUri, sub, files, Exporter::mimeTypeOf) }
        }
    }

    suspend fun shareIntent(): Intent? {
        val doc = state.value.document ?: return null
        val files = container.library.documentFiles(doc).filter { it.exists() }
        if (files.isEmpty()) return null
        val mime = if (doc.kind == DocumentKind.PDF) "application/pdf" else "image/*"
        return container.exporter.shareIntent(files, mime)
    }

    private suspend fun report(block: suspend () -> Unit) {
        val text = try {
            block()
            context.getString(R.string.saved)
        } catch (e: Exception) {
            context.getString(R.string.save_failed, context.getString(messageFor(Failures.reasonOf(e))))
        }
        _messages.emit(Message(text))
    }
}
