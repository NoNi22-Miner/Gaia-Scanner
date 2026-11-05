package com.gaiascanner.app.ui.tools

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.WatermarkStyle
import kotlin.math.roundToInt

private val COLOR_LABELS = mapOf(
    0x808080 to R.string.color_gray,
    0x000000 to R.string.color_black,
    0xD32F2F to R.string.color_red,
    0x1565C0 to R.string.color_blue,
    0xFFFFFF to R.string.color_white,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(documentId: Long, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: ToolsViewModel = viewModel(key = "watermark-$documentId") { ToolsViewModel(container, documentId) }
    val doc by vm.document.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val style by vm.style.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    LaunchedEffect(progress) { if (progress == ToolsViewModel.Progress.Saved) onBack() }
    val working = progress is ToolsViewModel.Progress.Working
    val set: (WatermarkStyle) -> Unit = { if (!working) vm.setStyle(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_watermark)) },
                navigationIcon = {
                    IconButton(onClick = { vm.cancel(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val d = doc ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                preview?.let {
                    Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            OutlinedTextField(
                value = style.text,
                onValueChange = { set(style.copy(text = it.take(WatermarkStyle.MAX_TEXT))) },
                label = { Text(stringResource(R.string.watermark_text)) },
                placeholder = { Text(stringResource(R.string.watermark_text_hint)) },
                singleLine = true,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.watermark_size, style.sizePercent), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = style.sizePercent.toFloat(),
                onValueChange = { set(style.copy(sizePercent = it.roundToInt())) },
                valueRange = WatermarkStyle.MIN_SIZE.toFloat()..WatermarkStyle.MAX_SIZE.toFloat(),
                enabled = !working,
            )
            Text(stringResource(R.string.watermark_opacity, style.opacity), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = style.opacity.toFloat(),
                onValueChange = { set(style.copy(opacity = it.roundToInt())) },
                valueRange = WatermarkStyle.MIN_OPACITY.toFloat()..100f,
                enabled = !working,
            )
            Text(stringResource(R.string.watermark_angle, style.angle), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = style.angle.toFloat(),
                onValueChange = { set(style.copy(angle = it.roundToInt())) },
                valueRange = -90f..90f,
                enabled = !working,
            )
            Text(stringResource(R.string.watermark_color), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WatermarkStyle.COLORS.forEach { rgb ->
                    val selected = style.color == rgb
                    val label = stringResource(COLOR_LABELS[rgb] ?: R.string.watermark_color)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000 or rgb.toLong()))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable(enabled = !working) { set(style.copy(color = rgb)) }
                            .semantics { contentDescription = label },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !style.tiled,
                    onClick = { set(style.copy(tiled = false)) },
                    label = { Text(stringResource(R.string.watermark_single)) },
                    enabled = !working,
                )
                FilterChip(
                    selected = style.tiled,
                    onClick = { set(style.copy(tiled = true)) },
                    label = { Text(stringResource(R.string.watermark_tiled)) },
                    enabled = !working,
                )
            }
            Text(
                stringResource(if (d.kind == DocumentKind.PDF) R.string.tool_raster_note else R.string.watermark_images_note),
                style = MaterialTheme.typography.bodySmall,
            )
            when (val p = progress) {
                is ToolsViewModel.Progress.Working -> {
                    LinearProgressIndicator(progress = { p.done / p.total.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.tool_working, p.done, p.total))
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
                }
                else -> {
                    if (p is ToolsViewModel.Progress.Failed) Text(p.message, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = vm::watermark,
                        enabled = style.text.isNotBlank() && !d.locked,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_save_watermarked)) }
                }
            }
        }
    }
}
