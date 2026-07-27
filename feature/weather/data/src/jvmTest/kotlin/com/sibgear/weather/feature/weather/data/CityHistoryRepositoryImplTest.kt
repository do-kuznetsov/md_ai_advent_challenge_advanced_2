package com.sibgear.weather.feature.weather.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.CityHistoryEntry

public class CityHistoryRepositoryImplTest {

    @Test
    public fun savingSameCityTwiceKeepsSingleRecentEntry(): Unit = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WeatherStorageDatabase.Schema.create(driver)
        val repository = WeatherDataModule.provideCityHistoryRepository(driver)

        repository.saveCity(createEntry(selectedAtEpochMillis = 1_723_000_000_000)).getOrThrow()
        repository.saveCity(createEntry(selectedAtEpochMillis = 1_723_000_100_000)).getOrThrow()

        val entries = repository.recentCities(limit = 10).getOrThrow()

        assertEquals(
            listOf(createEntry(selectedAtEpochMillis = 1_723_000_100_000)),
            entries,
        )
    }

    private fun createEntry(selectedAtEpochMillis: Long): CityHistoryEntry =
        CityHistoryEntry(
            name = "Москва",
            country = "Россия",
            latitude = 55.7558,
            longitude = 37.6173,
            selectedAtEpochMillis = selectedAtEpochMillis,
        )
}
