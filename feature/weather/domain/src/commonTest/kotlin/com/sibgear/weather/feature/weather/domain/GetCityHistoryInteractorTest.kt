package com.sibgear.weather.feature.weather.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

public class GetCityHistoryInteractorTest {

    @Test
    public fun returnsRepositoryCityHistory(): Unit = runTest {
        val entries = listOf(
            CityHistoryEntry(
                name = "Москва",
                country = "Россия",
                latitude = 55.7558,
                longitude = 37.6173,
                selectedAtEpochMillis = 1_723_000_000_000,
            ),
        )
        val interactor = GetCityHistoryInteractor(
            repository = object : CityHistoryRepository {
                override suspend fun saveCity(entry: CityHistoryEntry): Result<Unit> =
                    Result.success(Unit)

                override suspend fun recentCities(limit: Int): Result<List<CityHistoryEntry>> =
                    Result.success(entries)
            },
        )

        assertEquals(entries, interactor(limit = 5).getOrThrow())
    }

    @Test
    public fun returnsRepositoryFailure(): Unit = runTest {
        val failure = IllegalStateException("history unavailable")
        val interactor = GetCityHistoryInteractor(
            repository = object : CityHistoryRepository {
                override suspend fun saveCity(entry: CityHistoryEntry): Result<Unit> =
                    Result.success(Unit)

                override suspend fun recentCities(limit: Int): Result<List<CityHistoryEntry>> =
                    Result.failure(failure)
            },
        )

        assertSame(failure, interactor(limit = 5).exceptionOrNull())
    }
}
