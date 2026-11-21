package com.pranshulgg.recordmaster.core.domain

/** Channel selection for the next take. */
enum class RecordingChannels(
    val channelCount: Int,
    val label: String,
    val description: String
) {
    MONO(1, "Mono", "One channel. Compatible with every microphone and format."),
    STEREO(2, "Stereo", "Two channels when the selected input supports them.")
}

/** What kind of physical input a saved or discovered microphone represents. */
enum class MicrophoneInputKind {
    DEFAULT,
    BUILT_IN,
    WIRED,
    BLUETOOTH,
    USB,
    UNKNOWN
}

/**
 * A selectable microphone. [id] is persisted and must be stable for as long as
 * the device exposes the same input; [maxChannels] is used to validate stereo.
 */
data class MicrophoneInput(
    val id: String,
    val displayName: String,
    val kind: MicrophoneInputKind,
    val maxChannels: Int = 1
) {
    val isDefault: Boolean get() = id == DEFAULT_ID

    companion object {
        const val DEFAULT_ID = "default"

        val DEFAULT = MicrophoneInput(
            id = DEFAULT_ID,
            displayName = "Default microphone",
            kind = MicrophoneInputKind.DEFAULT
        )

        /** Rebuilds a saved selection without pretending we know its capabilities. */
        fun fromStoredId(id: String?): MicrophoneInput {
            val stored = id?.trim().orEmpty()
            if (stored.isBlank() || stored.equals(DEFAULT_ID, ignoreCase = true)) return DEFAULT

            val kind = when {
                stored.startsWith("bt:", ignoreCase = true) -> MicrophoneInputKind.BLUETOOTH
                stored.startsWith("usb:", ignoreCase = true) -> MicrophoneInputKind.USB
                stored.startsWith("wired:", ignoreCase = true) -> MicrophoneInputKind.WIRED
                stored.startsWith("builtin:", ignoreCase = true) -> MicrophoneInputKind.BUILT_IN
                else -> MicrophoneInputKind.UNKNOWN
            }
            val label = when (kind) {
                MicrophoneInputKind.BLUETOOTH -> "Bluetooth microphone"
                MicrophoneInputKind.USB -> "USB microphone"
                MicrophoneInputKind.WIRED -> "Wired headset microphone"
                MicrophoneInputKind.BUILT_IN -> "Built-in microphone"
                else -> "Selected microphone"
            }
            return MicrophoneInput(
                id = stored,
                displayName = label,
                kind = kind,
                maxChannels = 1
            )
        }
    }
}

/**
 * Immutable snapshot of recording preferences taken when a take starts.
 *
 * The controller will receive this object instead of reading live preferences
 * mid-recording, so a settings change cannot silently alter an active file.
 */
data class RecordingConfig(
    val format: RecordingFormat = RecordingFormat.DEFAULT,
    val quality: RecordingQuality = RecordingQuality.HIGH,
    val channels: RecordingChannels = RecordingChannels.MONO,
    val input: MicrophoneInput = MicrophoneInput.DEFAULT,
    val locationTaggingEnabled: Boolean = false
) {
    companion object {
        val DEFAULT: RecordingConfig = RecordingConfig()
    }
}
