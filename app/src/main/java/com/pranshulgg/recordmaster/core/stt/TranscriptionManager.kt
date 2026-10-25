package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface FileResult {
    data class Done(val text: String, val offline: Boolean) : FileResult
    data class Failed(val message: String) : FileResult
}

/**
 * Offline transcription entry point.
 *
 * The Vosk engine was removed in Phase 1 of the Whisper migration. The
 * on-device whisper.cpp engine is wired in over the next phases; until then
 * requests fail gracefully instead of crashing the UI.
 */
class TranscriptionManager(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") prefs: PreferencesManager
) {
    private val _modelProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Per-language model download progress, 0f..1f. Empty until Phase 3. */
    val modelProgress: StateFlow<Map<String, Float>> = _modelProgress.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    suspend fun transcribeFileWithProgress(
        path: String,
        onProgress: (Float) -> Unit
    ): FileResult = FileResult.Failed(
        "On-device Whisper transcription isn't available in this build yet."
    )

    fun release() = Unit
}
