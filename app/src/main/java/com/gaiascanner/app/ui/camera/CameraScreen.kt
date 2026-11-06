package com.gaiascanner.app.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.ui.components.Thumbnail
import com.gaiascanner.app.ui.importSummary
import kotlinx.coroutines.launch
import java.io.File

/**
 * Photos taken in the app's own camera screen. Phone camera apps can stamp a
 * watermark or time mark on photos; capturing with CameraX avoids that.
 */
class CameraViewModel(private val container: AppContainer, private val documentId: Long?) : ViewModel() {
    enum class Mode { SINGLE, BATCH }

    val captured = mutableStateListOf<File>()
    var mode by mutableStateOf(Mode.SINGLE)
    var flashMode by mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)
    var shooting by mutableStateOf(false)
    var saving by mutableStateOf(false)
        private set

    fun newFile(): File = container.files.newCameraFile()

    /** Imports the photos in shooting order. Calls [onSaved] with the document and the new page ids. */
    fun save(onSaved: (Long, List<Long>) -> Unit, onFailed: (String) -> Unit) {
        if (saving || captured.isEmpty()) return
        saving = true
        val photos = captured.toList()
        viewModelScope.launch {
            val result = container.library.importPhotos(photos, documentId)
            captured.clear()
            saving = false
            val doc = result.documentIds.firstOrNull()
            if (doc != null) onSaved(doc, result.pageIds) else onFailed(importSummary(container.appContext, result))
        }
    }

    fun discard() {
        captured.forEach { it.delete() }
        captured.clear()
    }

    override fun onCleared() {
        // Photos left here were not saved, for example the user closed the camera.
        if (!saving) discard()
    }
}

@Composable
fun CameraScreen(documentId: Long?, onSaved: (Long, List<Long>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: CameraViewModel = viewModel(key = "camera-$documentId") { CameraViewModel(container, documentId) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) askPermission.launch(Manifest.permission.CAMERA) }

    var confirmDiscard by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (vm.captured.isNotEmpty()) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = close)

    val snackbar = remember { SnackbarHostState() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraContent(vm, snackbar, onSaved, close)
        } else {
            PermissionNeeded(
                onAllow = { askPermission.launch(Manifest.permission.CAMERA) },
                onSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                onClose = onClose,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_photos_title)) },
            text = { Text(pluralStringResource(R.plurals.discard_photos_body, vm.captured.size, vm.captured.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discard()
                    onClose()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CameraContent(
    vm: CameraViewModel,
    snackbar: SnackbarHostState,
    onSaved: (Long, List<Long>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var cameraError by remember { mutableStateOf(false) }
    val photoFailed = stringResource(R.string.photo_failed)

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                cameraError = true
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) {
                try {
                    future.get().unbindAll()
                } catch (_: Exception) {
                }
            }
        }
    }

    // Saves photos in the orientation the phone is held (as in the CameraX docs).
    DisposableEffect(imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    LaunchedEffect(vm.flashMode) { imageCapture.flashMode = vm.flashMode }

    val failed: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val shoot: () -> Unit = shoot@{
        if (vm.shooting || vm.saving) return@shoot
        vm.shooting = true
        val file = vm.newFile()
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    vm.shooting = false
                    vm.captured += file
                    if (vm.mode == CameraViewModel.Mode.SINGLE) vm.save(onSaved, failed)
                }

                override fun onError(exception: ImageCaptureException) {
                    vm.shooting = false
                    file.delete()
                    failed(photoFailed)
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopControls(vm, onClose)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (cameraError) {
                Text(stringResource(R.string.no_camera), color = Color.White, modifier = Modifier.padding(24.dp))
            }
            if (vm.saving) CircularProgressIndicator()
        }
        BottomControls(vm, onShoot = shoot, onDone = { vm.save(onSaved, failed) })
    }
}

@Composable
private fun TopControls(vm: CameraViewModel, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
        }
        val flashLabel = stringResource(
            when (vm.flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> R.string.flash_auto
                ImageCapture.FLASH_MODE_ON -> R.string.flash_on
                else -> R.string.flash_off
            },
        )
        TextButton(onClick = {
            vm.flashMode = when (vm.flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
        }) { Text(flashLabel, color = Color.White) }
        Spacer(Modifier.weight(1f))
        ModeChip(stringResource(R.string.mode_single), vm.mode == CameraViewModel.Mode.SINGLE, enabled = vm.captured.isEmpty()) {
            vm.mode = CameraViewModel.Mode.SINGLE
        }
        ModeChip(stringResource(R.string.mode_batch), vm.mode == CameraViewModel.Mode.BATCH, enabled = true) {
            vm.mode = CameraViewModel.Mode.BATCH
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = Color.White,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun BottomControls(vm: CameraViewModel, onShoot: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Last photo and count (batch).
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
            vm.captured.lastOrNull()?.let { last ->
                Box {
                    Thumbnail(last.path, isPdf = false, size = 56.dp)
                    Text(
                        vm.captured.size.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val shutterLabel = stringResource(R.string.action_shutter)
        Box(
            Modifier
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .background(if (vm.shooting) Color.Gray else Color.White, CircleShape)
                .clickable(enabled = !vm.shooting && !vm.saving, onClickLabel = shutterLabel, onClick = onShoot),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            if (vm.mode == CameraViewModel.Mode.BATCH) {
                Button(onClick = onDone, enabled = vm.captured.isNotEmpty() && !vm.saving) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun PermissionNeeded(onAllow: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.camera_permission_needed), color = Color.White)
        Button(onClick = onAllow) { Text(stringResource(R.string.action_allow_camera)) }
        OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.action_open_settings), color = Color.White) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_close), color = Color.White) }
    }
}
