package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertIs
import com.sibgear.weather.core.location.Coordinates
import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.CityName
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository

public class WeatherDataModuleTest {

    @Test
    public fun providesCurrentWeatherRepositoryImplementation(): Unit {
        val repository = WeatherDataModule.provide(
            currentLocationProvider = object : CurrentLocationProvider {
                override suspend fun currentLocation(): Result<Coordinates> =
                    Result.success(Coordinates(55.03, 82.92))
            },
            reverseGeocodingRepository = object : ReverseGeocodingRepository {
                override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> =
                    Result.success(CityName("Новосибирск"))
            },
        )

        assertIs<CurrentWeatherRepositoryImpl>(repository)
    }
}
