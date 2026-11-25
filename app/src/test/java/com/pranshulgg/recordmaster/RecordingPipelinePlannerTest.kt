package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.RecordingChannels
import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingFormat
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import com.pranshulgg.recordmaster.core.record.FinalAudioEncoding
import com.pranshulgg.recordmaster.core.record.RecordingCapabilitySnapshot
import com.pranshulgg.recordmaster.core.record.RecordingCaptureStrategy
import com.pranshulgg.recordmaster.core.record.RecordingConfigIssueSeverity
import com.pranshulgg.recordmaster.core.record.RecordingMergeStrategy
import com.pranshulgg.recordmaster.core.record.RecordingPipelinePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM checks for the recorder/merge/final-encode plan. */
class RecordingPipelinePlannerTest {

    private fun snapshot(
        platformEncoders: Set<String> = setOf("audio/mp4a-latm", "audio/opus"),
        pcmCaptureAvailable: Boolean = true,
        defaultMaxInputChannels: Int = 2,
        sdkInt: Int = 36
    ) = RecordingCapabilitySnapshot(
        sdkInt = sdkInt,
        platformAudioEncoderMimes = platformEncoders,
        pcmCaptureAvailable = pcmCaptureAvailable,
        bundledAudioEncoders = setOf("flac", "vorbis"),
        microphoneInputs = emptyList(),
        defaultMaxInputChannels = defaultMaxInputChannels,
        inputEnumerationAvailable = true,
        bluetoothEnumerationAvailable = true,
        locationPermissionGranted = true,
        locationServicesEnabled = true
    )

    @Test
    fun defaultPlanKeepsTheExistingM4aPipeline() {
        val plan = RecordingPipelinePlanner.plan(RecordingConfig.DEFAULT, snapshot())

        assertTrue(plan.canStart)
        assertNull(plan.primaryError)
        assertSame(RecordingFormat.M4A_AAC, plan.effectiveConfig.format)
        assertEquals(RecordingQuality.HIGH, plan.effectiveConfig.quality)
        assertEquals(RecordingCaptureStrategy.MEDIA_RECORDER_AAC, plan.captureStrategy)
        assertEquals(RecordingMergeStrategy.MPEG4_STREAM_COPY, plan.mergeStrategy)
        assertEquals(FinalAudioEncoding.NONE, plan.finalEncoding)
        assertEquals("m4a", plan.chunkExtension)
        assertEquals("m4a", plan.finalExtension)
    }

    @Test
    fun wavPlanSelectsPcmCaptureAndWavMergeWithoutFinalEncode() {
        val plan = RecordingPipelinePlanner.plan(
            RecordingConfig(format = RecordingFormat.WAV_PCM),
            snapshot()
        )

        assertTrue(plan.canStart)
        assertSame(RecordingFormat.WAV_PCM, plan.effectiveConfig.format)
        assertEquals(RecordingCaptureStrategy.PCM_WAV, plan.captureStrategy)
        assertEquals(RecordingMergeStrategy.WAV_PCM_CONCAT, plan.mergeStrategy)
        assertEquals(FinalAudioEncoding.NONE, plan.finalEncoding)
        assertEquals("wav", plan.chunkExtension)
        assertEquals("wav", plan.finalExtension)
    }

    @Test
    fun compressedAndLosslessFormatsCaptureWavThenFinalEncode() {
        listOf(
            RecordingFormat.FLAC to FinalAudioEncoding.FLAC,
            RecordingFormat.OGG_VORBIS to FinalAudioEncoding.VORBIS,
            RecordingFormat.OGG_OPUS to FinalAudioEncoding.OPUS
        ).forEach { (format, encoding) ->
            val plan = RecordingPipelinePlanner.plan(
                RecordingConfig(format = format),
                snapshot()
            )

            assertTrue("${format.label} should be supported", plan.canStart)
            assertSame(format, plan.effectiveConfig.format)
            assertEquals(RecordingCaptureStrategy.PCM_WAV, plan.captureStrategy)
            assertEquals(RecordingMergeStrategy.WAV_PCM_CONCAT, plan.mergeStrategy)
            assertEquals(encoding, plan.finalEncoding)
            assertEquals("wav", plan.chunkExtension)
            assertEquals(format.extension, plan.finalExtension)
        }
    }

    @Test
    fun finalEncodeFormatsHardFailWhenPcmCaptureIsMissing() {
        listOf(RecordingFormat.FLAC, RecordingFormat.OGG_VORBIS, RecordingFormat.OGG_OPUS)
            .forEach { format ->
                val plan = RecordingPipelinePlanner.plan(
                    RecordingConfig(format = format),
                    snapshot(pcmCaptureAvailable = false)
                )

                assertFalse(plan.canStart)
                assertSame(format, plan.effectiveConfig.format)
                assertTrue(plan.primaryError.orEmpty().contains("PCM", ignoreCase = true))
            }
    }

    @Test
    fun unsupportedCapabilitiesStillHardFailWithoutChangingTheFormat() {
        val noFlac = RecordingPipelinePlanner.plan(
            RecordingConfig(format = RecordingFormat.FLAC),
            snapshot().copy(bundledAudioEncoders = emptySet())
        )
        assertFalse(noFlac.canStart)
        assertSame(RecordingFormat.FLAC, noFlac.effectiveConfig.format)

        val oldOpus = RecordingPipelinePlanner.plan(
            RecordingConfig(format = RecordingFormat.OGG_OPUS),
            snapshot(sdkInt = 28)
        )
        assertFalse(oldOpus.canStart)
        assertSame(RecordingFormat.OGG_OPUS, oldOpus.effectiveConfig.format)
        assertTrue(oldOpus.primaryError.orEmpty().contains("Android 10", ignoreCase = true))
    }

    @Test
    fun stereoDowngradeDoesNotBlockTheTake() {
        val plan = RecordingPipelinePlanner.plan(
            RecordingConfig(channels = RecordingChannels.STEREO),
            snapshot(defaultMaxInputChannels = 1)
        )

        assertTrue(plan.canStart)
        assertEquals(RecordingChannels.MONO, plan.effectiveConfig.channels)
        assertTrue(plan.issues.any { it.severity == RecordingConfigIssueSeverity.WARNING })
    }

    @Test
    fun availableExplicitMicrophoneIsSupportedForM4a() {
        val explicitInput = MicrophoneInput.fromStoredId("bt:00:11:22")
        val plan = RecordingPipelinePlanner.plan(
            RecordingConfig(input = explicitInput),
            snapshot().copy(microphoneInputs = listOf(explicitInput))
        )

        assertTrue(plan.canStart)
        assertNull(plan.primaryError)
        assertEquals(explicitInput, plan.effectiveConfig.input)
        assertEquals(RecordingCaptureStrategy.MEDIA_RECORDER_AAC, plan.captureStrategy)
    }

    @Test
    fun explicitMicrophoneWithM4aIsRejectedBelowApi28() {
        val explicitInput = MicrophoneInput.fromStoredId("bt:00:11:22")
        val plan = RecordingPipelinePlanner.plan(
            RecordingConfig(input = explicitInput),
            snapshot(sdkInt = 27).copy(microphoneInputs = listOf(explicitInput))
        )

        assertFalse(plan.canStart)
        assertTrue(plan.primaryError.orEmpty().contains("Android 9", ignoreCase = true))
    }

    @Test
    fun locationTaggingIsSupportedWhenPermissionIsGranted() {
        val plan = RecordingPipelinePlanner.plan(
            RecordingConfig(locationTaggingEnabled = true),
            snapshot()
        )

        assertTrue(plan.canStart)
        assertNull(plan.primaryError)
        assertTrue(plan.effectiveConfig.locationTaggingEnabled)
    }

    @Test
    fun locationTaggingStillRequiresPermission() {
        val plan = RecordingPipelinePlanner.plan(
            RecordingConfig(locationTaggingEnabled = true),
            snapshot().copy(locationPermissionGranted = false)
        )

        assertFalse(plan.canStart)
        assertTrue(plan.primaryError.orEmpty().contains("permission", ignoreCase = true))
    }

    @Test
    fun capabilityFailuresStillBlockEveryFormat() {
        val noAac = RecordingPipelinePlanner.plan(
            RecordingConfig.DEFAULT,
            snapshot(platformEncoders = emptySet())
        )
        assertFalse(noAac.canStart)

        val noPcm = RecordingPipelinePlanner.plan(
            RecordingConfig(format = RecordingFormat.WAV_PCM),
            snapshot(pcmCaptureAvailable = false)
        )
        assertFalse(noPcm.canStart)
        assertTrue(noPcm.primaryError.orEmpty().contains("PCM", ignoreCase = true))
    }
}
