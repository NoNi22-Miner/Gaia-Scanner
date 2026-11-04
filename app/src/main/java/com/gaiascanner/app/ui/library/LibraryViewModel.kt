package com.gaiascanner.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaiascanner.app.AppContainer
import com.gaiascanner.app.data.db.DocumentEntity
import com.gaiascanner.app.ui.importSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val documents: StateFlow<List<DocumentEntity>?> = container.library.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeJobs: StateFlow<Int> = container.jobs.recent
        .map { jobs -> jobs.count { it.status.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val localMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: Flow<String> = merge(localMessages, container.messages)

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
