package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.CityHistoryEntry
import com.sibgear.weather.feature.weather.domain.CityHistoryRepository

internal class CityHistoryRepositoryImpl(
    private val database: WeatherStorageDatabase,
    private val mapper: CityHistoryEntryMapper,
) : CityHistoryRepository {

    override suspend fun saveCity(entry: CityHistoryEntry): Result<Unit> =
        runCatching {
            database.cityHistoryQueries.upsert(
                name = entry.name,
                country = entry.country,
                latitude = entry.latitude,
                longitude = entry.longitude,
                selected_at_epoch_millis = entry.selectedAtEpochMillis,
            )
        }

    override suspend fun recentCities(limit: Int): Result<List<CityHistoryEntry>> =
        runCatching {
            database.cityHistoryQueries.selectRecent(limit.coerceAtLeast(0).toLong())
                .executeAsList()
                .map { row ->
                    mapper.map(
                        name = row.name,
                        country = row.country,
                        latitude = row.latitude,
                        longitude = row.longitude,
                        selectedAtEpochMillis = row.selected_at_epoch_millis,
                    )
                }
        }
}
