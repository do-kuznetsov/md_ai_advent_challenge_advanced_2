package com.sibgear.weather.feature.reversegeocoding.data

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.sibgear.weather.feature.reversegeocoding.domain.CityName
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class AndroidReverseGeocodingRepository(
    private val context: Context,
) : ReverseGeocodingRepository {

    override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> = runCatching {
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                Geocoder(context).getFromLocation(latitude, longitude, 1) { addresses ->
                    continuation.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) {
                Geocoder(context).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }

        address?.locality?.let(::CityName)
    }
}
