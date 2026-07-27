package com.sibgear.weather.feature.weather.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.FavoriteCityEntry

public class FavoriteCityRepositoryImplTest {

    @Test
    public fun addingSameCityTwiceKeepsSingleFavorite(): Unit = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WeatherStorageDatabase.Schema.create(driver)
        val repository = WeatherDataModule.provideFavoriteCityRepository(driver)

        repository.addCity(createEntry(name = "Москва")).getOrThrow()
        repository.addCity(createEntry(name = "Moscow")).getOrThrow()

        assertEquals(listOf(createEntry(name = "Moscow")), repository.favoriteCities().getOrThrow())
    }

    @Test
    public fun favoriteCityPersistsAndCanBeRemoved(): Unit = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WeatherStorageDatabase.Schema.create(driver)
        val repository = WeatherDataModule.provideFavoriteCityRepository(driver)

        repository.addCity(createEntry()).getOrThrow()

        val restoredRepository = WeatherDataModule.provideFavoriteCityRepository(driver)

        assertEquals(listOf(createEntry()), restoredRepository.favoriteCities().getOrThrow())

        restoredRepository.removeCity(createEntry()).getOrThrow()

        assertEquals(emptyList(), repository.favoriteCities().getOrThrow())
    }

    private fun createEntry(name: String = "Москва"): FavoriteCityEntry =
        FavoriteCityEntry(
            name = name,
            country = "Россия",
            latitude = 55.7558,
            longitude = 37.6173,
        )
}
