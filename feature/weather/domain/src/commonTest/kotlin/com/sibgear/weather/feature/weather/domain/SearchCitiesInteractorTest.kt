package com.sibgear.weather.feature.weather.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

public class SearchCitiesInteractorTest {

    @Test
    public fun returnsRepositoryCityCandidates(): Unit = runTest {
        val candidates = listOf(
            WeatherCityCandidate(
                name = "Москва",
                country = "Россия",
                latitude = 55.7558,
                longitude = 37.6173,
            ),
        )
        val interactor = SearchCitiesInteractor(
            repository = object : CitySearchRepository {
                override suspend fun searchCities(query: String): Result<List<WeatherCityCandidate>> =
                    Result.success(candidates)
            },
        )

        assertEquals(candidates, interactor("Москва").getOrThrow())
    }

    @Test
    public fun returnsRepositoryFailure(): Unit = runTest {
        val failure = IllegalStateException("geocoding failed")
        val interactor = SearchCitiesInteractor(
            repository = object : CitySearchRepository {
                override suspend fun searchCities(query: String): Result<List<WeatherCityCandidate>> =
                    Result.failure(failure)
            },
        )

        assertSame(failure, interactor("Москва").exceptionOrNull())
    }
}
