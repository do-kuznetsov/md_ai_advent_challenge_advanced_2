package com.sibgear.weather.core.location

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidCurrentLocationProvider(
    private val context: Context,
) : CurrentLocationProvider {

    override suspend fun currentLocation(): Result<Coordinates> = runCatching {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            val locationManager = context.getSystemService(LocationManager::class.java)

            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.NETWORK_PROVIDER,
                cancellationSignal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (location == null) {
                    continuation.resumeWith(Result.failure(LocationUnavailableException()))
                } else {
                    continuation.resume(Coordinates(location.latitude, location.longitude))
                }
            }

            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }
        }
    }
}
