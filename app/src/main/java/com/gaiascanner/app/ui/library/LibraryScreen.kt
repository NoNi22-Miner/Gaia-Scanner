package com.gaiascanner.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.ui.components.Thumbnail
import com.gaiascanner.app.ui.formatSize

private val IMAGE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    onOpenJobs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: LibraryViewModel = viewModel { LibraryViewModel(container) }
    val documents by vm.documents.collectAsStateWithLifecycle()
    val activeJobs by vm.activeJobs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importPdfs(uris)
    }
    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.importImages(uris)
    }
    val browseImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importImages(uris)
    }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenJobs) {
                        BadgedBox(badge = { if (activeJobs > 0) Badge { Text(activeJobs.toString()) } }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.jobs_title))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { menuOpen = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add)) },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_pdfs)) }, onClick = {
                        menuOpen = false
                        pickPdfs.launch(arrayOf("application/pdf"))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_gallery)) }, onClick = {
                        menuOpen = false
                        pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_image_files)) }, onClick = {
                        menuOpen = false
                        browseImages.launch(IMAGE_MIME_TYPES)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.take_photo)) }, onClick = {
                        menuOpen = false
                        onOpenCamera()
                    })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val docs = documents
        when {
            docs == null -> Unit
            docs.isEmpty() -> EmptyLibrary(Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(docs, key = { it.id }) { doc ->
                    DocumentRow(doc, onClick = { onOpenDocument(doc.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DocumentRow(doc: DocumentEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val pages = pluralStringResource(R.plurals.pages, doc.pageCount, doc.pageCount)
    val kind = stringResource(if (doc.kind == DocumentKind.PDF) R.string.kind_pdf else R.string.kind_images)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(doc.name, maxLines = 2) },
        supportingContent = {
            val details = if (doc.locked) {
                stringResource(R.string.document_locked_short)
            } else {
                "$pages · ${formatSize(context, doc.sizeBytes)}"
            }
            Text("$kind · $details")
        },
        leadingContent = { DocumentThumbnail(doc) },
        trailingContent = if (doc.locked) {
            { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.document_locked_short)) }
        } else {
            null
        },
    )
}

@Composable
private fun DocumentThumbnail(doc: DocumentEntity) {
    val container = LocalContext.current.appContainer
    if (doc.kind == DocumentKind.PDF) {
        if (!doc.locked) Thumbnail(doc.localPath, isPdf = true, rotation = 0, size = 56.dp)
    } else {
        val firstPage by androidx.compose.runtime.produceState<Pair<String, Int>?>(null, doc.id, doc.modifiedAt) {
            value = container.library.getPages(doc.id).firstOrNull()?.let { it.imagePath to it.rotation }
        }
        firstPage?.let { (path, rotation) -> Thumbnail(path, isPdf = false, rotation = rotation, size = 56.dp) }
    }
}
