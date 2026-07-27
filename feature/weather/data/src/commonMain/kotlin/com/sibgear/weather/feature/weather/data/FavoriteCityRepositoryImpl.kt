package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.FavoriteCityEntry
import com.sibgear.weather.feature.weather.domain.FavoriteCityRepository

internal class FavoriteCityRepositoryImpl(
    private val database: WeatherStorageDatabase,
    private val mapper: FavoriteCityEntryMapper,
) : FavoriteCityRepository {

    override suspend fun addCity(entry: FavoriteCityEntry): Result<Unit> =
        runCatching {
            database.favoriteCityQueries.upsertFavorite(
                name = entry.name,
                country = entry.country,
                latitude = entry.latitude,
                longitude = entry.longitude,
            )
        }

    override suspend fun removeCity(entry: FavoriteCityEntry): Result<Unit> =
        runCatching {
            database.favoriteCityQueries.deleteFavorite(
                latitude = entry.latitude,
                longitude = entry.longitude,
            )
        }

    override suspend fun favoriteCities(): Result<List<FavoriteCityEntry>> =
        runCatching {
            database.favoriteCityQueries.selectFavorites()
                .executeAsList()
                .map { row ->
                    mapper.map(
                        name = row.name,
                        country = row.country,
                        latitude = row.latitude,
                        longitude = row.longitude,
                    )
                }
        }
}
