package com.sibgear.weather.feature.weather.domain

import kotlin.test.Test
import kotlin.test.assertEquals

public class GetCurrentWeatherInteractorTest {

    @Test
    public fun returnsRepositoryValue() =
        kotlinx.coroutines.test.runTest {
            val expected = CurrentWeather("Novosibirsk", 20.0, 45, 5.0, 0.0)
            val interactor =
                GetCurrentWeatherInteractor(
                    repository =
                        object : CurrentWeatherRepository {

                            override suspend fun loadCurrentWeather(): Result<CurrentWeather> = Result.success(expected)
                        },
                )

            assertEquals(expected, interactor().getOrThrow())
        }
}
