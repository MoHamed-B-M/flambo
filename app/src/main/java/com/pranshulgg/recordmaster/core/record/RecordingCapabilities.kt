package com.pranshulgg.recordmaster.core.record

import com.pranshulgg.recordmaster.core.domain.AudioCodec
import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.RecordingChannels
import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingFormat
import java.util.Locale

/** Runtime facts gathered before a take starts. No Android types leak into tests. */
data class RecordingCapabilitySnapshot(
    val sdkInt: Int,
    val platformAudioEncoderMimes: Set<String>,
    val pcmCaptureAvailable: Boolean,
    val bundledAudioEncoders: Set<String>,
    val microphoneInputs: List<MicrophoneInput>,
    val defaultMaxInputChannels: Int,
    val inputEnumerationAvailable: Boolean,
    val bluetoothEnumerationAvailable: Boolean,
    val locationPermissionGranted: Boolean,
    val locationServicesEnabled: Boolean
)

data class FormatSupport(
    val format: RecordingFormat,
    val isSupported: Boolean,
    val reason: String? = null
)

enum class RecordingConfigIssueSeverity {
    ERROR,
    WARNING
}

data class RecordingConfigIssue(
    val severity: RecordingConfigIssueSeverity,
    val message: String
)

/**
 * Validated configuration plus any graceful adjustments made before recording.
 * [effectiveConfig] never changes the selected format; unsupported formats are
 * hard errors so a take cannot silently become a different file type.
 */
data class RecordingConfigValidation(
    val effectiveConfig: RecordingConfig,
    val issues: List<RecordingConfigIssue>
) {
    val canStart: Boolean get() = issues.none { it.severity == RecordingConfigIssueSeverity.ERROR }
}

/** Pure format/channel/location validation shared by settings and the recorder. */
object RecordingCapabilities {

    fun supportFor(format: RecordingFormat, snapshot: RecordingCapabilitySnapshot): FormatSupport {
        if (snapshot.sdkInt < format.minSdk) {
            return unsupported(format, "${format.label} requires ${format.minSdkLabel} or later.")
        }

        return when (format.codec) {
            AudioCodec.AAC -> platformEncoderSupport(format, snapshot, "AAC")
            AudioCodec.PCM -> {
                if (snapshot.pcmCaptureAvailable) {
                    FormatSupport(format, true)
                } else {
                    unsupported(format, "PCM microphone capture is not available on this device.")
                }
            }
            AudioCodec.OPUS -> platformEncoderSupport(format, snapshot, "Opus")
            AudioCodec.FLAC, AudioCodec.VORBIS -> bundledEncoderSupport(format, snapshot)
        }
    }

    fun supportedFormats(snapshot: RecordingCapabilitySnapshot): List<RecordingFormat> =
        RecordingFormat.entries.filter { supportFor(it, snapshot).isSupported }

    fun validate(
        config: RecordingConfig,
        snapshot: RecordingCapabilitySnapshot
    ): RecordingConfigValidation {
        val issues = mutableListOf<RecordingConfigIssue>()
        var effective = config

        val formatSupport = supportFor(config.format, snapshot)
        if (!formatSupport.isSupported) {
            issues += error(
                formatSupport.reason ?: "${config.format.label} is not supported on this device."
            )
        }

        if (!effective.input.isDefault) {
            val stillAvailable = snapshot.microphoneInputs.any { it.id == effective.input.id }
            if (!stillAvailable) {
                effective = effective.copy(input = MicrophoneInput.DEFAULT)
                issues += warning("The selected microphone is no longer available; using the default microphone.")
            }
        }

        if (effective.channels == RecordingChannels.STEREO) {
            val maxChannels = if (effective.input.isDefault) {
                snapshot.defaultMaxInputChannels
            } else {
                snapshot.microphoneInputs.firstOrNull { it.id == effective.input.id }?.maxChannels ?: 1
            }
            if (!snapshot.inputEnumerationAvailable || maxChannels < RecordingChannels.STEREO.channelCount) {
                effective = effective.copy(channels = RecordingChannels.MONO)
                issues += warning("This microphone does not support stereo; recording one channel instead.")
            }
        }

        if (config.locationTaggingEnabled) {
            if (!snapshot.locationPermissionGranted) {
                issues += error("Location permission is required before location tagging can be enabled.")
            } else if (!snapshot.locationServicesEnabled) {
                issues += warning("Location services are off; coordinates will not be attached to this take.")
            }
        }

        return RecordingConfigValidation(effective, issues)
    }

    private fun platformEncoderSupport(
        format: RecordingFormat,
        snapshot: RecordingCapabilitySnapshot,
        encoderName: String
    ): FormatSupport {
        val mime = format.platformEncoderMime
        if (mime == null || snapshot.platformAudioEncoderMimes.none { it.equals(mime, ignoreCase = true) }) {
            return unsupported(format, "$encoderName encoder is not available on this device.")
        }
        return FormatSupport(format, true)
    }

    private fun bundledEncoderSupport(
        format: RecordingFormat,
        snapshot: RecordingCapabilitySnapshot
    ): FormatSupport {
        val encoder = format.bundledEncoderName?.lowercase(Locale.US)
        if (encoder == null || snapshot.bundledAudioEncoders.none { it.equals(encoder, ignoreCase = true) }) {
            return unsupported(format, "${format.label} encoder is not available in the bundled codec libraries.")
        }
        return FormatSupport(format, true)
    }

    private fun unsupported(format: RecordingFormat, reason: String): FormatSupport =
        FormatSupport(format, false, reason)

    private fun error(message: String): RecordingConfigIssue =
        RecordingConfigIssue(RecordingConfigIssueSeverity.ERROR, message)

    private fun warning(message: String): RecordingConfigIssue =
        RecordingConfigIssue(RecordingConfigIssueSeverity.WARNING, message)
}
