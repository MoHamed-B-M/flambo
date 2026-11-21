package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.domain.AudioCodec
import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.MicrophoneInputKind
import com.pranshulgg.recordmaster.core.domain.RecordingChannels
import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingFormat
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import com.pranshulgg.recordmaster.core.record.RecordingCapabilities
import com.pranshulgg.recordmaster.core.record.RecordingCapabilitySnapshot
import com.pranshulgg.recordmaster.core.record.RecordingConfigIssueSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM checks for the new recording option model and capability rules. */
class RecordingOptionsTest {

    private fun snapshot(
        sdkInt: Int = 36,
        platformEncoders: Set<String> = setOf("audio/mp4a-latm", "audio/opus"),
        pcmCaptureAvailable: Boolean = true,
        bundledEncoders: Set<String> = setOf("flac", "vorbis"),
        inputs: List<MicrophoneInput> = emptyList(),
        defaultMaxInputChannels: Int = 2,
        inputEnumerationAvailable: Boolean = true,
        bluetoothEnumerationAvailable: Boolean = true,
        locationPermissionGranted: Boolean = true,
        locationServicesEnabled: Boolean = true
    ) = RecordingCapabilitySnapshot(
        sdkInt = sdkInt,
        platformAudioEncoderMimes = platformEncoders,
        pcmCaptureAvailable = pcmCaptureAvailable,
        bundledAudioEncoders = bundledEncoders,
        microphoneInputs = inputs,
        defaultMaxInputChannels = defaultMaxInputChannels,
        inputEnumerationAvailable = inputEnumerationAvailable,
        bluetoothEnumerationAvailable = bluetoothEnumerationAvailable,
        locationPermissionGranted = locationPermissionGranted,
        locationServicesEnabled = locationServicesEnabled
    )

    @Test
    fun formatMetadataMatchesThePlannedRecordingStrategies() {
        assertEquals("m4a", RecordingFormat.M4A_AAC.extension)
        assertEquals(AudioCodec.AAC, RecordingFormat.M4A_AAC.codec)
        assertEquals("audio/mp4a-latm", RecordingFormat.M4A_AAC.platformEncoderMime)
        assertFalse(RecordingFormat.M4A_AAC.usesExternalCodec)

        assertEquals("wav", RecordingFormat.WAV_PCM.extension)
        assertEquals(AudioCodec.PCM, RecordingFormat.WAV_PCM.codec)
        assertTrue(RecordingFormat.WAV_PCM.isLossless)

        assertEquals(29, RecordingFormat.OGG_OPUS.minSdk)
        assertEquals("audio/opus", RecordingFormat.OGG_OPUS.platformEncoderMime)

        assertEquals("flac", RecordingFormat.FLAC.bundledEncoderName)
        assertTrue(RecordingFormat.FLAC.isLossless)
        assertEquals("vorbis", RecordingFormat.OGG_VORBIS.bundledEncoderName)
        assertEquals("ogg", RecordingFormat.OGG_VORBIS.extension)
    }

    @Test
    fun storedFormatFallsBackToLegacyM4a() {
        assertSame(RecordingFormat.M4A_AAC, RecordingFormat.fromStoredValue(null))
        assertSame(RecordingFormat.M4A_AAC, RecordingFormat.fromStoredValue(""))
        assertSame(RecordingFormat.M4A_AAC, RecordingFormat.fromStoredValue("REMOVED_FORMAT"))
        assertSame(RecordingFormat.FLAC, RecordingFormat.fromStoredValue("flac"))
    }

    @Test
    fun defaultConfigPreservesExistingRecordingBehavior() {
        val config = RecordingConfig.DEFAULT
        assertSame(RecordingFormat.M4A_AAC, config.format)
        assertEquals(RecordingQuality.HIGH, config.quality)
        assertEquals(1, config.channels.channelCount)
        assertTrue(config.input.isDefault)
        assertFalse(config.locationTaggingEnabled)
    }

    @Test
    fun formatAvailabilityUsesPlatformExternalAndApiFacts() {
        val all = RecordingCapabilities.supportedFormats(snapshot())
        assertEquals(RecordingFormat.entries.toSet(), all.toSet())

        val noAac = RecordingCapabilities.supportFor(RecordingFormat.M4A_AAC, snapshot(platformEncoders = emptySet()))
        assertFalse(noAac.isSupported)
        assertTrue(noAac.reason.orEmpty().contains("AAC", ignoreCase = true))

        val oldDevice = RecordingCapabilities.supportFor(RecordingFormat.OGG_OPUS, snapshot(sdkInt = 28))
        assertFalse(oldDevice.isSupported)

        val noPcm = RecordingCapabilities.supportFor(RecordingFormat.WAV_PCM, snapshot(pcmCaptureAvailable = false))
        assertFalse(noPcm.isSupported)
    }

    @Test
    fun flacAndVorbisRequireTheBundledEncoders() {
        val noBundledCodec = snapshot(bundledEncoders = emptySet())
        assertFalse(RecordingCapabilities.supportFor(RecordingFormat.FLAC, noBundledCodec).isSupported)
        assertFalse(RecordingCapabilities.supportFor(RecordingFormat.OGG_VORBIS, noBundledCodec).isSupported)

        val onlyFlac = snapshot(bundledEncoders = setOf("flac"))
        assertTrue(RecordingCapabilities.supportFor(RecordingFormat.FLAC, onlyFlac).isSupported)
        assertFalse(RecordingCapabilities.supportFor(RecordingFormat.OGG_VORBIS, onlyFlac).isSupported)
    }

    @Test
    fun bundledEncoderNamesAreStable() {
        assertEquals("flac", RecordingFormat.FLAC.bundledEncoderName)
        assertEquals("vorbis", RecordingFormat.OGG_VORBIS.bundledEncoderName)
        assertTrue(RecordingFormat.FLAC.usesExternalCodec)
        assertTrue(RecordingFormat.OGG_VORBIS.usesExternalCodec)
    }

    @Test
    fun stereoIsDowngradedOnlyWhenInputCannotProvideTwoChannels() {
        val config = RecordingConfig(channels = RecordingChannels.STEREO)

        val monoInput = RecordingCapabilities.validate(config, snapshot(defaultMaxInputChannels = 1))
        assertEquals(RecordingChannels.MONO, monoInput.effectiveConfig.channels)
        assertTrue(monoInput.issues.any { it.severity == RecordingConfigIssueSeverity.WARNING })

        val stereoInput = RecordingCapabilities.validate(config, snapshot(defaultMaxInputChannels = 2))
        assertEquals(RecordingChannels.STEREO, stereoInput.effectiveConfig.channels)
        assertFalse(stereoInput.issues.any { it.severity == RecordingConfigIssueSeverity.WARNING })
    }

    @Test
    fun savedInputFallsBackWhenItIsNoLongerAvailable() {
        val savedInput = MicrophoneInput.fromStoredId("bt:00:11:22")
        assertEquals(MicrophoneInputKind.BLUETOOTH, savedInput.kind)

        val result = RecordingCapabilities.validate(
            RecordingConfig(input = savedInput),
            snapshot(inputs = emptyList())
        )

        assertTrue(result.effectiveConfig.input.isDefault)
        assertTrue(result.issues.any { it.message.contains("microphone", ignoreCase = true) })
        assertTrue(result.canStart)
    }

    @Test
    fun unsupportedFormatIsAnErrorAndIsNeverSilentlyReplaced() {
        val result = RecordingCapabilities.validate(
            RecordingConfig(format = RecordingFormat.FLAC),
            snapshot(bundledEncoders = emptySet())
        )

        assertFalse(result.canStart)
        assertSame(RecordingFormat.FLAC, result.effectiveConfig.format)
        assertTrue(result.issues.any { it.severity == RecordingConfigIssueSeverity.ERROR })
    }

    @Test
    fun locationValidationRequiresPermissionButToleratesGpsBeingOff() {
        val config = RecordingConfig(locationTaggingEnabled = true)

        val denied = RecordingCapabilities.validate(config, snapshot(locationPermissionGranted = false))
        assertFalse(denied.canStart)

        val gpsOff = RecordingCapabilities.validate(config, snapshot(locationServicesEnabled = false))
        assertTrue(gpsOff.canStart)
        assertTrue(gpsOff.issues.any { it.severity == RecordingConfigIssueSeverity.WARNING })
    }
}
