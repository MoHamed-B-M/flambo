package com.flambo.recorder.domain

enum class RecordingQuality(
    val label: String,
    val description: String,
    val sampleRate: Int,
    val bitRate: Int
) {
    VOICE(
        label = "Voice",
        description = "Small files, great for speech. 16 kHz · 32 kbps",
        sampleRate = 16000,
        bitRate = 32000
    ),
    HIGH(
        label = "High Quality",
        description = "Balanced for everyday use. 44.1 kHz · 128 kbps",
        sampleRate = 44100,
        bitRate = 128000
    ),
    LOSSLESS(
        label = "Lossless-ish",
        description = "Larger files, maximum detail. 48 kHz · 192 kbps",
        sampleRate = 48000,
        bitRate = 192000
    )
}
