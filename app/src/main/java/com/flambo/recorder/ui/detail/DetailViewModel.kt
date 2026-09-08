package com.flambo.recorder.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flambo.recorder.data.Recording
import com.flambo.recorder.data.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: RecordingRepository,
    private val recordingId: Long
) : ViewModel() {

    val recording: StateFlow<Recording?> =
        repository.observeById(recordingId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _editTitle = MutableStateFlow<String?>(null)
    val editTitle: StateFlow<String?> = _editTitle

    fun setEditTitle(value: String?) { _editTitle.value = value }

    fun saveTitle() {
        val title = _editTitle.value?.trim().orEmpty()
        if (title.isBlank()) { _editTitle.value = null; return }
        viewModelScope.launch {
            repository.rename(recordingId, title)
            _editTitle.value = null
        }
    }

    fun toggleFavorite() = viewModelScope.launch { repository.toggleFavorite(recordingId) }

    fun updateTags(tags: List<String>) = viewModelScope.launch { repository.updateTags(recordingId, tags) }

    fun softDelete() = viewModelScope.launch { repository.softDelete(recordingId) }
}
