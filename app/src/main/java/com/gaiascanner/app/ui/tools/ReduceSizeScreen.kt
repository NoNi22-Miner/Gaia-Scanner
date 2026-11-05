package com.gaiascanner.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.ReducePreset
import com.gaiascanner.app.core.WatermarkLayout
import com.gaiascanner.app.ui.formatSize
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReduceSizeScreen(documentId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: ToolsViewModel = viewModel(key = "reduce-$documentId") { ToolsViewModel(container, documentId) }
    val doc by vm.document.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var preset by rememberSaveable { mutableStateOf(ReducePreset.MEDIUM) }
    LaunchedEffect(progress) { if (progress == ToolsViewModel.Progress.Saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_reduce)) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(d.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reduce_current_size, formatSize(context, d.sizeBytes)))
            val working = progress is ToolsViewModel.Progress.Working
            listOf(
                Triple(ReducePreset.HIGH, R.string.preset_high, R.string.preset_high_body),
                Triple(ReducePreset.MEDIUM, R.string.preset_medium, R.string.preset_medium_body),
                Triple(ReducePreset.SMALL, R.string.preset_small, R.string.preset_small_body),
            ).forEach { (p, title, body) ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = preset == p,
                        enabled = !working,
                        role = Role.RadioButton,
                        onClick = { preset = p; vm.discard() },
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = preset == p, onClick = null, enabled = !working)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(stringResource(R.string.tool_raster_note), style = MaterialTheme.typography.bodySmall)

            when (val p = progress) {
                ToolsViewModel.Progress.Idle, ToolsViewModel.Progress.Saved -> Button(
                    onClick = { vm.reduce(preset) },
                    enabled = !d.locked,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_make_smaller_copy)) }
                is ToolsViewModel.Progress.Working -> {
                    LinearProgressIndicator(progress = { p.done / p.total.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.tool_working, p.done, p.total))
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                is ToolsViewModel.Progress.Ready -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val saved = WatermarkLayout.savedFraction(p.originalBytes, p.newBytes)
                        val before = formatSize(context, p.originalBytes)
                        val after = formatSize(context, p.newBytes)
                        Text(
                            if (saved > 0) {
                                stringResource(R.string.reduce_result, before, after, (saved * 100).roundToInt())
                            } else {
                                stringResource(R.string.reduce_not_smaller, before, after)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.keepReduced() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_copy)) }
                            OutlinedButton(onClick = vm::discard, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_discard_copy))
                            }
                        }
                    }
                }
                is ToolsViewModel.Progress.Failed -> {
                    Text(p.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.reduce(preset) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_make_smaller_copy))
                    }
                }
            }
        }
    }
}
