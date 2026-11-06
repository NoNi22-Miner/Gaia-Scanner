package com.gaiascanner.app.ui.tools

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.R
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.FileNames
import com.gaiascanner.app.core.ReducePreset
import com.gaiascanner.app.core.WatermarkStyle
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.edit
import com.gaiascanner.app.engine.Failures
import com.gaiascanner.app.engine.PdfTools
import com.gaiascanner.app.ui.messageFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Reduce file size and text watermark for one document. Both make a new PDF
 * copy in the same folder; the original is kept (owner decision, phase 3).
 * Work runs while the screen is open; leaving the screen cancels it.
 */
class ToolsViewModel(private val container: AppContainer, private val documentId: Long) : ViewModel() {

    sealed interface Progress {
        data object Idle : Progress
        data class Working(val done: Int, val total: Int) : Progress
        /** A reduced copy is ready to keep or discard. */
        data class Ready(val originalBytes: Long, val newBytes: Long) : Progress
        data class Failed(val message: String) : Progress
        data object Saved : Progress
    }

    private val context get() = container.appContext

    val document: StateFlow<DocumentEntity?> = container.library.document(documentId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress

    private val _style = MutableStateFlow(WatermarkStyle())
    val style: StateFlow<WatermarkStyle> = _style

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private var work: Job? = null
    private var pending: File? = null

    init {
        viewModelScope.launch { _style.value = container.settings.current().watermark }
        startPreview()
    }

    fun setStyle(style: WatermarkStyle) {
        _style.value = style
    }

    @OptIn(FlowPreview::class)
    private fun startPreview() = viewModelScope.launch {
        _style.debounce(200).map { style ->
            val doc = document.first { it != null } ?: return@map null
            withContext(Dispatchers.Default) { renderPreview(doc, style) }
        }.collect { bitmap ->
            _preview.value = bitmap
        }
    }

    private suspend fun renderPreview(doc: DocumentEntity, style: WatermarkStyle): Bitmap? = when (doc.kind) {
        DocumentKind.PDF -> if (doc.locked) null else PdfTools.previewPdf(File(doc.localPath), PREVIEW_PX, style)
        DocumentKind.IMAGES -> container.library.getPages(doc.id).firstOrNull()?.let {
            PdfTools.previewImage(PdfTools.ImagePage(File(it.imagePath), it.edit()), PREVIEW_PX, style)
        }
    }

    fun reduce(preset: ReducePreset) = start { doc, out ->
        PdfTools.rewritePdf(File(doc.localPath), out, preset.dpi, preset.jpegQuality) { d, t ->
            _progress.value = Progress.Working(d, t)
        }
        _progress.value = Progress.Ready(doc.sizeBytes, out.length())
    }

    fun watermark() = start { doc, out ->
        val style = _style.value.normalized()
        container.settings.update { it.copy(watermark = style) }
        when (doc.kind) {
            DocumentKind.PDF -> {
                val s = container.settings.current()
                PdfTools.rewritePdf(File(doc.localPath), out, dpi = s.dpi.coerceIn(150, 300), jpegQuality = s.jpegQuality, watermark = style) { d, t ->
                    _progress.value = Progress.Working(d, t)
                }
            }
            DocumentKind.IMAGES -> {
                val s = container.settings.current()
                val pages = container.library.getPages(doc.id).map { PdfTools.ImagePage(File(it.imagePath), it.edit()) }
                PdfTools.imagesWithWatermark(pages, out, s.pageSize, s.margin, style) { d, t ->
                    _progress.value = Progress.Working(d, t)
                }
            }
        }
        save(context.getString(R.string.suffix_watermark))
    }

    /** Keeps the reduced copy as a new document next to the original. */
    fun keepReduced() = viewModelScope.launch { save(context.getString(R.string.suffix_small)) }

    fun discard() {
        pending?.delete()
        pending = null
        _progress.value = Progress.Idle
    }

    fun cancel() {
        work?.cancel()
        discard()
    }

    private fun start(block: suspend (DocumentEntity, File) -> Unit) {
        val doc = document.value ?: return
        if (doc.locked || work?.isActive == true) return
        discard()
        work = viewModelScope.launch {
            val out = File(context.cacheDir, "tools/${UUID.randomUUID()}.pdf")
            pending = out
            _progress.value = Progress.Working(0, doc.pageCount.coerceAtLeast(1))
            try {
                container.files.ensureSpace(doc.sizeBytes)
                withContext(Dispatchers.Default) { block(doc, out) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                out.delete()
                throw e
            } catch (e: Throwable) {
                out.delete()
                pending = null
                _progress.value = Progress.Failed(context.getString(messageFor(Failures.reasonOf(e))))
            }
        }
    }

    private suspend fun save(suffix: String) {
        val doc = document.value ?: return
        val file = pending ?: return
        val name = "${doc.name} ($suffix)"
        val dir = container.files.newDocumentDir()
        val target = File(dir, "${FileNames.baseName("$name.pdf")}.pdf")
        try {
            withContext(Dispatchers.IO) { container.files.moveFile(file, target) }
            container.library.addPdfFile(target, name, folderId = doc.folderId)
            pending = null
            container.postMessage(context.getString(R.string.tool_saved, name))
            _progress.value = Progress.Saved
        } catch (e: Throwable) {
            dir.deleteRecursively()
            _progress.value = Progress.Failed(context.getString(messageFor(Failures.reasonOf(e))))
        }
    }

    override fun onCleared() {
        pending?.delete()
    }

    private companion object {
        const val PREVIEW_PX = 720
    }
}
