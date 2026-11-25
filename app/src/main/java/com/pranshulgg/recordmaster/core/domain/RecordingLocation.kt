package com.pranshulgg.recordmaster.core.domain

/**
 * A one-shot coordinate captured for a take. Kept free of Android imports so
 * validation and mapping can be covered by local JVM tests.
 */
data class RecordingLocation(
    val latitude: Double,
    val longitude: Double,
    val capturedAtMs: Long
) {
    companion object {
        fun isValid(latitude: Double, longitude: Double): Boolean =
            !latitude.isNaN() &&
                !longitude.isNaN() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0

        /** Returns null instead of persisting impossible coordinates. */
        fun create(latitude: Double, longitude: Double, capturedAtMs: Long): RecordingLocation? =
            if (isValid(latitude, longitude)) {
                RecordingLocation(latitude, longitude, capturedAtMs)
            } else {
                null
            }
    }
}
