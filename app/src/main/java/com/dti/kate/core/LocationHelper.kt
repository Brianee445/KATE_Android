package com.dti.kate.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Returns Pair(latitude, longitude) from the best last-known fix, or null. */
    fun getLastKnownLocation(): Pair<Double, Double>? {
        if (!hasPermission()) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        var best: android.location.Location? = null

        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || location.accuracy < best.accuracy) {
                    best = location
                }
            } catch (e: SecurityException) {
                // Permission revoked between check and call
            }
        }

        return best?.let { Pair(it.latitude, it.longitude) }
    }
}
