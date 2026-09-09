package com.flambo.recorder.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

// Primary engine: system SpeechRecognizer. Huge language coverage, understands
// partial results, and works offline when the language pack is installed
// (EXTRA_PREFER_OFFLINE). Cannot transcribe files and cannot share the mic
// with MediaRecorder — the manager guards both cases.
class SpeechRecognizerEngine(private val context: Context) {

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var finalText = ""

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun emitError(message: String) {
        main.post { _state.value = TranscriptionState.Error(message) }
    }

    fun reset() {
        main.post { _state.value = TranscriptionState.Idle }
    }

    fun startListening(languageTag: String) {
        main.post {
            try {
                destroyInternal()
                if (!isAvailable()) {
                    _state.value = TranscriptionState.Error("Speech recognition isn't available on this device.")
                    return@post
                }
                val tag = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                this.recognizer = recognizer
                finalText = ""
                recognizer.setRecognitionListener(listener)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Use on-device pack when present; falls back to online automatically.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                _state.value = TranscriptionState.Listening
                recognizer.startListening(intent)
            } catch (e: Exception) {
                _state.value = TranscriptionState.Error("Couldn't start listening: ${e.message}")
            }
        }
    }

    fun stopListening() {
        main.post { runCatching { recognizer?.stopListening() } }
    }

    fun cancel() {
        main.post {
            runCatching { recognizer?.cancel() }
            _state.value = TranscriptionState.Idle
        }
    }

    fun release() {
        main.post { destroyInternal() }
    }

    private fun destroyInternal() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = TranscriptionState.Listening
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) _state.value = TranscriptionState.Partial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            finalText = text.ifBlank { finalText }
            _state.value = if (finalText.isBlank()) {
                TranscriptionState.Error("Didn't catch that — try again in a quieter spot.")
            } else {
                TranscriptionState.Final(finalText)
            }
        }

        override fun onError(error: Int) {
            // If we already have a partial, keep it instead of failing hard.
            val current = _state.value
            if (current is TranscriptionState.Partial && error == SpeechRecognizer.ERROR_NO_MATCH) {
                _state.value = TranscriptionState.Final(current.text)
                return
            }
            _state.value = TranscriptionState.Error(
                when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone error — is another app using it?"
                    SpeechRecognizer.ERROR_CLIENT -> " Listening was interrupted."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network needed for this language — check your connection or download the offline pack."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy — wait a moment and retry."
                    SpeechRecognizer.ERROR_SERVER -> "Recognition server hiccup — try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard — tap dictate and speak."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        "That language isn't supported here — pick another one."
                    else -> "Recognition failed (code $error)."
                }.trim()
            )
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
