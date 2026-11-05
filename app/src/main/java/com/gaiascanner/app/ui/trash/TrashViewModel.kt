package com.gaiascanner.app.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.FolderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(private val container: AppContainer) : ViewModel() {

    /** One row of the trash: a folder (with what was trashed with it) or a single document. */
    sealed interface Item {
        val id: Long
        val name: String
        val trashedAt: Long

        data class Folder(val folder: FolderEntity, val itemCount: Int) : Item {
            override val id get() = folder.id
            override val name get() = folder.name
            override val trashedAt get() = folder.trashedAt ?: 0L
        }

        data class Document(val document: DocumentEntity) : Item {
            override val id get() = document.id
            override val name get() = document.name
            override val trashedAt get() = document.trashedAt ?: 0L
        }
    }

    /**
     * Only the top of each trashed group is listed: a folder whose parent is not
     * in the trash, and documents not trashed together with their folder.
     * Newest first. Null until loaded.
     */
    val items: StateFlow<List<Item>?> = combine(container.library.trashedFolders, container.library.trashedDocuments) { folders, docs ->
        val trashedById = folders.associateBy { it.id }
        val topFolders = folders.filter { f -> f.parentId?.let { trashedById[it] }?.trashedAt != f.trashedAt }
        val inFolderGroup = { d: DocumentEntity -> d.folderId?.let { trashedById[it] }?.trashedAt == d.trashedAt }
        val counts = HashMap<Long, Int>()
        docs.filter(inFolderGroup).forEach { d -> d.folderId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        folders.filter { it !in topFolders }.forEach { f -> f.parentId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        val rows: List<Item> = topFolders.map { Item.Folder(it, counts[it.id] ?: 0) } +
            docs.filterNot(inFolderGroup).map { Item.Document(it) }
        rows.sortedByDescending { it.trashedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(item: Item) = viewModelScope.launch {
        when (item) {
            is Item.Folder -> container.library.restoreFolder(item.id)
            is Item.Document -> container.library.restoreDocument(item.id)
        }
    }

    fun deleteForever(item: Item) = viewModelScope.launch {
        when (item) {
            is Item.Folder -> container.library.deleteFolderForever(item.id)
            is Item.Document -> container.library.deleteDocument(item.id)
        }
    }

    fun emptyTrash() = viewModelScope.launch { container.library.emptyTrash() }
}
