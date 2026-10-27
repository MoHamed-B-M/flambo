package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.audio.EnhanceStrength
import com.pranshulgg.recordmaster.core.data.Recording
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM checks for the metadata rules shared by Home, the row sheet and the
 * playback screen. No Android runtime is required.
 */
class RecordingModelTest {

    private fun recording(tags: String = "", peaks: String = "") = Recording(
        title = "User_001",
        filePath = "/tmp/User_001.m4a",
        durationMs = 1000L,
        createdAt = 0L,
        tags = tags,
        amplitudePeaks = peaks
    )

    @Test
    fun tagListTrimsAndDropsEmptyEntries() {
        assertEquals(
            listOf("work", "ideas"),
            recording(tags = " work, ,ideas , ").tagList
        )
    }

    @Test
    fun tagListIsEmptyForBlankMetadata() {
        assertTrue(recording(tags = "   ").tagList.isEmpty())
    }

    @Test
    fun peakListIgnoresInvalidEntries() {
        assertEquals(
            listOf(0.1f, 0.9f),
            recording(peaks = "0.1, nope, 0.9").peakList
        )
    }

    @Test
    fun qualityPresetsRemainOrdered() {
        assertEquals(
            listOf(16000, 44100, 48000),
            RecordingQuality.entries.map { it.sampleRate }
        )
    }

    @Test
    fun enhanceStrengthFallsBackToBalanced() {
        assertEquals(EnhanceStrength.BALANCED, EnhanceStrength.fromPref("unknown"))
        assertEquals(EnhanceStrength.STRONG, EnhanceStrength.fromPref("strong"))
    }
}
