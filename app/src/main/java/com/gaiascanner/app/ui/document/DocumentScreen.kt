package com.gaiascanner.app.ui.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.ConvertDefaults
import com.gaiascanner.app.core.CropRotation
import com.gaiascanner.app.core.Quad
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.data.AppSettings
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.PageEntity
import com.gaiascanner.app.data.db.edit
import com.gaiascanner.app.ui.components.ChoiceRow
import com.gaiascanner.app.ui.components.Thumbnail
import com.gaiascanner.app.ui.components.rememberNotificationPermissionRequest
import com.gaiascanner.app.ui.formatSize
import com.gaiascanner.app.ui.marginLabel
import com.gaiascanner.app.ui.pageSizeLabel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val IMAGE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    documentId: Long,
    onBack: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenCamera: (Long) -> Unit,
    onAdjustPage: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: DocumentViewModel = viewModel(key = "document-$documentId") { DocumentViewModel(container, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewJobs = stringResource(R.string.action_view_jobs)
    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(msg.text, actionLabel = if (msg.offerJobs) viewJobs else null)
            if (result == SnackbarResult.ActionPerformed) onOpenJobs()
        }
    }

    val doc = state.document
    if (state.loaded && doc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showConvert by rememberSaveable { mutableStateOf(false) }
    var showMakePdf by rememberSaveable { mutableStateOf(false) }
    var showUnlock by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberNotificationPermissionRequest()

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveTo(uri)
    }
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.saveToFolder(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (doc != null && !doc.locked) {
                        IconButton(onClick = {
                            scope.launch { vm.shareIntent()?.let { context.startActivity(it) } }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_move_to_trash))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (doc == null) return@Scaffold
        when (doc.kind) {
            DocumentKind.PDF -> PdfContent(
                doc = doc,
                canUnlock = vm.canUnlock,
                modifier = Modifier.padding(padding),
                onConvert = { showConvert = true },
                onSave = { savePdf.launch("${doc.name}.pdf") },
                onUnlock = { showUnlock = true },
            )
            DocumentKind.IMAGES -> ImagesContent(
                pages = pages,
                padding = padding,
                onMove = vm::movePage,
                onAdjust = onAdjustPage,
                onRemove = vm::removePage,
                onMakePdf = { showMakePdf = true },
                onSave = { saveFolder.launch(null) },
                onAddImages = vm::addImages,
                onAddBlank = { vm.addBlankPage(it) },
                onOpenCamera = { onOpenCamera(doc.id) },
            )
        }
    }

    if (confirmDelete && doc != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, doc.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showConvert) {
        ConvertToImagesDialog(
            defaults = settings,
            onDismiss = { showConvert = false },
            onStart = { format, dpi, quality ->
                showConvert = false
                askNotifications()
                vm.convertToImages(format, dpi, quality)
            },
        )
    }
    if (showMakePdf) {
        MakePdfDialog(
            defaults = settings,
            onDismiss = { showMakePdf = false },
            onStart = { size, margin ->
                showMakePdf = false
                askNotifications()
                vm.makePdf(size, margin)
            },
        )
    }
    if (showUnlock) {
        UnlockDialog(
            onDismiss = { showUnlock = false },
            onUnlock = {
                showUnlock = false
                vm.unlock(it)
            },
        )
    }
}

@Composable
private fun PdfContent(
    doc: DocumentEntity,
    canUnlock: Boolean,
    modifier: Modifier,
    onConvert: () -> Unit,
    onSave: () -> Unit,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!doc.locked) Thumbnail(doc.localPath, isPdf = true, size = 220.dp)
        val created = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(doc.createdAt))
        if (doc.locked) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.document_locked_title), style = MaterialTheme.typography.titleMedium)
                    if (canUnlock) {
                        Text(stringResource(R.string.document_locked_body))
                        Button(onClick = onUnlock) { Text(stringResource(R.string.action_unlock)) }
                    } else {
                        Text(stringResource(R.string.error_password))
                    }
                }
            }
        } else {
            Text(
                "${pluralStringResource(R.plurals.pages, doc.pageCount, doc.pageCount)} · ${formatSize(context, doc.sizeBytes)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.created_at, created), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onConvert, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_convert_to_images)) }
            OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save_to_phone)) }
        }
    }
}

@Composable
private fun ImagesContent(
    pages: List<PageEntity>,
    padding: PaddingValues,
    onMove: (Long, Int) -> Unit,
    onAdjust: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onMakePdf: () -> Unit,
    onSave: () -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onAddBlank: (afterPageId: Long?) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) onAddImages(uris)
    }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onAddImages(uris)
    }
    var addMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        if (pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_pages))
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                    PageRow(
                        page = page,
                        number = index + 1,
                        isFirst = index == 0,
                        isLast = index == pages.lastIndex,
                        onUp = { onMove(page.id, -1) },
                        onDown = { onMove(page.id, 1) },
                        onAdjust = { onAdjust(page.id) },
                        onRemove = { onRemove(page.id) },
                        onBlankAfter = { onAddBlank(page.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onMakePdf, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_make_pdf))
                }
                Box {
                    OutlinedButton(onClick = { addMenu = true }) { Text(stringResource(R.string.action_add)) }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.import_gallery)) }, onClick = {
                            addMenu = false
                            pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.import_image_files)) }, onClick = {
                            addMenu = false
                            browse.launch(IMAGE_MIME_TYPES)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.take_photo)) }, onClick = {
                            addMenu = false
                            onOpenCamera()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_blank_page_end)) }, onClick = {
                            addMenu = false
                            onAddBlank(null)
                        })
                    }
                }
                OutlinedButton(onClick = onSave, enabled = pages.isNotEmpty()) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun PageRow(
    page: PageEntity,
    number: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onAdjust: () -> Unit,
    onRemove: () -> Unit,
    onBlankAfter: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.action_edit_page), onClick = onAdjust)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(page.imagePath, isPdf = false, size = 72.dp, edit = page.edit())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.page_number, number), style = MaterialTheme.typography.titleSmall)
            val (w, h) = CropRotation.finalSize(page.width, page.height, Quad.parse(page.crop), page.rotation)
            Text("$w × $h", style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tap_to_edit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onUp, enabled = !isFirst) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
        }
        IconButton(onClick = onDown, enabled = !isLast) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_blank_page_after)) },
                    onClick = { menu = false; onBlankAfter() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_page)) },
                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}

@Composable
internal fun ConvertToImagesDialog(
    defaults: AppSettings,
    onDismiss: () -> Unit,
    onStart: (ImageFormat, Int, Int) -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(defaults.imageFormat) }
    var dpi by rememberSaveable { mutableIntStateOf(defaults.dpi) }
    var quality by rememberSaveable { mutableIntStateOf(defaults.jpegQuality) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_convert_to_images)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(stringResource(R.string.setting_format), ImageFormat.entries, format, { it.name }, { format = it })
                ChoiceRow(stringResource(R.string.setting_dpi), ConvertDefaults.DPI_CHOICES, dpi, { it.toString() }, { dpi = it })
                if (format == ImageFormat.JPG) {
                    Text(stringResource(R.string.setting_quality_value, quality), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.roundToInt() },
                        valueRange = 50f..100f,
                        steps = 9,
                    )
                }
                if (dpi >= 600) Text(stringResource(R.string.dpi_cap_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onStart(format, dpi, quality) }) { Text(stringResource(R.string.action_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun MakePdfDialog(defaults: AppSettings, onDismiss: () -> Unit, onStart: (PageSizeOption, MarginOption) -> Unit) {
    var size by rememberSaveable { mutableStateOf(defaults.pageSize) }
    var margin by rememberSaveable { mutableStateOf(defaults.margin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_make_pdf)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(stringResource(R.string.setting_page_size), PageSizeOption.entries, size, { pageSizeLabel(it) }, { size = it })
                ChoiceRow(stringResource(R.string.setting_margins), MarginOption.entries, margin, { marginLabel(it) }, { margin = it })
            }
        },
        confirmButton = { TextButton(onClick = { onStart(size, margin) }) { Text(stringResource(R.string.action_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun UnlockDialog(onDismiss: () -> Unit, onUnlock: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_unlock)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.unlock_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.action_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
