package com.flambo.recorder.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.Recording
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.stt.FileResult
import com.flambo.recorder.stt.FileTranscription
import com.flambo.recorder.stt.SpeechLanguages
import com.flambo.recorder.stt.SttEngine
import com.flambo.recorder.stt.TranscriptionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class DetailViewModel(
    private val repository: RecordingRepository,
    private val recordingId: Long,
    private val transcription: TranscriptionManager,
    private val prefs: PreferencesManager
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

    // ---- Speech-to-text ----

    val enginePref: StateFlow<String> =
        prefs.sttEngineFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SttEngine.AUTO)

    val languagePref: StateFlow<String> =
        prefs.sttLanguageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _transcription = MutableStateFlow<FileTranscription>(FileTranscription.Idle)
    val transcriptionUi: StateFlow<FileTranscription> = _transcription

    private val _speechLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val speechLanguages: StateFlow<List<Locale>> = _speechLanguages

    private var transcribeJob: Job? = null

    // Languages need a context for the details broadcast, so the screen
    // passes its own and we cache the result.
    fun loadSpeechLanguages(context: android.content.Context) {
        if (_speechLanguages.value.isNotEmpty()) return
        viewModelScope.launch {
            _speechLanguages.value = SpeechLanguages.fetchSupported(context.applicationContext)
        }
    }

    fun setEngine(engine: String) = viewModelScope.launch { prefs.setSttEngine(engine) }
    fun setLanguage(tag: String) = viewModelScope.launch { prefs.setSttLanguage(tag) }

    fun transcribe() {
        val path = recording.value?.filePath ?: return
        if (transcribeJob?.isActive == true) return
        _transcription.value = FileTranscription.Working(0f)
        transcribeJob = viewModelScope.launch {
            when (val result = transcription.transcribeFileWithProgress(path) {
                _transcription.value = FileTranscription.Working(it.coerceIn(0f, 1f))
            }) {
                is FileResult.Done -> _transcription.value = FileTranscription.Done(result.text, result.offline)
                is FileResult.Failed -> _transcription.value =
                    FileTranscription.Error(result.message, result.needsModelCode)
            }
        }
    }

    fun cancelTranscription() {
        transcribeJob?.cancel()
        _transcription.value = FileTranscription.Idle
    }

    fun dismissTranscription() {
        transcribeJob?.cancel()
        _transcription.value = FileTranscription.Idle
    }

    fun saveTranscript(text: String) = viewModelScope.launch {
        repository.saveTranscript(recordingId, text)
    }

    fun clearSavedTranscript() = viewModelScope.launch {
        repository.clearTranscript(recordingId)
    }
}
