package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.domain.RecordingLocation
import com.pranshulgg.recordmaster.utils.formatCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM checks for location validation and display formatting. */
class LocationTaggingTest {

    @Test
    fun validCoordinatesAreAccepted() {
        assertTrue(RecordingLocation.isValid(37.7749, -122.4194))
        assertTrue(RecordingLocation.isValid(0.0, 0.0))
        assertTrue(RecordingLocation.isValid(-90.0, 180.0))
    }

    @Test
    fun impossibleCoordinatesAreRejected() {
        assertFalse(RecordingLocation.isValid(90.1, 0.0))
        assertFalse(RecordingLocation.isValid(0.0, -180.1))
        assertFalse(RecordingLocation.isValid(Double.NaN, 0.0))
        assertFalse(RecordingLocation.isValid(0.0, Double.NaN))

        assertNull(RecordingLocation.create(91.0, 0.0, 1_000L))
        assertEquals(
            12.5,
            RecordingLocation.create(12.5, 4.25, 1_000L)?.latitude ?: 0.0,
            0.0001
        )
    }

    @Test
    fun coordinatesUseStableDigitsAndSigns() {
        assertEquals("37.77490, -122.41942", formatCoordinates(37.7749, -122.41942))
        assertEquals("0.00000, 0.00000", formatCoordinates(0.0, 0.0))
    }
}
