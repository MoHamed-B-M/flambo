package com.flambo.recorder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flambo.recorder.data.Recording
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.record.RecordingController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recordings: List<Recording> = emptyList(),
    val query: String = "",
    val isSearchActive: Boolean = false,
    val showTrash: Boolean = false,
    val trash: List<Recording> = emptyList(),
    val lastDeleted: Recording? = null,
    /** IDs whose audio file is gone from disk/SAF (e.g. deleted in a file manager). */
    val missingIds: Set<Long> = emptySet()
)

class HomeViewModel(
    private val repository: RecordingRepository,
    controller: RecordingController
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val showTrashFlow = MutableStateFlow(false)
    private val lastDeletedFlow = MutableStateFlow<Recording?>(null)

    val recorderState = controller.state

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recordingsFlow = queryFlow.flatMapLatest { q -> repository.search(q) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val missingIdsFlow = recordingsFlow
        .mapLatest { list -> repository.findMissingIds(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val baseUiState: StateFlow<HomeUiState> = combine(
        recordingsFlow,
        queryFlow,
        showTrashFlow,
        lastDeletedFlow,
        repository.observeTrash()
    ) { recordings, query, showTrash, lastDeleted, trash ->
        HomeUiState(
            recordings = recordings,
            query = query,
            isSearchActive = query.isNotEmpty(),
            showTrash = showTrash,
            trash = trash,
            lastDeleted = lastDeleted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(baseUiState, missingIdsFlow) { state, missingIds ->
        state.copy(missingIds = missingIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun onQueryChange(value: String) { queryFlow.value = value }
    fun clearQuery() { queryFlow.value = "" }
    fun toggleTrash(show: Boolean) { showTrashFlow.value = show }

    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }

    fun softDelete(rec: Recording) = viewModelScope.launch {
        repository.softDelete(rec.id)
        lastDeletedFlow.value = rec
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeletedFlow.value?.let { repository.restore(it.id) }
        lastDeletedFlow.value = null
    }

    fun dismissUndo() { lastDeletedFlow.value = null }

    fun purgeOldTrash() = viewModelScope.launch { repository.purgeOldTrash() }

    fun permanentDelete(id: Long) = viewModelScope.launch { repository.deletePermanently(id) }

    fun emptyTrash() = viewModelScope.launch { repository.emptyTrash() }

    fun softDeleteAll(ids: Set<Long>) = viewModelScope.launch { repository.softDeleteAll(ids) }

    fun moveRecordings(ids: Set<Long>, dir: java.io.File, onDone: (Int) -> Unit = {}) =
        viewModelScope.launch { onDone(repository.moveToDirectory(ids, dir)) }

    fun tagRecordings(ids: Set<Long>, tag: String, onDone: (Int) -> Unit = {}) =
        viewModelScope.launch { onDone(repository.addTagToAll(ids, tag)) }

    fun restoreFromTrash(id: Long) = viewModelScope.launch { repository.restore(id) }

    fun rename(id: Long, newTitle: String) = viewModelScope.launch { repository.rename(id, newTitle) }
}
