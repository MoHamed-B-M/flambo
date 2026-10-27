package com.pranshulgg.recordmaster.core.stt

import android.util.Log

class WhisperEngine {
    init {
        System.loadLibrary("whisper-jni")
    }

    /**
     * Load a GGML model from the given absolute path.
     * Returns a context pointer (as Long). 0 if loading fails.
     */
    external fun nativeLoadModel(modelPath: String): Long

    /**
     * Transcribe raw 16-bit mono PCM samples (16 kHz expected).
     * PCM is converted to float32 in [-1,1] natively.
     * Returns concatenated transcript text for all segments.
     */
    external fun nativeTranscribe(ctxPtr: Long, pcm: ShortArray): String

    /**
     * Free the whisper context.
     */
    external fun nativeFree(ctxPtr: Long)

    /**
     * Get whisper.cpp version string.
     */
    external fun nativeVersion(): String

    companion object {
        private const val TAG = "WhisperEngine"

        fun load(): WhisperEngine {
            return WhisperEngine()
        }
    }
}
