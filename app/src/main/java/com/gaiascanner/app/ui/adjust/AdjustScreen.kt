package com.gaiascanner.app.ui.adjust

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.CropRotation
import com.gaiascanner.app.core.Point
import com.gaiascanner.app.core.Quad
import com.gaiascanner.app.core.Rotation
import com.gaiascanner.app.data.db.PageEntity
import com.gaiascanner.app.engine.ImageDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Crop (four corners, straightened) and rotate, one page at a time. */
class AdjustViewModel(private val container: AppContainer, private val pageIds: List<Long>) : ViewModel() {
    var index by mutableIntStateOf(0)
        private set
    var page by mutableStateOf<PageEntity?>(null)
        private set

    /** Upright preview (EXIF applied) without crop or user rotation. */
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var crop by mutableStateOf(Quad.FULL)
        private set
    var rotation by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set

    val count: Int get() = pageIds.size
    val isLast: Boolean get() = index >= pageIds.lastIndex

    init {
        load(0)
    }

    private fun load(i: Int) {
        index = i
        loading = true
        viewModelScope.launch {
            val p = pageIds.getOrNull(i)?.let { container.library.getPage(it) }
            page = p
            preview = p?.let { withContext(Dispatchers.IO) { ImageDecoding.decodeThumbnail(File(it.imagePath), PREVIEW_SIDE, 0, null) } }
            crop = Quad.parse(p?.crop) ?: Quad.FULL
            rotation = p?.rotation ?: 0
            loading = false
        }
    }

    fun rotate() {
        rotation = Rotation.rotateClockwise(rotation)
    }

    fun reset() {
        crop = Quad.FULL
        rotation = 0
    }

    /** Moves corner [cornerIndex] to [displayPoint], a fraction of the image as shown. */
    fun moveCorner(cornerIndex: Int, displayPoint: Point) {
        crop = crop.withCorner(cornerIndex, CropRotation.toStored(displayPoint.clamped(), rotation))
    }

    fun apply(onFinished: () -> Unit) {
        val p = page
        viewModelScope.launch {
            if (p != null) container.library.setPageAdjust(p.id, crop.ordered(), rotation)
            next(onFinished)
        }
    }

    fun skip(onFinished: () -> Unit) = next(onFinished)

    private fun next(onFinished: () -> Unit) {
        if (index + 1 < pageIds.size) load(index + 1) else onFinished()
    }

    private companion object {
        const val PREVIEW_SIDE = 1600
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustScreen(pageIds: List<Long>, onFinished: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: AdjustViewModel = viewModel(key = "adjust-${pageIds.joinToString(",")}") { AdjustViewModel(container, pageIds) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adjust_title, vm.index + 1, vm.count)) },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (vm.count > 1) TextButton(onClick = { vm.skip(onFinished) }) { Text(stringResource(R.string.action_skip)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = vm.preview
                when {
                    vm.loading -> CircularProgressIndicator()
                    bmp == null -> Text(stringResource(R.string.error_unsupported_format), Modifier.padding(24.dp))
                    else -> CropEditor(bmp, vm.crop, vm.rotation, vm::moveCorner)
                }
            }
            Text(
                stringResource(R.string.adjust_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = vm::rotate, enabled = !vm.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_rotate))
                    }
                    TextButton(onClick = vm::reset, enabled = !vm.loading) { Text(stringResource(R.string.action_reset)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.apply(onFinished) }, enabled = !vm.loading) {
                        Text(stringResource(if (vm.isLast) R.string.action_done else R.string.action_next))
                    }
                }
            }
        }
    }
}

/** The image as it will look after rotation, with four draggable corner handles. */
@Composable
private fun CropEditor(preview: Bitmap, crop: Quad, rotation: Int, onMoveCorner: (Int, Point) -> Unit) {
    val image: ImageBitmap = remember(preview, rotation) { rotated(preview, rotation).asImageBitmap() }
    val currentCrop by rememberUpdatedState(crop)
    val currentRotation by rememberUpdatedState(rotation)
    var active by remember { mutableIntStateOf(-1) }
    val accent = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .padding(28.dp)
            .pointerInput(image) {
                val grab = 48.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        val rect = fitRect(image, Size(size.width.toFloat(), size.height.toFloat()))
                        val handles = displayCorners(currentCrop, currentRotation, rect)
                        val nearest = handles.indices.minBy { hypot(handles[it].x - start.x, handles[it].y - start.y) }
                        val distance = hypot(handles[nearest].x - start.x, handles[nearest].y - start.y)
                        active = if (distance <= grab) nearest else -1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, _ ->
                        if (active >= 0) {
                            change.consume()
                            val rect = fitRect(image, Size(size.width.toFloat(), size.height.toFloat()))
                            val p = Point((change.position.x - rect.left) / rect.width, (change.position.y - rect.top) / rect.height)
                            onMoveCorner(active, p)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val rect = fitRect(image, size)
            drawImage(
                image = image,
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
            )
            val pts = displayCorners(crop, rotation, rect)
            val outline = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            // Darken what will be cut away.
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(rect)
                addPath(outline)
            }
            drawPath(outside, Color.Black.copy(alpha = 0.5f))
            drawPath(outline, accent, style = Stroke(width = 2.dp.toPx()))
            pts.forEachIndexed { i, p ->
                val r = if (i == active) 16.dp.toPx() else 12.dp.toPx()
                drawCircle(Color.White, radius = r, center = p)
                drawCircle(accent, radius = r, center = p, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

/** Where the image sits inside [area] when scaled to fit, centered. */
private fun fitRect(image: ImageBitmap, area: Size): Rect {
    val scale = min(area.width / image.width, area.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val left = (area.width - w) / 2f
    val top = (area.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

private fun displayCorners(crop: Quad, rotation: Int, rect: Rect): List<Offset> =
    crop.corners.map { CropRotation.toDisplay(it, rotation) }
        .map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }

private fun rotated(bitmap: Bitmap, degrees: Int): Bitmap {
    val d = Rotation.normalize(degrees)
    if (d == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(d.toFloat()) }, true)
}
