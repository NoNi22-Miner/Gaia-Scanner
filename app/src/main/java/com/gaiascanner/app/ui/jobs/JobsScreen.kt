package com.gaiascanner.app.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.JobStatus
import com.gaiascanner.app.core.JobType
import com.gaiascanner.app.data.db.JobEntity
import com.gaiascanner.app.data.db.resultIds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JobsViewModel(private val container: AppContainer) : ViewModel() {
    val jobs: StateFlow<List<JobEntity>?> = container.jobs.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cancel(id: Long) = viewModelScope.launch { container.jobs.cancel(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(onBack: () -> Unit, onOpenDocument: (Long) -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: JobsViewModel = viewModel { JobsViewModel(container) }
    val jobs by vm.jobs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jobs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val list = jobs
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.jobs_empty))
            }
        } else if (list != null) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { job ->
                    JobCard(job, onCancel = { vm.cancel(job.id) }, onOpen = onOpenDocument)
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobEntity, onCancel: () -> Unit, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (job.type == JobType.PDF_TO_IMAGES) R.string.job_pdf_to_images else R.string.job_images_to_pdf),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(statusLabel(job.status)), style = MaterialTheme.typography.labelLarge)
            }
            if (job.status == JobStatus.RUNNING || job.status == JobStatus.CANCEL_REQUESTED) {
                LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            job.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            job.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            job.failures?.let {
                Text(stringResource(R.string.job_failures), style = MaterialTheme.typography.labelLarge)
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            job.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.status == JobStatus.QUEUED || job.status == JobStatus.RUNNING) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
                job.resultIds().forEachIndexed { index, id ->
                    if (index < 3) {
                        TextButton(onClick = { onOpen(id) }) {
                            Text(stringResource(R.string.action_open_result, index + 1))
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: JobStatus): Int = when (status) {
    JobStatus.QUEUED -> R.string.status_queued
    JobStatus.RUNNING -> R.string.status_running
    JobStatus.CANCEL_REQUESTED -> R.string.status_cancelling
    JobStatus.DONE -> R.string.status_done
    JobStatus.FAILED -> R.string.status_failed
    JobStatus.CANCELLED -> R.string.status_cancelled
}
