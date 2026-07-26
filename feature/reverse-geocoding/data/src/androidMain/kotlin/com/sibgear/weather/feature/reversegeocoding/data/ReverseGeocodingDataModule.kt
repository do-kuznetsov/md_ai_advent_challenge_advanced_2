package com.sibgear.weather.feature.reversegeocoding.data

import android.content.Context
import android.location.Geocoder
import com.sibgear.weather.feature.reversegeocoding.domain.CityName
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

public object ReverseGeocodingDataModule {

    public fun provide(context: Context): ReverseGeocodingRepository = AndroidReverseGeocodingRepository(context)
}

internal class AndroidReverseGeocodingRepository(
    private val context: Context,
) : ReverseGeocodingRepository {

    @Suppress("DEPRECATION")
    override suspend fun resolveCityName(
        latitude: Double,
        longitude: Double,
    ): CityName? =
        withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, MAX_RESULTS)
                    ?.firstOrNull()
                    ?.locality
                    ?.takeIf(String::isNotBlank)
                    ?.let(::CityName)
            }.getOrNull()
        }

    private companion object {

        const val MAX_RESULTS: Int = 1
    }
}
