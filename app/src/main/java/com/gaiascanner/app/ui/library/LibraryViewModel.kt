package com.gaiascanner.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.R
import com.gaiascanner.app.core.DocumentKind
import com.gaiascanner.app.core.ImageFormat
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption
import com.gaiascanner.app.core.LibraryOrder
import com.gaiascanner.app.core.LibrarySort
import com.gaiascanner.app.core.LibraryView
import com.gaiascanner.app.core.SortKey
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.data.db.FolderEntity
import com.gaiascanner.app.data.AppSettings
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

    /** Documents picked in select-many mode. Empty means the mode is off. */
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Every folder that is not trashed, for the "Move to" list. */
    val allFolders: StateFlow<List<FolderEntity>> = container.library.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

    private var tutorialChecked = false

    /** True once per app run if the tutorial has never been shown. */
    suspend fun takeTutorialPrompt(): Boolean {
        if (tutorialChecked) return false
        tutorialChecked = true
        return !container.settings.current().tutorialSeen
    }

    fun openFolder(id: Long?) {
        _selected.value = emptySet()
        openFolderId.value = id
    }

    /** Goes one folder up. Returns false at the root. */
    fun goUp(): Boolean {
        val current = state.value?.currentFolder ?: return false
        openFolder(current.parentId)
        return true
    }

    fun toggleSelected(documentId: Long) {
        _selected.value = _selected.value.let { if (documentId in it) it - documentId else it + documentId }
    }

    fun selectAll() {
        _selected.value = state.value?.documents.orEmpty().map { it.id }.toSet()
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    /** The selected documents that are still shown. */
    private fun selectedDocuments(): List<DocumentEntity> {
        val ids = _selected.value
        return state.value?.documents.orEmpty().filter { it.id in ids }
    }

    /** Share sheet for every selected file. Locked PDFs are left out. */
    suspend fun shareSelected(): Intent? {
        val docs = selectedDocuments().filterNot { it.locked }
        val files = docs.flatMap { container.library.documentFiles(it) }.filter { it.exists() }
        if (files.isEmpty()) return null
        val mime = when {
            docs.all { it.kind == DocumentKind.PDF } -> "application/pdf"
            docs.all { it.kind == DocumentKind.IMAGES } -> "image/*"
            else -> "*/*"
        }
        return container.exporter.shareIntent(files, mime)
    }

    fun trashSelected() = viewModelScope.launch {
        val ids = selectedDocuments().map { it.id }
        container.library.trashDocuments(ids)
        _selected.value = emptySet()
        localMessages.emit(container.appContext.resources.getQuantityString(R.plurals.trashed_files, ids.size, ids.size))
    }

    fun moveSelected(folderId: Long?) = viewModelScope.launch {
        container.library.moveDocuments(selectedDocuments().map { it.id }, folderId)
        _selected.value = emptySet()
        localMessages.emit(container.appContext.getString(R.string.moved))
    }

    /** What Convert would do: PDFs become images, image sets become PDFs. Locked PDFs are skipped. */
    data class ConvertPlan(val pdfIds: List<Long>, val imageDocIds: List<Long>)

    fun convertPlan(): ConvertPlan {
        val docs = selectedDocuments()
        return ConvertPlan(
            pdfIds = docs.filter { it.kind == DocumentKind.PDF && !it.locked }.map { it.id },
            imageDocIds = docs.filter { it.kind == DocumentKind.IMAGES && it.pageCount > 0 }.map { it.id },
        )
    }

    fun convertPdfs(ids: List<Long>, format: ImageFormat, dpi: Int, quality: Int) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        container.settings.update { it.copy(imageFormat = format, dpi = dpi, jpegQuality = quality) }
        container.jobs.enqueuePdfToImages(ids, format, dpi, quality)
        _selected.value = emptySet()
        localMessages.emit(container.appContext.getString(R.string.job_started))
    }

    fun makePdfs(ids: List<Long>, pageSize: PageSizeOption, margin: MarginOption) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        container.settings.update { it.copy(pageSize = pageSize, margin = margin) }
        ids.forEach { container.jobs.enqueueImagesToPdf(it, pageSize, margin) }
        _selected.value = emptySet()
        localMessages.emit(container.appContext.getString(R.string.job_started))
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
