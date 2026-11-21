package com.pranshulgg.recordmaster.core.record

import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingFormat

/** How chunks are captured for one take. Frozen when the take starts. */
enum class RecordingCaptureStrategy {
    MEDIA_RECORDER_AAC,
    PCM_WAV,
    UNSUPPORTED
}

/** How finalized chunks become the review file and the saved file. */
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
 * Turns validated preferences into the first pipeline-supported plan.
 *
 * Phase 3 wires M4A/AAC and WAV/PCM. FLAC, OGG/Opus, OGG/Vorbis, explicit
 * microphone selection, and location tagging are visible in the model but are
 * rejected here until their own pipeline phases land.
 */
object RecordingPipelinePlanner {

    fun plan(
        config: RecordingConfig,
        snapshot: RecordingCapabilitySnapshot
    ): RecordingPipelinePlan {
        val validation = RecordingCapabilities.validate(config, snapshot)
        val effective = validation.effectiveConfig
        val issues = validation.issues.toMutableList()

        val (capture, merge) = when (effective.format) {
            RecordingFormat.M4A_AAC ->
                RecordingCaptureStrategy.MEDIA_RECORDER_AAC to RecordingMergeStrategy.MPEG4_STREAM_COPY
            RecordingFormat.WAV_PCM ->
                RecordingCaptureStrategy.PCM_WAV to RecordingMergeStrategy.WAV_PCM_CONCAT
            else -> {
                issues += error("${effective.format.label} is not wired into the recorder pipeline yet.")
                RecordingCaptureStrategy.UNSUPPORTED to RecordingMergeStrategy.UNSUPPORTED
            }
        }

        if (!effective.input.isDefault) {
            issues += error("Selecting a specific microphone is not available in the recorder pipeline yet.")
        }

        if (effective.locationTaggingEnabled) {
            issues += error("Location tagging is not available in the recorder pipeline yet.")
        }

        return RecordingPipelinePlan(
            effectiveConfig = effective,
            captureStrategy = capture,
            mergeStrategy = merge,
            chunkExtension = effective.format.extension,
            finalExtension = effective.format.extension,
            issues = issues
        )
    }

    private fun error(message: String): RecordingConfigIssue =
        RecordingConfigIssue(RecordingConfigIssueSeverity.ERROR, message)
}
