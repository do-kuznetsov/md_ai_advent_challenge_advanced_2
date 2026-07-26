package com.sibgear.weather.feature.reversegeocoding.data

import com.sibgear.weather.feature.reversegeocoding.domain.CityName
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
public object ReverseGeocodingDataModule {
    public fun provide(): ReverseGeocodingRepository = IosReverseGeocodingRepository()
}

@OptIn(ExperimentalForeignApi::class)
internal class IosReverseGeocodingRepository : ReverseGeocodingRepository {
    override suspend fun resolveCityName(
        latitude: Double,
        longitude: Double,
    ): CityName? =
        suspendCancellableCoroutine { continuation ->
            val geocoder = CLGeocoder()
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.reverseGeocodeLocation(CLLocation(latitude, longitude)) { placemarks, _ ->
                val cityName =
                    (placemarks?.firstOrNull() as? CLPlacemark)
                        ?.locality
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::CityName)
                if (continuation.isActive) {
                    continuation.resume(cityName)
                }
            }
        }
}
