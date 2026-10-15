package com.jpcottin.skylens.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import com.jpcottin.skylens.astro.Observer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A location fix as the engine wants it. */
data class LocationFix(val observer: Observer, val accuracyM: Float, val timeMillis: Long)

/** Observer position from the platform location manager (fused provider). */
class LocationSource(private val context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Emits the last known fix first, then updates every ten seconds; stops when collection stops. */
    @Suppress("MissingPermission")
    fun fixes(intervalMillis: Long = 10_000L): Flow<LocationFix> = callbackFlow {
        val manager = locationManager ?: run { close(); return@callbackFlow }
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val provider = pickProvider(manager) ?: run { close(); return@callbackFlow }
        manager.getLastKnownLocation(provider)?.let { trySend(it.toFix()) }
        val listener = LocationListener { location -> trySend(location.toFix()) }
        val request = LocationRequest.Builder(intervalMillis).setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY).build()
        manager.requestLocationUpdates(provider, request, context.mainExecutor, listener)
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun pickProvider(manager: LocationManager): String? = when {
        manager.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        manager.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    private fun Location.toFix() = LocationFix(Observer(latitude, longitude, if (hasAltitude()) altitude else 0.0), accuracy, time)

    companion object {
        /** Magnetic declination (east positive) at an observer, from the World Magnetic Model. */
        fun declinationDeg(observer: Observer, epochMillis: Long): Double =
            GeomagneticField(observer.latitudeDeg.toFloat(), observer.longitudeDeg.toFloat(), observer.altitudeM.toFloat(), epochMillis).declination.toDouble()
    }
}
