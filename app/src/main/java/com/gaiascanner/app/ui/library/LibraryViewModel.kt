package com.gaiascanner.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.core.LibraryOrder
import com.gaiascanner.app.core.LibrarySort
import com.gaiascanner.app.core.LibraryView
import com.gaiascanner.app.core.SortKey
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.FolderEntity
import com.gaiascanner.app.ui.importSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        /** Folders from the root down to the open folder; empty at the root. */
        val path: List<FolderEntity> = emptyList(),
        val folders: List<FolderEntity> = emptyList(),
        val documents: List<DocumentEntity> = emptyList(),
        /** Number of documents and subfolders directly inside each folder. */
        val folderItemCounts: Map<Long, Int> = emptyMap(),
        val sort: LibrarySort = LibrarySort(),
        val view: LibraryView = LibraryView.LIST,
        /** Non-null while the search box is open. Search covers the whole library. */
        val query: String? = null,
        /** True when the library has no documents and no folders at all. */
        val libraryEmpty: Boolean = false,
    ) {
        val currentFolder: FolderEntity? get() = path.lastOrNull()
    }

    private val openFolderId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState?> = combine(
        container.library.documents,
        container.library.folders,
        container.settings.settings,
        openFolderId,
        query,
    ) { docs, folders, settings, openId, q ->
        val byId = folders.associateBy { it.id }
        // A folder that was trashed or deleted while open falls back to the root.
        val current = openId?.takeIf { it in byId }
        val sort = settings.librarySort
        val searching = !q.isNullOrBlank()
        val shownFolders = if (searching) folders.filter { LibraryOrder.matches(it.name, q!!) } else folders.filter { it.parentId == current }
        val shownDocs = if (searching) docs.filter { LibraryOrder.matches(it.name, q!!) } else docs.filter { it.folderId == current }
        val counts = HashMap<Long, Int>()
        folders.forEach { f -> f.parentId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        docs.forEach { d -> d.folderId?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        UiState(
            path = LibraryOrder.pathTo(current, byId) { it.parentId },
            folders = LibraryOrder.sort(shownFolders, sort) { SortKey(it.name, it.createdAt, it.modifiedAt, 0) },
            documents = LibraryOrder.sort(shownDocs, sort) { SortKey(it.name, it.createdAt, it.modifiedAt, it.sizeBytes) },
            folderItemCounts = counts,
            sort = sort,
            view = settings.libraryView,
            query = q,
            libraryEmpty = docs.isEmpty() && folders.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeJobs: StateFlow<Int> = container.jobs.recent
        .map { jobs -> jobs.count { it.status.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val localMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: Flow<String> = merge(localMessages, container.messages)

    init {
        // New imports and camera documents land in the folder that is open.
        viewModelScope.launch { openFolderId.collect { container.library.targetFolderId = it } }
    }

    fun openFolder(id: Long?) {
        openFolderId.value = id
    }

    /** Goes one folder up. Returns false at the root. */
    fun goUp(): Boolean {
        val current = state.value?.currentFolder ?: return false
        openFolderId.value = current.parentId
        return true
    }

    fun setQuery(text: String?) {
        query.value = text
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { container.settings.update { it.copy(librarySort = sort) } }
    }

    fun setView(view: LibraryView) {
        viewModelScope.launch { container.settings.update { it.copy(libraryView = view) } }
    }

    fun createFolder(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) container.library.createFolder(name, state.value?.currentFolder?.id)
    }

    fun renameFolder(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) container.library.renameFolder(id, name)
    }

    fun trashFolder(id: Long) = viewModelScope.launch {
        container.library.trashFolder(id)
    }

    fun importPdfs(uris: List<Uri>) = viewModelScope.launch {
        localMessages.emit(importSummary(container.appContext, container.library.importPdfs(uris)))
    }

    fun importImages(uris: List<Uri>) = viewModelScope.launch {
        localMessages.emit(importSummary(container.appContext, container.library.importImages(uris)))
    }

    fun say(text: String) {
        localMessages.tryEmit(text)
    }
}
