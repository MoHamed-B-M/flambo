package com.flambo.recorder.stt

// One-shot offline file transcription shown on the detail screen.
sealed interface FileTranscription {
    data object Idle : FileTranscription
    data class Working(val progress: Float) : FileTranscription
    data class Done(val text: String, val offline: Boolean) : FileTranscription
    data class Error(val message: String, val needsModelCode: String? = null) : FileTranscription
}
