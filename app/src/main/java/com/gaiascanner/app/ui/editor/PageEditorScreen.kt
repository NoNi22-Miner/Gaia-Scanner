package com.gaiascanner.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.CropRotation
import com.gaiascanner.app.core.EraseMode
import com.gaiascanner.app.core.PageFilter
import com.gaiascanner.app.core.Point
import com.gaiascanner.app.core.Quad
import com.gaiascanner.app.core.Rotation
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    documentId: Long,
    pageIds: List<Long>,
    startMode: PageEditorViewModel.Mode,
    autoDetect: Boolean,
    onFinished: (documentDeleted: Boolean) -> Unit,
) {
    val container = LocalContext.current.appContainer
    val vm: PageEditorViewModel = viewModel(key = "edit-$documentId-${pageIds.joinToString(",")}-$startMode") {
        PageEditorViewModel(container, documentId, pageIds, startMode, autoDetect)
    }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscardAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.page_of, vm.index + 1, vm.count)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (vm.mode == PageEditorViewModel.Mode.ERASE) {
                            vm.mode = PageEditorViewModel.Mode.ENHANCE
                        } else {
                            onFinished(false)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !vm.busy) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (vm.count > 1 && !vm.isLast) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_process_all)) }, onClick = {
                                    menu = false
                                    vm.processAll(onFinished)
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_discard_page)) }, onClick = {
                                menu = false
                                vm.discard(onFinished)
                            })
                            if (vm.count > 1) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_discard_all)) }, onClick = {
                                    menu = false
                                    confirmDiscardAll = true
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.mode == PageEditorViewModel.Mode.ENHANCE) FilterRow(vm.edit.filter, vm::setFilter)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val upright = vm.upright
                when {
                    vm.page == null && !vm.busy -> Text(stringResource(R.string.error_missing_file))
                    upright == null -> CircularProgressIndicator()
                    vm.mode == PageEditorViewModel.Mode.CROP -> CropEditor(upright, vm.currentCrop(), vm.edit.rotation, vm::moveCorner, vm::moveEdge)
                    vm.mode == PageEditorViewModel.Mode.ERASE -> EraseCanvas(vm.rendered, vm.brushSize, vm::addStroke)
                    else -> vm.rendered?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                        )
                    } ?: CircularProgressIndicator()
                }
                if (vm.busy && upright != null) CircularProgressIndicator()
            }
            val hint = when {
                vm.mode == PageEditorViewModel.Mode.CROP && vm.noEdgesFound -> R.string.no_edges_found
                vm.mode == PageEditorViewModel.Mode.CROP -> R.string.edit_hint_crop
                vm.mode == PageEditorViewModel.Mode.ERASE -> R.string.edit_hint_erase
                else -> null
            }
            hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (vm.mode == PageEditorViewModel.Mode.ENHANCE && vm.showTone) TonePanel(vm)
            if (vm.mode == PageEditorViewModel.Mode.ERASE) ErasePanel(vm)
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (vm.mode) {
                        PageEditorViewModel.Mode.CROP -> {
                            TextButton(onClick = vm::detectAgain, enabled = !vm.busy) { Text(stringResource(R.string.action_auto)) }
                            TextButton(onClick = vm::fullFrame, enabled = !vm.busy) { Text(stringResource(R.string.action_full_frame)) }
                            RotateButtons(vm)
                            Spacer(Modifier.weight(1f))
                            DoneButton(enabled = !vm.busy, onClick = vm::cropDone)
                        }
                        PageEditorViewModel.Mode.ENHANCE -> {
                            TextButton(onClick = { vm.mode = PageEditorViewModel.Mode.CROP }) { Text(stringResource(R.string.action_crop)) }
                            RotateButtons(vm)
                            TextButton(onClick = { vm.mode = PageEditorViewModel.Mode.ERASE }) { Text(stringResource(R.string.action_erase)) }
                            TextButton(onClick = { vm.showTone = !vm.showTone }) { Text(stringResource(R.string.action_tone)) }
                            Spacer(Modifier.weight(1f))
                            DoneButton(enabled = !vm.busy) { vm.next(onFinished) }
                        }
                        PageEditorViewModel.Mode.ERASE -> {
                            TextButton(onClick = vm::undoStroke, enabled = vm.edit.strokes.isNotEmpty()) { Text(stringResource(R.string.action_undo)) }
                            Spacer(Modifier.weight(1f))
                            DoneButton(enabled = true) { vm.mode = PageEditorViewModel.Mode.ENHANCE }
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscardAll) {
        AlertDialog(
            onDismissRequest = { confirmDiscardAll = false },
            title = { Text(stringResource(R.string.action_discard_all)) },
            text = { Text(pluralStringResource(R.plurals.discard_all_body, vm.count, vm.count)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscardAll = false
                    vm.discardAll(onFinished)
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardAll = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DoneButton(enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_done))
    }
}

@Composable
private fun RotateButtons(vm: PageEditorViewModel) {
    // Core icons have one circular arrow; mirrored, it reads as "rotate left".
    IconButton(onClick = vm::rotateLeft, enabled = !vm.busy) {
        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_rotate_left), modifier = Modifier.graphicsLayer(scaleX = -1f))
    }
    IconButton(onClick = vm::rotateRight, enabled = !vm.busy) {
        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_rotate_right))
    }
}

@Composable
private fun FilterRow(selected: PageFilter, onSelect: (PageFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageFilter.entries.forEach { f ->
            FilterChip(selected = f == selected, onClick = { onSelect(f) }, label = { Text(stringResource(filterLabel(f))) })
        }
    }
}

private fun filterLabel(f: PageFilter): Int = when (f) {
    PageFilter.ORIGINAL -> R.string.filter_original
    PageFilter.PHOTO -> R.string.filter_photo
    PageFilter.CLEAR -> R.string.filter_clear
    PageFilter.DOCS -> R.string.filter_docs
    PageFilter.COLOR -> R.string.filter_color
}

@Composable
private fun TonePanel(vm: PageEditorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.tone_brightness, vm.edit.tone.brightness), style = MaterialTheme.typography.labelLarge)
        Slider(value = vm.edit.tone.brightness.toFloat(), onValueChange = { vm.setBrightness(it.roundToInt()) }, valueRange = -100f..100f)
        Text(stringResource(R.string.tone_contrast, vm.edit.tone.contrast), style = MaterialTheme.typography.labelLarge)
        Slider(value = vm.edit.tone.contrast.toFloat(), onValueChange = { vm.setContrast(it.roundToInt()) }, valueRange = -100f..100f)
    }
}

@Composable
private fun ErasePanel(vm: PageEditorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.eraseMode == EraseMode.WHITE, onClick = { vm.eraseMode = EraseMode.WHITE }, label = { Text(stringResource(R.string.erase_white)) })
            FilterChip(selected = vm.eraseMode == EraseMode.FILL, onClick = { vm.eraseMode = EraseMode.FILL }, label = { Text(stringResource(R.string.erase_fill)) })
        }
        Text(stringResource(R.string.erase_size), style = MaterialTheme.typography.labelLarge)
        Slider(value = vm.brushSize, onValueChange = { vm.brushSize = it }, valueRange = 0.01f..0.12f)
    }
}

/** The photo as it will be turned, with 4 corner handles and 4 side handles. */
@Composable
private fun CropEditor(
    upright: Bitmap,
    crop: Quad,
    rotation: Int,
    onMoveCorner: (Int, Point) -> Unit,
    onMoveEdge: (Int, Float, Float) -> Unit,
) {
    val image: ImageBitmap = remember(upright, rotation) { rotated(upright, rotation).asImageBitmap() }
    val currentCrop by rememberUpdatedState(crop)
    val currentRotation by rememberUpdatedState(rotation)
    var active by remember { mutableIntStateOf(-1) } // 0-3 corners, 4-7 sides
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
                        val handles = handlePositions(currentCrop, currentRotation, rect)
                        val nearest = handles.indices.minBy { hypot(handles[it].x - start.x, handles[it].y - start.y) }
                        active = if (hypot(handles[nearest].x - start.x, handles[nearest].y - start.y) <= grab) nearest else -1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, amount ->
                        if (active < 0) return@detectDragGestures
                        change.consume()
                        val rect = fitRect(image, Size(size.width.toFloat(), size.height.toFloat()))
                        if (active < 4) {
                            onMoveCorner(active, Point((change.position.x - rect.left) / rect.width, (change.position.y - rect.top) / rect.height))
                        } else {
                            onMoveEdge(active - 4, amount.x / rect.width, amount.y / rect.height)
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
            val handles = handlePositions(crop, rotation, rect)
            val outline = Path().apply {
                moveTo(handles[0].x, handles[0].y)
                for (i in 1..3) lineTo(handles[i].x, handles[i].y)
                close()
            }
            drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(rect); addPath(outline) }, Color.Black.copy(alpha = 0.5f))
            drawPath(outline, accent, style = Stroke(width = 2.dp.toPx()))
            handles.forEachIndexed { i, p ->
                val r = (if (i == active) 16.dp else if (i < 4) 12.dp else 9.dp).toPx()
                drawCircle(Color.White, radius = r, center = p)
                drawCircle(accent, radius = r, center = p, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

/** Corners 0-3, then the middle of each side (side i runs from corner i to corner i+1). */
private fun handlePositions(crop: Quad, rotation: Int, rect: Rect): List<Offset> {
    val corners = crop.corners.map { CropRotation.toDisplay(it, rotation) }
        .map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
    val sides = (0..3).map { (corners[it] + corners[(it + 1) % 4]) / 2f }
    return corners + sides
}

/** Brush over the page; each finished drag becomes one eraser stroke. */
@Composable
private fun EraseCanvas(rendered: Bitmap?, brushSize: Float, onStroke: (List<Point>) -> Unit) {
    if (rendered == null) {
        CircularProgressIndicator()
        return
    }
    val image = remember(rendered) { rendered.asImageBitmap() }
    val current = remember { mutableStateListOf<Offset>() }
    val brush by rememberUpdatedState(brushSize)
    Box(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .pointerInput(image) {
                detectDragGestures(
                    onDragStart = { current.clear(); current += it },
                    onDrag = { change, _ ->
                        change.consume()
                        current += change.position
                    },
                    onDragEnd = {
                        val rect = fitRect(image, Size(size.width.toFloat(), size.height.toFloat()))
                        val points = current.map { Point((it.x - rect.left) / rect.width, (it.y - rect.top) / rect.height).clamped() }
                        current.clear()
                        onStroke(points)
                    },
                    onDragCancel = { current.clear() },
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
            if (current.size > 1) {
                val path = Path().apply {
                    moveTo(current[0].x, current[0].y)
                    current.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path,
                    Color(0x8000BCD4),
                    style = Stroke(width = brush * rect.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun fitRect(image: ImageBitmap, area: Size): Rect {
    val scale = min(area.width / image.width, area.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val left = (area.width - w) / 2f
    val top = (area.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

private fun rotated(bitmap: Bitmap, degrees: Int): Bitmap {
    val d = Rotation.normalize(degrees)
    if (d == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(d.toFloat()) }, true)
}

