package com.pranshulgg.recordmaster.core.record

/**
 * Final encode applied after WAV chunks have been merged. Kept JVM-only so the
 * pipeline plan can be unit-tested without Android codec classes.
 */
enum class FinalAudioEncoding {
    NONE,
    FLAC,
    VORBIS,
    OPUS
}
