package com.gaiascanner.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.BuildConfigInfo
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.data.AppSettings
import com.gaiascanner.app.ui.components.ChoiceRow
import com.gaiascanner.app.ui.marginLabel
import com.gaiascanner.app.ui.pageSizeLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { container.settings.update(transform) }

    /** Keeps read and write access to the picked folder across restarts. */
    fun setOutputFolder(context: Context, uri: Uri) = viewModelScope.launch {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            return@launch
        }
        val old = settings.value?.outputFolderUri
        container.settings.update { it.copy(outputFolderUri = uri.toString()) }
        if (old != null && old != uri.toString()) release(context, old)
    }

    fun clearOutputFolder(context: Context) = viewModelScope.launch {
        val old = settings.value?.outputFolderUri ?: return@launch
        container.settings.update { it.copy(outputFolderUri = null) }
        release(context, old)
    }

    private fun release(context: Context, uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val current by vm.settings.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setOutputFolder(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val s = current ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_pdf_to_images), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(stringResource(R.string.setting_format), ImageFormat.entries, s.imageFormat, { it.name }, { f ->
                vm.update { it.copy(imageFormat = f) }
            })
            ChoiceRow(stringResource(R.string.setting_dpi), ConvertDefaults.DPI_CHOICES, s.dpi, { it.toString() }, { d ->
                vm.update { it.copy(dpi = d) }
            })
            var quality by remember(s.jpegQuality) { mutableFloatStateOf(s.jpegQuality.toFloat()) }
            Text(stringResource(R.string.setting_quality_value, quality.roundToInt()), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = quality,
                onValueChange = { quality = it },
                onValueChangeFinished = { vm.update { it.copy(jpegQuality = quality.roundToInt()) } },
                valueRange = 50f..100f,
                steps = 9,
            )

            HorizontalDivider()
            Text(stringResource(R.string.settings_images_to_pdf), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(stringResource(R.string.setting_page_size), PageSizeOption.entries, s.pageSize, { pageSizeLabel(it) }, { p ->
                vm.update { it.copy(pageSize = p) }
            })
            ChoiceRow(stringResource(R.string.setting_margins), MarginOption.entries, s.margin, { marginLabel(it) }, { m ->
                vm.update { it.copy(margin = m) }
            })

            HorizontalDivider()
            Text(stringResource(R.string.setting_output_folder), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.setting_output_folder_body), style = MaterialTheme.typography.bodySmall)
            val folderName = s.outputFolderUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }
            Text(folderName ?: stringResource(R.string.setting_output_folder_none))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.action_choose_folder)) }
                if (s.outputFolderUri != null) {
                    TextButton(onClick = { vm.clearOutputFolder(context) }) { Text(stringResource(R.string.action_clear)) }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.version, BuildConfigInfo.versionName(context)), style = MaterialTheme.typography.bodySmall)
        }
    }
}
