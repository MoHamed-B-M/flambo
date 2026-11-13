package com.pranshulgg.recordmaster.core.stt

/** Where a model came from. Built-ins are shipped/downloaded by the app. */
enum class SpeechModelSource { BUILT_IN, IMPORTED }

/**
 * A Whisper GGML model known to the app.
 *
 * [id] is the stable identity used by the registry; [fileName] is the file on
 * disk under `filesDir/whisper/`. Renaming a model only changes [displayName].
 */
data class SpeechModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val source: SpeechModelSource,
    val sizeBytes: Long,
    val modelType: String,
    val multilingual: Boolean,
    val sourceUrl: String? = null,
    val importedAt: Long? = null
) {
    val isBuiltIn: Boolean get() = source == SpeechModelSource.BUILT_IN
}

/**
 * Models the app can download itself. Imported models live in
 * [SpeechModelStore] and are appended after these.
 */
object SpeechModelCatalog {
    val TINY = SpeechModel(
        id = "tiny",
        displayName = "Multilingual (tiny, int8)",
        fileName = "ggml-tiny-q8_0.bin",
        source = SpeechModelSource.BUILT_IN,
        sizeBytes = 42L * 1024 * 1024,
        modelType = "tiny",
        multilingual = true,
        sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin"
    )

    val BASE = SpeechModel(
        id = "base",
        displayName = "Multilingual (base, int8)",
        fileName = "ggml-base-q8_0.bin",
        source = SpeechModelSource.BUILT_IN,
        sizeBytes = 78L * 1024 * 1024,
        modelType = "base",
        multilingual = true,
        sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q8_0.bin"
    )

    val SMALL = SpeechModel(
        id = "small",
        displayName = "Multilingual (small, int8)",
        fileName = "ggml-small-q8_0.bin",
        source = SpeechModelSource.BUILT_IN,
        sizeBytes = 252L * 1024 * 1024,
        modelType = "small",
        multilingual = true,
        sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin"
    )

    val BUILT_INS = listOf(TINY, BASE, SMALL)

    val DEFAULT = TINY

    /** App-managed models replaced by [DEFAULT]; deleted once a new one is in place. */
    internal val LEGACY_FILENAMES = listOf("ggml-tiny.en-q8_0.bin")

    fun builtIn(id: String): SpeechModel? = BUILT_INS.firstOrNull { it.id == id }
}
