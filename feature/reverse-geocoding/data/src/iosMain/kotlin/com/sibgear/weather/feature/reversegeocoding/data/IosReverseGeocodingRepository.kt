package com.sibgear.weather.feature.reversegeocoding.data

import com.sibgear.weather.feature.reversegeocoding.domain.CityName
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark

@OptIn(ExperimentalForeignApi::class)
internal class IosReverseGeocodingRepository : ReverseGeocodingRepository {

    override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> = runCatching {
        suspendCancellableCoroutine { continuation ->
            CLGeocoder().reverseGeocodeLocation(CLLocation(latitude, longitude)) { placemarks, error ->
                if (error != null) {
                    continuation.resumeWith(
                        Result.failure(IllegalStateException(error.localizedDescription)),
                    )
                } else {
                    continuation.resume(
                        (placemarks?.firstOrNull() as? CLPlacemark)?.locality?.let(::CityName),
                    )
                }
            }
        }
    }
}
