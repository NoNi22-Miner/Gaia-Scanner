package com.gaiascanner.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.core.CropRotation
import com.gaiascanner.app.core.EraseMode
import com.gaiascanner.app.core.EraseStroke
import com.gaiascanner.app.core.PageEdit
import com.gaiascanner.app.core.PageFilter
import com.gaiascanner.app.core.Point
import com.gaiascanner.app.core.Quad
import com.gaiascanner.app.core.Rotation
import com.gaiascanner.app.data.db.PageEntity
import com.gaiascanner.app.data.db.edit
import com.gaiascanner.app.engine.EdgeDetector
import com.gaiascanner.app.engine.ImageDecoding
import com.gaiascanner.app.engine.PageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Edits pages one at a time: Crop (corners, edges, auto detect, rotate),
 * Enhance (filter, brightness, contrast) and Erase. Nothing is saved until
 * Next / Done, Process All, or a discard.
 */
class PageEditorViewModel(
    private val container: AppContainer,
    private val documentId: Long,
    pageIds: List<Long>,
    private val startMode: Mode,
    private val autoDetect: Boolean,
) : ViewModel() {

    enum class Mode { CROP, ENHANCE, ERASE }

    private val queue = pageIds.toMutableList()

    var mode by mutableStateOf(startMode)
    var index by mutableIntStateOf(0)
        private set
    var page by mutableStateOf<PageEntity?>(null)
        private set

    /** Upright photo (EXIF applied), no crop or rotation, longest side [PREVIEW_SIDE]. */
    var upright by mutableStateOf<Bitmap?>(null)
        private set
    var edit by mutableStateOf(PageEdit())
        private set

    /** The page as it will look, for Enhance and Erase. */
    var rendered by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(true)
        private set
    var noEdgesFound by mutableStateOf(false)
        private set

    var eraseMode by mutableStateOf(EraseMode.WHITE)
    var brushSize by mutableFloatStateOf(0.04f)
    var showTone by mutableStateOf(false)

    val count: Int get() = queue.size
    val isLast: Boolean get() = index >= queue.lastIndex

    private var renderJob: Job? = null

    init {
        load(0)
    }

    private fun load(i: Int) {
        index = i
        busy = true
        mode = startMode
        noEdgesFound = false
        viewModelScope.launch {
            val p = queue.getOrNull(i)?.let { container.library.getPage(it) }
            page = p
            if (p == null) {
                busy = false
                return@launch
            }
            val preview = withContext(Dispatchers.IO) { ImageDecoding.decodeThumbnail(File(p.imagePath), PREVIEW_SIDE, 0, null) }
            upright = preview
            var e = p.edit()
            if (autoDetect && p.crop == null && preview != null) {
                val found = withContext(Dispatchers.Default) { EdgeDetector.detect(preview) }
                if (found != null) e = e.copy(crop = found) else noEdgesFound = true
            }
            edit = e
            busy = false
            render(immediate = true)
        }
    }

    // ---- Crop ----

    fun moveCorner(corner: Int, shown: Point) =
        changeGeometry(edit.copy(crop = currentCrop().withCorner(corner, CropRotation.toStored(shown.clamped(), edit.rotation))))

    fun moveEdge(side: Int, dx: Float, dy: Float) =
        changeGeometry(edit.copy(crop = currentCrop().moveEdge(side, dx, dy, edit.rotation)))

    fun fullFrame() = changeGeometry(edit.copy(crop = null))

    fun detectAgain() {
        val preview = upright ?: return
        viewModelScope.launch {
            val found = withContext(Dispatchers.Default) { EdgeDetector.detect(preview) }
            noEdgesFound = found == null
            if (found != null) changeGeometry(edit.copy(crop = found))
        }
    }

    fun rotateRight() = changeGeometry(edit.copy(rotation = Rotation.rotateClockwise(edit.rotation)))

    fun rotateLeft() = changeGeometry(edit.copy(rotation = Rotation.normalize(edit.rotation - 90)))

    fun currentCrop(): Quad = edit.crop ?: Quad.FULL

    /** Eraser strokes sit in page coordinates, so a new crop or rotation clears them. */
    private fun changeGeometry(next: PageEdit) {
        edit = if (edit.strokes.isNotEmpty()) next.copy(strokes = emptyList()) else next
        render()
    }

    fun cropDone() {
        edit = edit.copy(crop = edit.crop?.ordered()?.takeUnless { it.isFullFrame })
        mode = Mode.ENHANCE
        render(immediate = true)
    }

    // ---- Enhance ----

    fun setFilter(f: PageFilter) {
        edit = edit.copy(filter = f)
        render(immediate = true)
    }

    fun setBrightness(v: Int) {
        edit = edit.copy(tone = edit.tone.copy(brightness = v))
        render()
    }

    fun setContrast(v: Int) {
        edit = edit.copy(tone = edit.tone.copy(contrast = v))
        render()
    }

    // ---- Erase ----

    fun addStroke(points: List<Point>) {
        if (points.isEmpty()) return
        edit = edit.copy(strokes = edit.strokes + EraseStroke(eraseMode, brushSize, points))
        render(immediate = true)
    }

    fun undoStroke() {
        if (edit.strokes.isEmpty()) return
        edit = edit.copy(strokes = edit.strokes.dropLast(1))
        render(immediate = true)
    }

    private fun render(immediate: Boolean = false) {
        val preview = upright ?: return
        val snapshot = edit
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (!immediate) delay(120)
            val out = withContext(Dispatchers.Default) { PageRenderer.renderFromUpright(preview, snapshot, RENDER_SIDE) }
            if (snapshot == edit) rendered = out
        }
    }

    // ---- Save, next, discard ----

    /** Saves this page and moves on. Calls [onFinished] after the last page. */
    fun next(onFinished: (documentDeleted: Boolean) -> Unit) {
        val p = page ?: return onFinished(false)
        busy = true
        viewModelScope.launch {
            container.library.setPageEdit(p.id, edit)
            if (index + 1 < queue.size) load(index + 1) else onFinished(false)
        }
    }

    /**
     * Saves this page, then gives every remaining page automatic corners and this
     * page's filter, brightness and contrast.
     */
    fun processAll(onFinished: (Boolean) -> Unit) {
        val p = page ?: return onFinished(false)
        val look = edit
        busy = true
        viewModelScope.launch {
            container.library.setPageEdit(p.id, look)
            for (id in queue.drop(index + 1)) {
                val other = container.library.getPage(id) ?: continue
                val crop = other.crop?.let { Quad.parse(it) } ?: withContext(Dispatchers.Default) {
                    ImageDecoding.decodeThumbnail(File(other.imagePath), DETECT_SIDE, 0, null)?.let { EdgeDetector.detect(it) }
                }
                container.library.setPageEdit(
                    other.id,
                    other.edit().copy(crop = crop, filter = look.filter, tone = look.tone),
                )
            }
            onFinished(false)
        }
    }

    fun discard(onFinished: (Boolean) -> Unit) {
        val p = page ?: return onFinished(false)
        busy = true
        viewModelScope.launch {
            val deleted = container.library.removePages(documentId, listOf(p.id))
            queue.removeAt(index)
            when {
                deleted -> onFinished(true)
                queue.isEmpty() -> onFinished(false)
                else -> load(index.coerceAtMost(queue.lastIndex))
            }
        }
    }

    /** Removes every page in this editing run. */
    fun discardAll(onFinished: (Boolean) -> Unit) {
        busy = true
        viewModelScope.launch {
            val deleted = container.library.removePages(documentId, queue.toList())
            queue.clear()
            onFinished(deleted)
        }
    }

    private companion object {
        const val PREVIEW_SIDE = 1600
        const val RENDER_SIDE = 1400
        const val DETECT_SIDE = 1000
    }
}
