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
}
