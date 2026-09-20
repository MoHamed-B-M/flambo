package com.flambo.recorder.stt

sealed interface FileTranscription {
    data object Idle : FileTranscription
    data class Working(val progress: Float) : FileTranscription
    data class Done(val text: String, val offline: Boolean) : FileTranscription
    data class Error(val message: String, val needsModelCode: String? = null) : FileTranscription
}
