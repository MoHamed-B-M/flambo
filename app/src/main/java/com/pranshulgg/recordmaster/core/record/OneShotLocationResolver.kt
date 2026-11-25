package com.pranshulgg.recordmaster.core.record

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.pranshulgg.recordmaster.core.domain.RecordingLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Resolves one coordinate when a take starts. Location is best-effort: a denied
 * permission, disabled services, or a failed fix simply yields null so the
 * recording itself is never delayed or blocked.
 */
class OneShotLocationResolver(private val context: Context) {

    suspend fun current(): RecordingLocation? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            freshFix(manager, provider)
        } else {
            cachedFix(manager)
        } ?: return null

        return RecordingLocation.create(location.latitude, location.longitude, System.currentTimeMillis())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun freshFix(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { runCatching { signal.cancel() } }
                @SuppressLint("MissingPermission")
                manager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context)
                ) { fix ->
                    if (continuation.isActive) continuation.resume(fix)
                }
            }
        }?.takeIf { usable(it) }

    /** Older devices get a recent cached fix; we never wait or burn battery here. */
    private fun cachedFix(manager: LocationManager): Location? = listOf(
        lastKnown(manager, LocationManager.GPS_PROVIDER),
        lastKnown(manager, LocationManager.NETWORK_PROVIDER)
    ).filterNotNull()
        .filter { usable(it) && !stale(it) }
        .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, provider: String): Location? =
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()

    private fun usable(location: Location): Boolean =
        RecordingLocation.isValid(location.latitude, location.longitude)

    private fun stale(location: Location): Boolean =
        System.currentTimeMillis() - location.time > MAX_FIX_AGE_MS

    private companion object {
        const val FIX_TIMEOUT_MS = 3_000L
        const val MAX_FIX_AGE_MS = 5 * 60 * 1_000L
    }
}
