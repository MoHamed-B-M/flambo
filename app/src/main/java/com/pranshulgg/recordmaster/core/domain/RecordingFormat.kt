package com.pranshulgg.recordmaster.core.domain

/** Codec carried inside a recorded file. Kept platform-neutral for JVM tests. */
enum class AudioCodec {
    AAC,
    PCM,
    OPUS,
    FLAC,
    VORBIS
}

/**
 * A user-selectable recording format.
 *
 * The metadata here is the single source of truth for later pipeline work:
 * file extension, MIME type, minimum API, and which encoder/muxer family must
 * be used. Defaults preserve the app's existing M4A/AAC behavior.
 */
enum class RecordingFormat(
    val label: String,
    val shortLabel: String,
    val description: String,
    val extension: String,
    val mimeType: String,
    val codec: AudioCodec,
    val minSdk: Int,
    val minSdkLabel: String,
    val platformEncoderMime: String? = null,
    val bundledEncoderName: String? = null,
    val isLossless: Boolean = false
) {
    M4A_AAC(
        label = "M4A (AAC)",
        shortLabel = "M4A",
        description = "Default. Widely compatible compressed audio.",
        extension = "m4a",
        mimeType = "audio/mp4",
        codec = AudioCodec.AAC,
        minSdk = 24,
        minSdkLabel = "Android 7.0",
        platformEncoderMime = "audio/mp4a-latm"
    ),
    WAV_PCM(
        label = "WAV (PCM)",
        shortLabel = "WAV",
        description = "Uncompressed audio. Larger files, no re-encoding.",
        extension = "wav",
        mimeType = "audio/wav",
        codec = AudioCodec.PCM,
        minSdk = 24,
        minSdkLabel = "Android 7.0",
        isLossless = true
    ),
    OGG_OPUS(
        label = "OGG (Opus)",
        shortLabel = "OGG Opus",
        description = "Efficient modern compressed audio on supported devices.",
        extension = "ogg",
        mimeType = "audio/ogg",
        codec = AudioCodec.OPUS,
        minSdk = 29,
        minSdkLabel = "Android 10",
        platformEncoderMime = "audio/opus"
    ),
    FLAC(
        label = "FLAC",
        shortLabel = "FLAC",
        description = "True lossless compression using the bundled FLAC encoder.",
        extension = "flac",
        mimeType = "audio/flac",
        codec = AudioCodec.FLAC,
        minSdk = 24,
        minSdkLabel = "Android 7.0",
        bundledEncoderName = "flac",
        isLossless = true
    ),
    OGG_VORBIS(
        label = "OGG (Vorbis)",
        shortLabel = "OGG Vorbis",
        description = "Open compressed audio using the bundled Vorbis encoder.",
        extension = "ogg",
        mimeType = "audio/ogg",
        codec = AudioCodec.VORBIS,
        minSdk = 24,
        minSdkLabel = "Android 7.0",
        bundledEncoderName = "vorbis"
    );

    /** True when the format needs one of the bundled encoder libraries. */
    val usesExternalCodec: Boolean get() = bundledEncoderName != null

    companion object {
        val DEFAULT: RecordingFormat = M4A_AAC

        /** Unknown or stale stored values always fall back to the legacy format. */
        fun fromStoredValue(value: String?): RecordingFormat =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
