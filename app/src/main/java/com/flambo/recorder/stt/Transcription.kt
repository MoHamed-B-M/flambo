package com.flambo.recorder.stt

// Shared states for both engines. Kept deliberately small so the UI can
// render Listening / Partial / Final / Error without caring which engine runs.
sealed interface TranscriptionState {
    data object Idle : TranscriptionState
    data object Listening : TranscriptionState
    data class Partial(val text: String) : TranscriptionState
    data class Final(val text: String) : TranscriptionState
    data class Error(val message: String) : TranscriptionState
}

// Engine preference values stored in DataStore.
object SttEngine {
    const val AUTO = "auto"
    const val SYSTEM = "system"
    const val VOSK = "vosk"

    fun label(value: String): String = when (value) {
        SYSTEM -> "System"
        VOSK -> "Vosk offline"
        else -> "Auto"
    }
}

// One-shot file transcription shown on the detail screen.
sealed interface FileTranscription {
    data object Idle : FileTranscription
    data class Working(val progress: Float) : FileTranscription
    data class Done(val text: String, val offline: Boolean) : FileTranscription
    data class Error(val message: String, val needsModelCode: String? = null) : FileTranscription
}
