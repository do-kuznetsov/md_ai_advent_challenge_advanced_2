package com.sibgear.weather.core.location

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class IosCurrentLocationProvider : CurrentLocationProvider {

    private val locationManager: CLLocationManager = CLLocationManager()
    private var continuation: kotlinx.coroutines.CancellableContinuation<Coordinates>? = null
    private val delegate: IosLocationDelegate = IosLocationDelegate(
        onLocationUpdated = ::handleLocationUpdate,
        onLocationFailed = ::handleLocationFailure,
    )

    init {
        locationManager.delegate = delegate
    }

    override suspend fun currentLocation(): Result<Coordinates> = runCatching {
        suspendCancellableCoroutine { currentContinuation ->
            continuation = currentContinuation
            locationManager.requestLocation()
            currentContinuation.invokeOnCancellation {
                continuation = null
            }
        }
    }

    private fun handleLocationUpdate(didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        val currentContinuation = continuation ?: return
        continuation = null
        if (location == null) {
            currentContinuation.resumeWith(Result.failure(LocationUnavailableException()))
        } else {
            val coordinates = location.coordinate.useContents {
                Coordinates(
                    latitude = latitude,
                    longitude = longitude,
                )
            }
            currentContinuation.resume(
                coordinates,
            )
        }
    }

    private fun handleLocationFailure() {
        val currentContinuation = continuation ?: return
        continuation = null
        currentContinuation.resumeWith(Result.failure(LocationUnavailableException()))
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosLocationDelegate(
    private val onLocationUpdated: (List<*>) -> Unit,
    private val onLocationFailed: () -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        onLocationUpdated(didUpdateLocations)
    }

    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: NSError,
    ) {
        onLocationFailed()
    }
}
