package com.pranshulgg.recordmaster.core.record

import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingFormat

/** `MediaRecorder.setPreferredDevice` is only available from Android 9. */
private const val MEDIA_RECORDER_INPUT_SELECTION_MIN_SDK = 28

/** How chunks are captured for one take. Frozen when the take starts. */
enum class RecordingCaptureStrategy {
    MEDIA_RECORDER_AAC,
    PCM_WAV,
    UNSUPPORTED
}

/** How finalized chunks become the intermediate WAV or review file. */
enum class RecordingMergeStrategy {
    MPEG4_STREAM_COPY,
    WAV_PCM_CONCAT,
    UNSUPPORTED
}

/**
 * Complete, immutable instruction set for one take.
 *
 * This is deliberately JVM-only: capability facts come from
 * [RecordingCapabilitySnapshot], and the Android controller merely executes the
 * plan. A plan can never switch formats silently; unsupported formats are hard
 * errors rather than a fallback to M4A.
 */
data class RecordingPipelinePlan(
    val effectiveConfig: RecordingConfig,
    val captureStrategy: RecordingCaptureStrategy,
    val mergeStrategy: RecordingMergeStrategy,
    val finalEncoding: FinalAudioEncoding,
    val chunkExtension: String,
    val finalExtension: String,
    val issues: List<RecordingConfigIssue>
) {
    val canStart: Boolean get() = issues.none { it.severity == RecordingConfigIssueSeverity.ERROR }

    val primaryError: String? get() = issues.firstOrNull {
        it.severity == RecordingConfigIssueSeverity.ERROR
    }?.message
}

/**
 * Turns validated preferences into a pipeline-supported plan.
 *
 * M4A/AAC uses MediaRecorder chunks stream-copied into the final file. Every
 * other format captures canonical PCM WAV chunks; WAV is stream-copied directly,
 * while FLAC, OGG/Vorbis and OGG/Opus are encoded only once, after the take
 * ends. Explicit microphone selection and one-shot location tagging are
 * supported when capabilities allow them.
 */
object RecordingPipelinePlanner {

    fun plan(
        config: RecordingConfig,
        snapshot: RecordingCapabilitySnapshot
    ): RecordingPipelinePlan {
        val validation = RecordingCapabilities.validate(config, snapshot)
        val effective = validation.effectiveConfig
        val issues = validation.issues.toMutableList()

        val (capture, merge, finalEncoding) = when (effective.format) {
            RecordingFormat.M4A_AAC -> Triple(
                RecordingCaptureStrategy.MEDIA_RECORDER_AAC,
                RecordingMergeStrategy.MPEG4_STREAM_COPY,
                FinalAudioEncoding.NONE
            )

            RecordingFormat.WAV_PCM -> Triple(
                RecordingCaptureStrategy.PCM_WAV,
                RecordingMergeStrategy.WAV_PCM_CONCAT,
                FinalAudioEncoding.NONE
            )

            RecordingFormat.FLAC -> Triple(
                RecordingCaptureStrategy.PCM_WAV,
                RecordingMergeStrategy.WAV_PCM_CONCAT,
                FinalAudioEncoding.FLAC
            )

            RecordingFormat.OGG_VORBIS -> Triple(
                RecordingCaptureStrategy.PCM_WAV,
                RecordingMergeStrategy.WAV_PCM_CONCAT,
                FinalAudioEncoding.VORBIS
            )

            RecordingFormat.OGG_OPUS -> Triple(
                RecordingCaptureStrategy.PCM_WAV,
                RecordingMergeStrategy.WAV_PCM_CONCAT,
                FinalAudioEncoding.OPUS
            )
        }

        if (capture == RecordingCaptureStrategy.PCM_WAV && !snapshot.pcmCaptureAvailable) {
            issues += error(
                "${effective.format.label} needs PCM microphone capture, which is not available on this device."
            )
        }

        if (!effective.input.isDefault &&
            capture == RecordingCaptureStrategy.MEDIA_RECORDER_AAC &&
            snapshot.sdkInt < MEDIA_RECORDER_INPUT_SELECTION_MIN_SDK
        ) {
            issues += error(
                "Selecting a specific microphone with M4A/AAC requires Android 9 or later. " +
                    "Use a PCM-based format on this device."
            )
        }

        return RecordingPipelinePlan(
            effectiveConfig = effective,
            captureStrategy = capture,
            mergeStrategy = merge,
            finalEncoding = finalEncoding,
            chunkExtension = if (capture == RecordingCaptureStrategy.PCM_WAV) {
                "wav"
            } else {
                effective.format.extension
            },
            finalExtension = effective.format.extension,
            issues = issues
        )
    }

    private fun error(message: String): RecordingConfigIssue =
        RecordingConfigIssue(RecordingConfigIssueSeverity.ERROR, message)
}
