package com.gaiascanner.app.ui.trash

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gaiascanner.app.R
import com.gaiascanner.app.appContainer
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.TrashRules
import com.gaiascanner.app.ui.components.Thumbnail

private sealed interface TrashDialog {
    data object Empty : TrashDialog
    data class Delete(val item: TrashViewModel.Item) : TrashDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: TrashViewModel = viewModel { TrashViewModel(container) }
    val items by vm.items.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<TrashDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!items.isNullOrEmpty()) {
                        TextButton(onClick = { dialog = TrashDialog.Empty }) { Text(stringResource(R.string.action_empty_trash)) }
                    }
                },
            )
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty), textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    stringResource(R.string.trash_hint, TrashRules.KEEP_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(list, key = { (if (it is TrashViewModel.Item.Folder) "f" else "d") + it.id }) { item ->
                TrashRow(item, onRestore = { vm.restore(item) }, onDelete = { dialog = TrashDialog.Delete(item) })
                HorizontalDivider()
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        TrashDialog.Empty -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.empty_trash_title)) },
            text = { Text(stringResource(R.string.empty_trash_body)) },
            confirmButton = {
                TextButton(onClick = { vm.emptyTrash(); dialog = null }) { Text(stringResource(R.string.action_empty_trash)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        is TrashDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(stringResource(R.string.delete_forever_body, d.item.name)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteForever(d.item); dialog = null }) { Text(stringResource(R.string.action_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TrashRow(item: TrashViewModel.Item, onRestore: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val days = TrashRules.daysLeft(item.trashedAt, now)
    val date = DateUtils.formatDateTime(
        context,
        item.trashedAt,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
    )
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.name, maxLines = 2) },
        supportingContent = {
            val extra = when (item) {
                is TrashViewModel.Item.Folder -> pluralStringResource(R.plurals.folder_items, item.itemCount, item.itemCount) + " · "
                is TrashViewModel.Item.Document -> ""
            }
            Text(extra + stringResource(R.string.trashed_on, date) + " · " + pluralStringResource(R.plurals.days_left, days, days))
        },
        leadingContent = {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                when (item) {
                    is TrashViewModel.Item.Folder -> Icon(
                        painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    is TrashViewModel.Item.Document ->
                        if (item.document.kind == DocumentKind.PDF && !item.document.locked) {
                            Thumbnail(item.document.localPath, isPdf = true, size = 56.dp)
                        }
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_restore)) }, onClick = { menu = false; onRestore() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete_forever)) }, onClick = { menu = false; onDelete() })
                }
            }
        },
    )
}
