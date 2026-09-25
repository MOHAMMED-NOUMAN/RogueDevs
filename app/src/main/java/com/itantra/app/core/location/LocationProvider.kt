package com.itantra.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's position from Android's own location service (GPS works with no network; no
 * Play Services). Returns null when location permission is missing or no fix is available.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Most recent fix any provider already has, newest first; may be old. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        if (!hasPermission() || manager == null) return null
        return providers().mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** A fresh fix, or null if none arrives within [timeoutMs] (a cold GPS start can be slow). */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long): Location? {
        if (!hasPermission() || manager == null) return null
        val provider = providers().firstOrNull() ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, cancel, ContextCompat.getMainExecutor(context)
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
    }

    /** Enabled providers, GPS first: it is the one that works without a network. */
    private fun providers(): List<String> = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).filter { runCatching { manager?.isProviderEnabled(it) == true }.getOrDefault(false) }
}
