package com.sibgear.weather.core.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
public object LocationCoreModule {

    public fun provide(): CurrentLocationProvider = IosCurrentLocationProvider()
}

@OptIn(ExperimentalForeignApi::class)
internal class IosCurrentLocationProvider : CurrentLocationProvider {

    private val locationManager: CLLocationManager = CLLocationManager()
    private var continuation: kotlinx.coroutines.CancellableContinuation<Result<LocationCoordinates>>? = null
    private val delegate: LocationDelegate = LocationDelegate(::finish)

    init {
        locationManager.delegate = delegate
    }

    override suspend fun currentLocation(): Result<LocationCoordinates> =
        suspendCancellableCoroutine { current ->
            continuation = current
            current.invokeOnCancellation { continuation = null }
            locationManager.requestLocation()
        }

    private fun finish(result: Result<LocationCoordinates>) {
        continuation?.takeIf { it.isActive }?.resume(result)
        continuation = null
    }

    private class LocationDelegate(
        private val onResult: (Result<LocationCoordinates>) -> Unit,
    ) : NSObject(),
        CLLocationManagerDelegateProtocol {

        override fun locationManager(
            manager: CLLocationManager,
            didUpdateLocations: List<*>,
        ) {
            val location = didUpdateLocations.firstOrNull() as? CLLocation
            val result =
                location?.let {
                    Result.success(
                        it.coordinate.useContents {
                            LocationCoordinates(latitude = latitude, longitude = longitude)
                        },
                    )
                } ?: Result.failure(LocationUnavailableException())
            onResult(result)
        }

        override fun locationManager(
            manager: CLLocationManager,
            didFailWithError: NSError,
        ) {
            onResult(Result.failure(LocationUnavailableException()))
        }
    }
}
