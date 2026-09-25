package com.flambo.recorder.ui.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flambo.recorder.audio.AudioEnhancer
import com.flambo.recorder.audio.EnhanceStrength
import com.flambo.recorder.data.AudioFileStore
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.Recording
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.data.SafFolderHelper
import com.flambo.recorder.stt.FileResult
import com.flambo.recorder.stt.FileTranscription
import com.flambo.recorder.stt.TranscriptionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DetailViewModel(
    private val repository: RecordingRepository,
    private val recordingId: Long,
    private val transcription: TranscriptionManager,
    private val prefs: PreferencesManager,
    private val appContext: Context
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

    val gestureEnabled: StateFlow<Boolean> =
        prefs.gestureEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val languagePref: StateFlow<String> =
        prefs.sttLanguageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _transcription = MutableStateFlow<FileTranscription>(FileTranscription.Idle)
    val transcriptionUi: StateFlow<FileTranscription> = _transcription

    private var transcribeJob: Job? = null

    fun setLanguage(tag: String) = viewModelScope.launch { prefs.setSttLanguage(tag) }

    fun transcribe() {
        val path = recording.value?.filePath ?: return
        if (transcribeJob?.isActive == true) return
        _transcription.value = FileTranscription.Working(0f)
        transcribeJob = viewModelScope.launch {
            val local = AudioFileStore.ensureLocalFile(appContext, path)
            if (local == null) {
                _transcription.value = FileTranscription.Error("Audio file not found — it may have been deleted outside the app.")
                return@launch
            }
            when (val result = transcription.transcribeFileWithProgress(local.absolutePath) {
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

    val enhanceStrength: StateFlow<String> =
        prefs.enhanceStrengthFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "balanced")

    private val _enhance = MutableStateFlow<EnhanceUi>(EnhanceUi.Idle)
    val enhanceUi: StateFlow<EnhanceUi> = _enhance

    private var enhanceJob: Job? = null
    private var enhanceStartedAt = 0L

    fun setEnhanceStrength(strength: String) =
        viewModelScope.launch { prefs.setEnhanceStrength(strength) }

    fun enhance() {
        val rec = recording.value ?: return
        if (enhanceJob?.isActive == true) return
        _enhance.value = EnhanceUi.Working(0f)
        enhanceJob = viewModelScope.launch {
            val strength = EnhanceStrength.fromPref(prefs.enhanceStrengthFlow.first())
            val keep = prefs.keepOriginalFlow.first()
            val local = AudioFileStore.ensureLocalFile(appContext, rec.filePath)
            if (local == null) {
                _enhance.value = EnhanceUi.Error("Audio file not found — it may have been deleted outside the app.")
                return@launch
            }
            val result = AudioEnhancer.enhance(local, strength) {
                _enhance.value = EnhanceUi.Working(it.coerceIn(0f, 1f))
            }
            result.fold(
                onSuccess = { enhanced ->
                    if (keep) {
                        repository.saveEnhanced(recordingId, enhanced.file.absolutePath)
                        _enhance.value = EnhanceUi.Done(enhanced.file.absolutePath, replaced = false)
                    } else {
                        replaceOriginal(rec, enhanced.file, enhanced.peaks)
                        _enhance.value = EnhanceUi.Done(rec.filePath, replaced = true)
                    }
                },
                onFailure = { _enhance.value = EnhanceUi.Error(it.message ?: "Couldn't clean this one.") }
            )
        }.also { enhanceStartedAt = System.currentTimeMillis() }
    }

    private suspend fun replaceOriginal(rec: Recording, cleaned: File, peaks: List<Float>) {
        // SAF-hosted originals can't be replaced in place: swap the document instead.
        if (AudioFileStore.isContentUri(rec.filePath)) {
            return try {
                AudioFileStore.delete(appContext, rec.filePath)
                val tree = runCatching { prefs.customFolderUri() }.getOrNull().orEmpty()
                val target = if (tree.isNotBlank() && SafFolderHelper.isTreeUriValid(appContext, tree)) {
                    AudioFileStore.copyFileToTree(appContext, tree, cleaned, "audio/wav")?.toString()
                } else null
                if (target != null) {
                    runCatching { cleaned.delete() }
                    val peaksStr = peaks.takeLast(120).joinToString(",") { String.format("%.3f", it) }
                    repository.update(rec.copy(filePath = target, amplitudePeaks = peaksStr))
                } else {
                    repository.saveEnhanced(recordingId, cleaned.absolutePath)
                }
            } catch (_: Exception) {
                repository.saveEnhanced(recordingId, cleaned.absolutePath)
            }
        }
        val original = File(rec.filePath)
        return try {
            original.delete()
            if (!cleaned.renameTo(original)) {

                repository.saveEnhanced(recordingId, cleaned.absolutePath)
                return
            }
            val peaksStr = peaks.takeLast(120).joinToString(",") { String.format("%.3f", it) }
            repository.update(rec.copy(amplitudePeaks = peaksStr))
        } catch (_: Exception) {
            repository.saveEnhanced(recordingId, cleaned.absolutePath)
        }
    }

    fun cancelEnhance() {
        enhanceJob?.cancel()

        recording.value?.let { rec ->
            if (AudioFileStore.isContentUri(rec.filePath)) return@let
            val dir = File(rec.filePath).parentFile
            val candidate = File(dir, File(rec.filePath).nameWithoutExtension + "_enhanced.wav")
            if (candidate.exists() && candidate.lastModified() >= enhanceStartedAt) {
                runCatching { candidate.delete() }
            }
        }
        _enhance.value = EnhanceUi.Idle
    }

    fun dismissEnhance() {
        enhanceJob?.cancel()
        _enhance.value = EnhanceUi.Idle
    }

    fun deleteEnhanced() = viewModelScope.launch {
        repository.clearEnhanced(recordingId)
        if (_enhance.value is EnhanceUi.Done) _enhance.value = EnhanceUi.Idle
    }
}

sealed interface EnhanceUi {
    data object Idle : EnhanceUi
    data class Working(val progress: Float) : EnhanceUi
    data class Done(val path: String, val replaced: Boolean) : EnhanceUi
    data class Error(val message: String) : EnhanceUi
}
