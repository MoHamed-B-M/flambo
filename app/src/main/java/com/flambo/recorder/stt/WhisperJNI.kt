package com.flambo.recorder.stt

internal object WhisperJNI {
    init {
        try { System.loadLibrary("whisper_jni") } catch (_: UnsatisfiedLinkError) {}
    }
    external fun init(modelPath: String): Long
    external fun free(ctxPtr: Long)
    external fun fullTranscribe(ctxPtr: Long, nThreads: Int, audioData: FloatArray, lang: String?): Int
    external fun getSegmentCount(ctxPtr: Long): Int
    external fun getSegmentText(ctxPtr: Long, index: Int): String
    external fun getSegmentT0(ctxPtr: Long, index: Int): Long
    external fun getSegmentT1(ctxPtr: Long, index: Int): Long
}
