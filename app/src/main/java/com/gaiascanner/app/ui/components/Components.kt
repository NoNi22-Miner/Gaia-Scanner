package com.gaiascanner.app.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gaiascanner.app.appContainer
import java.io.File

/** Thumbnail of an image file, or of the first page of a PDF when [isPdf]. */
@Composable
fun Thumbnail(path: String, isPdf: Boolean, rotation: Int, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<android.graphics.Bitmap?>(null, path, rotation, px) {
        val cache = context.appContainer.thumbnails
        value = if (isPdf) cache.pdf(path, px) else cache.image(path, rotation, px)
    }
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** A labelled row of single-choice chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

/** Opens the phone's camera app. Calls [onPhoto] with the saved file on success. */
class CameraLauncher(private val launch: () -> Boolean) {
    /** Returns false if no camera app is installed. */
    fun takePhoto(): Boolean = launch()
}

@Composable
fun rememberCameraLauncher(onPhoto: (File) -> Unit): CameraLauncher {
    val context = LocalContext.current
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (file != null) {
            if (saved && file.length() > 0) onPhoto(file) else file.delete()
        }
    }
    return remember(launcher) {
        CameraLauncher {
            if (!hasCameraApp(context)) return@CameraLauncher false
            val file = context.appContainer.files.newCameraFile()
            pendingPath = file.path
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            try {
                launcher.launch(uri)
                true
            } catch (e: ActivityNotFoundException) {
                pendingPath = null
                false
            }
        }
    }
}

private fun hasCameraApp(context: Context): Boolean =
    Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null

/** Asks once for the notification permission (Android 13+), so job progress can show. */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
