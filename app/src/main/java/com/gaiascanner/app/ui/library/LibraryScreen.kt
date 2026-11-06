package com.gaiascanner.app.ui.library

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.LibrarySort
import com.gaiascanner.app.core.LibraryView
import com.gaiascanner.app.core.SortField
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.FolderEntity
import com.gaiascanner.app.data.db.PageEntity
import com.gaiascanner.app.data.db.edit
import com.gaiascanner.app.core.LibraryOrder
import com.gaiascanner.app.ui.components.Thumbnail
import com.gaiascanner.app.ui.components.rememberNotificationPermissionRequest
import com.gaiascanner.app.ui.document.ConvertToImagesDialog
import com.gaiascanner.app.ui.document.MakePdfDialog
import kotlinx.coroutines.launch
import com.gaiascanner.app.ui.formatSize
import com.gaiascanner.app.ui.info.FeedbackDialog

private val IMAGE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/** Dialogs the library can show. */
private sealed interface LibraryDialog {
    data object NewFolder : LibraryDialog
    data object Feedback : LibraryDialog
    data class Rename(val folder: FolderEntity) : LibraryDialog
    data class TrashFolder(val folder: FolderEntity) : LibraryDialog
    data object TrashSelected : LibraryDialog
    data object MoveSelected : LibraryDialog
    /** Convert step 1: PDFs to images (skipped when there are none). Step 2: image sets to PDF. */
    data class ConvertPdfs(val plan: LibraryViewModel.ConvertPlan) : LibraryDialog
    data class MakePdfs(val plan: LibraryViewModel.ConvertPlan) : LibraryDialog
}

/** Long press selects; while anything is selected, a tap toggles too. */
private class Selection(val ids: Set<Long>, val onToggle: (Long) -> Unit) {
    val active: Boolean get() = ids.isNotEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    onOpenJobs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: LibraryViewModel = viewModel { LibraryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val activeJobs by vm.activeJobs.collectAsStateWithLifecycle()
    val selectedIds by vm.selected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val askNotifications = rememberNotificationPermissionRequest()
    val nothingToShare = stringResource(R.string.nothing_to_share)
    val nothingToConvert = stringResource(R.string.nothing_to_convert)
    val drawer = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(vm) { if (vm.takeTutorialPrompt()) onOpenTutorial() }
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
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val folderTrashedText = stringResource(R.string.folder_trashed)

    val ui = state
    val searching = ui?.query != null
    val inFolder = ui?.currentFolder != null
    // Only documents still on screen count as selected.
    val visibleSelected = ui?.documents.orEmpty().map { it.id }.filter { it in selectedIds }.toSet()
    val selection = Selection(visibleSelected, vm::toggleSelected)
    BackHandler(enabled = selection.active || searching || inFolder) {
        when {
            selection.active -> vm.clearSelection()
            searching -> vm.setQuery(null)
            else -> vm.goUp()
        }
    }
    // Registered last, so it wins while the side menu is open.
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    val fromMenu: (() -> Unit) -> Unit = { action -> scope.launch { drawer.close() }; action() }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !inFolder && !selection.active && !searching,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
                DrawerItem(Icons.Filled.Settings, R.string.menu_settings) { fromMenu(onOpenSettings) }
                DrawerItem(Icons.Filled.Delete, R.string.menu_trash) { fromMenu(onOpenTrash) }
                DrawerItem(Icons.Filled.PlayArrow, R.string.menu_tutorial) { fromMenu(onOpenTutorial) }
                DrawerItem(Icons.Filled.Email, R.string.menu_feedback) { fromMenu { dialog = LibraryDialog.Feedback } }
                DrawerItem(Icons.Filled.Info, R.string.menu_about) { fromMenu(onOpenAbout) }
            }
        },
    ) {
        Scaffold(
            topBar = {
                if (selection.active) {
                    SelectionBar(
                        count = visibleSelected.size,
                        onClose = vm::clearSelection,
                        onShare = {
                            scope.launch {
                                val intent = vm.shareSelected()
                                if (intent == null) vm.say(nothingToShare) else context.startActivity(intent)
                            }
                        },
                        onTrash = { dialog = LibraryDialog.TrashSelected },
                        onSelectAll = vm::selectAll,
                        onMove = { dialog = LibraryDialog.MoveSelected },
                        onConvert = {
                            val plan = vm.convertPlan()
                            dialog = when {
                                plan.pdfIds.isNotEmpty() -> LibraryDialog.ConvertPdfs(plan)
                                plan.imageDocIds.isNotEmpty() -> LibraryDialog.MakePdfs(plan)
                                else -> { vm.say(nothingToConvert); null }
                            }
                        },
                    )
                } else if (searching) {
                    LibrarySearchBar(query = ui?.query.orEmpty(), onQuery = vm::setQuery, onClose = { vm.setQuery(null) })
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                ui?.currentFolder?.name ?: stringResource(R.string.app_name),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            if (inFolder) {
                                IconButton(onClick = { vm.goUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_up))
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.action_open_menu))
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            SortMenu(
                                sort = ui?.sort ?: LibrarySort(),
                                view = ui?.view ?: LibraryView.LIST,
                                onSort = vm::setSort,
                                onView = vm::setView,
                            )
                            IconButton(onClick = onOpenJobs) {
                                BadgedBox(badge = { if (activeJobs > 0) Badge { Text(activeJobs.toString()) } }) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.jobs_title))
                                }
                            }
                            OverflowMenu(
                                onImportPdfs = { pickPdfs.launch(arrayOf("application/pdf")) },
                                onImportImageFiles = { browseImages.launch(IMAGE_MIME_TYPES) },
                                onNewFolder = { dialog = LibraryDialog.NewFolder },
                            )
                        },
                    )
                }
            },
            bottomBar = {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(R.drawable.ic_gallery), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_gallery))
                        }
                        Button(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.take_photo))
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (ui == null) return@Scaffold
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (inFolder && !searching) {
                    FolderPath(path = ui.path, onOpen = vm::openFolder)
                }
                val empty = ui.folders.isEmpty() && ui.documents.isEmpty()
                when {
                    empty && searching -> Message(stringResource(R.string.search_no_results))
                    empty && ui.libraryEmpty -> EmptyLibrary()
                    empty -> Message(stringResource(R.string.folder_empty))
                    ui.view == LibraryView.GRID -> LibraryGrid(
                        ui = ui,
                        selection = selection,
                        onOpenFolder = { vm.setQuery(null); vm.openFolder(it.id) },
                        onOpenDocument = onOpenDocument,
                        onFolderAction = { dialog = it },
                    )
                    else -> LibraryList(
                        ui = ui,
                        selection = selection,
                        onOpenFolder = { vm.setQuery(null); vm.openFolder(it.id) },
                        onOpenDocument = onOpenDocument,
                        onFolderAction = { dialog = it },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibraryDialog.Feedback -> FeedbackDialog(onDismiss = { dialog = null })
        LibraryDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.action_new_folder),
            initial = "",
            onDismiss = { dialog = null },
            onConfirm = { vm.createFolder(it); dialog = null },
        )
        is LibraryDialog.Rename -> NameDialog(
            title = stringResource(R.string.action_rename),
            initial = d.folder.name,
            onDismiss = { dialog = null },
            onConfirm = { vm.renameFolder(d.folder.id, it); dialog = null },
        )
        is LibraryDialog.TrashFolder -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.folder_trash_title)) },
            text = { Text(stringResource(R.string.folder_trash_body, d.folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.trashFolder(d.folder.id)
                    vm.say(folderTrashedText)
                    dialog = null
                }) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        LibraryDialog.TrashSelected -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.trash_selected_title)) },
            text = { Text(pluralStringResource(R.plurals.trash_selected_body, visibleSelected.size, visibleSelected.size)) },
            confirmButton = {
                TextButton(onClick = { vm.trashSelected(); dialog = null }) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        LibraryDialog.MoveSelected -> MoveToDialog(
            folders = allFolders,
            current = ui?.currentFolder?.id,
            onDismiss = { dialog = null },
            onMove = { vm.moveSelected(it); dialog = null },
        )
        is LibraryDialog.ConvertPdfs -> ConvertToImagesDialog(
            defaults = settings,
            onDismiss = { dialog = null },
            onStart = { format, dpi, quality ->
                askNotifications()
                vm.convertPdfs(d.plan.pdfIds, format, dpi, quality)
                dialog = if (d.plan.imageDocIds.isNotEmpty()) LibraryDialog.MakePdfs(d.plan) else null
            },
        )
        is LibraryDialog.MakePdfs -> MakePdfDialog(
            defaults = settings,
            onDismiss = { dialog = null },
            onStart = { size, margin ->
                askNotifications()
                vm.makePdfs(d.plan.imageDocIds, size, margin)
                dialog = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onTrash: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onConvert: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
        },
        title = { Text(stringResource(R.string.selected_count, count)) },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share)) }
            IconButton(onClick = onTrash) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_move_to_trash))
            }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_select_all)) }, onClick = { open = false; onSelectAll() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_to_folder)) }, onClick = { open = false; onMove() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_convert)) }, onClick = { open = false; onConvert() })
                }
            }
        },
    )
}

@Composable
private fun MoveToDialog(folders: List<FolderEntity>, current: Long?, onDismiss: () -> Unit, onMove: (Long?) -> Unit) {
    val tree = remember(folders) { LibraryOrder.flattenTree(folders, { it.id }, { it.parentId }, { it.name }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    MoveTarget(stringResource(R.string.library_root), depth = 0, isCurrent = current == null) { onMove(null) }
                }
                items(tree, key = { it.first.id }) { (folder, depth) ->
                    MoveTarget(folder.name, depth = depth + 1, isCurrent = current == folder.id) { onMove(folder.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MoveTarget(name: String, depth: Int, isCurrent: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = !isCurrent, onClick = onClick),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width((depth * 16).dp))
                Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        trailingContent = if (isCurrent) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
    )
}

@Composable
private fun SortMenu(sort: LibrarySort, view: LibraryView, onSort: (LibrarySort) -> Unit, onView: (LibraryView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val fields = listOf(
                SortField.NAME to R.string.sort_name,
                SortField.CREATED to R.string.sort_created,
                SortField.MODIFIED to R.string.sort_modified,
                SortField.SIZE to R.string.sort_size,
            )
            fields.forEach { (field, label) ->
                CheckItem(stringResource(label), checked = sort.field == field) {
                    onSort(sort.copy(field = field))
                    open = false
                }
            }
            HorizontalDivider()
            CheckItem(stringResource(R.string.sort_ascending), checked = sort.ascending) {
                onSort(sort.copy(ascending = true))
                open = false
            }
            CheckItem(stringResource(R.string.sort_descending), checked = !sort.ascending) {
                onSort(sort.copy(ascending = false))
                open = false
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_view_list)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_view_list), contentDescription = null) },
                trailingIcon = { if (view == LibraryView.LIST) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = { onView(LibraryView.LIST); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_view_grid)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_grid_view), contentDescription = null) },
                trailingIcon = { if (view == LibraryView.GRID) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = { onView(LibraryView.GRID); open = false },
            )
        }
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        icon = { Icon(icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = { if (checked) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun OverflowMenu(
    onImportPdfs: () -> Unit,
    onImportImageFiles: () -> Unit,
    onNewFolder: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.import_pdfs)) }, onClick = { open = false; onImportPdfs() })
            DropdownMenuItem(text = { Text(stringResource(R.string.import_image_files)) }, onClick = { open = false; onImportImageFiles() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_new_folder)) }, onClick = { open = false; onNewFolder() })
        }
    }
}

@Composable
private fun FolderPath(path: List<FolderEntity>, onOpen: (Long?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onOpen(null) }) { Text(stringResource(R.string.library_root)) }
        path.forEachIndexed { i, folder ->
            Text("›", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onOpen(folder.id) }, enabled = i < path.lastIndex) {
                Text(folder.name, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LibraryList(
    ui: LibraryViewModel.UiState,
    selection: Selection,
    onOpenFolder: (FolderEntity) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onFolderAction: (LibraryDialog) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(ui.folders, key = { "f${it.id}" }) { folder ->
            FolderRow(folder, ui.folderItemCounts[folder.id] ?: 0, onClick = { onOpenFolder(folder) }, onAction = onFolderAction)
            HorizontalDivider()
        }
        items(ui.documents, key = { "d${it.id}" }) { doc ->
            DocumentRow(
                doc,
                selected = doc.id in selection.ids,
                onClick = { if (selection.active) selection.onToggle(doc.id) else onOpenDocument(doc.id) },
                onLongClick = { selection.onToggle(doc.id) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(
    ui: LibraryViewModel.UiState,
    selection: Selection,
    onOpenFolder: (FolderEntity) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onFolderAction: (LibraryDialog) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(ui.folders, key = { "f${it.id}" }) { folder ->
            Card(modifier = Modifier.clickable { onOpenFolder(folder) }) {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        val count = ui.folderItemCounts[folder.id] ?: 0
                        Text(
                            pluralStringResource(R.plurals.folder_items, count, count),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Box(Modifier.align(Alignment.TopEnd)) { FolderActions(folder, onFolderAction) }
                }
            }
        }
        items(ui.documents, key = { "d${it.id}" }) { doc ->
            val context = LocalContext.current
            val picked = doc.id in selection.ids
            Card(
                modifier = Modifier.combinedClickable(
                    onClick = { if (selection.active) selection.onToggle(doc.id) else onOpenDocument(doc.id) },
                    onLongClick = { selection.onToggle(doc.id) },
                ),
                colors = if (picked) {
                    androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    androidx.compose.material3.CardDefaults.cardColors()
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        DocumentThumbnail(doc, size = 120.dp)
                        if (doc.locked) Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.document_locked_short))
                        if (picked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.selected_count, 1),
                                modifier = Modifier.align(Alignment.TopEnd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(doc.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(
                        "${pluralStringResource(R.plurals.pages, doc.pageCount, doc.pageCount)} · ${shortDate(context, doc.modifiedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntity, itemCount: Int, onClick: () -> Unit, onAction: (LibraryDialog) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(folder.name, maxLines = 2) },
        supportingContent = { Text(pluralStringResource(R.plurals.folder_items, itemCount, itemCount)) },
        leadingContent = {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = { FolderActions(folder, onAction) },
    )
}

@Composable
private fun FolderActions(folder: FolderEntity, onAction: (LibraryDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = { open = false; onAction(LibraryDialog.Rename(folder)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_trash)) },
                onClick = { open = false; onAction(LibraryDialog.TrashFolder(folder)) },
            )
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                label = { Text(stringResource(R.string.folder_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentRow(doc: DocumentEntity, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val pages = pluralStringResource(R.plurals.pages, doc.pageCount, doc.pageCount)
    val kind = stringResource(if (doc.kind == DocumentKind.PDF) R.string.kind_pdf else R.string.kind_images)
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        headlineContent = { Text(doc.name, maxLines = 2) },
        supportingContent = {
            val details = if (doc.locked) {
                stringResource(R.string.document_locked_short)
            } else {
                "$pages · ${formatSize(context, doc.sizeBytes)}"
            }
            Column {
                Text("$kind · $details")
                Text(
                    stringResource(R.string.modified_on, shortDate(context, doc.modifiedAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        leadingContent = { DocumentThumbnail(doc, size = 56.dp) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else if (doc.locked) {
            { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.document_locked_short)) }
        } else {
            null
        },
    )
}

private fun shortDate(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
    )

@Composable
private fun DocumentThumbnail(doc: DocumentEntity, size: Dp) {
    val container = LocalContext.current.appContainer
    if (doc.kind == DocumentKind.PDF) {
        if (!doc.locked) Thumbnail(doc.localPath, isPdf = true, size = size)
    } else {
        val firstPage by produceState<PageEntity?>(null, doc.id, doc.modifiedAt) {
            value = container.library.getPages(doc.id).firstOrNull()
        }
        firstPage?.let { Thumbnail(it.imagePath, isPdf = false, size = size, edit = it.edit()) }
    }
}
