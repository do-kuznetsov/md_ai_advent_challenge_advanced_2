package com.sibgear.weather.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

public object LocationCoreModule {
    public fun provide(context: Context): CurrentLocationProvider = AndroidCurrentLocationProvider(context)
}

internal class AndroidCurrentLocationProvider(
    private val context: Context,
) : CurrentLocationProvider {
    override suspend fun currentLocation(): Result<LocationCoordinates> {
        if (!hasCoarseLocationPermission()) {
            return Result.failure(LocationUnavailableException())
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            return Result.failure(LocationUnavailableException())
        }

        return readLastKnownLocation(locationManager)
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocation(locationManager: LocationManager): Result<LocationCoordinates> =
        try {
            locationManager
                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.let { Result.success(LocationCoordinates(it.latitude, it.longitude)) }
                ?: Result.failure(LocationUnavailableException())
        } catch (_: SecurityException) {
            Result.failure(LocationUnavailableException())
        }

    private fun hasCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
