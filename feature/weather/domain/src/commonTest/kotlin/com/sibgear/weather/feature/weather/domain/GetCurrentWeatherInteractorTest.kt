package com.sibgear.weather.feature.weather.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

public class GetCurrentWeatherInteractorTest {

    @Test
    public fun returnsRepositoryWeather(): Unit = runTest {
        val weather = CurrentWeather(
            cityName = "Новосибирск",
            temperatureCelsius = 18.4,
            cloudCoverPercent = 62,
            windSpeedKilometersPerHour = 12.6,
            precipitationMillimeters = 0.4,
        )
        val interactor = GetCurrentWeatherInteractor(
            repository = object : CurrentWeatherRepository {
                override suspend fun loadCurrentWeather(): Result<CurrentWeather> =
                    Result.success(weather)
            },
        )

        assertEquals(weather, interactor().getOrThrow())
    }

    @Test
    public fun returnsRepositoryFailure(): Unit = runTest {
        val failure = CurrentWeatherLocationUnavailableException()
        val interactor = GetCurrentWeatherInteractor(
            repository = object : CurrentWeatherRepository {
                override suspend fun loadCurrentWeather(): Result<CurrentWeather> =
                    Result.failure(failure)
            },
        )

        assertSame(failure, interactor().exceptionOrNull())
    }

    @Test
    public fun returnsRepositoryWeatherForSelectedLocation(): Unit = runTest {
        val location = SelectedWeatherLocation.City(
            name = "Москва",
            latitude = 55.75,
            longitude = 37.62,
        )
        val weather = CurrentWeather(
            cityName = "Москва",
            temperatureCelsius = 21.0,
            cloudCoverPercent = 10,
            windSpeedKilometersPerHour = 5.0,
            precipitationMillimeters = 0.0,
        )
        val interactor = GetCurrentWeatherInteractor(
            repository = object : CurrentWeatherRepository {
                override suspend fun loadCurrentWeather(): Result<CurrentWeather> =
                    Result.failure(IllegalStateException("current location is not expected"))

                override suspend fun loadWeather(location: SelectedWeatherLocation): Result<CurrentWeather> =
                    Result.success(weather)
            },
        )

        assertEquals(weather, interactor(location).getOrThrow())
    }
}
