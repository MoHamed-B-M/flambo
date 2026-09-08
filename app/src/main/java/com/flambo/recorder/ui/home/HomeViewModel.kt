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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recordings: List<Recording> = emptyList(),
    val query: String = "",
    val isSearchActive: Boolean = false,
    val showTrash: Boolean = false,
    val trash: List<Recording> = emptyList(),
    val lastDeleted: Recording? = null
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

    val uiState: StateFlow<HomeUiState> = combine(
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

    fun restoreFromTrash(id: Long) = viewModelScope.launch { repository.restore(id) }

    fun rename(id: Long, newTitle: String) = viewModelScope.launch { repository.rename(id, newTitle) }
}
