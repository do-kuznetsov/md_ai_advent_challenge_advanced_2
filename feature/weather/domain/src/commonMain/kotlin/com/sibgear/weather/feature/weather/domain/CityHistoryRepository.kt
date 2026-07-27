package com.sibgear.weather.feature.weather.domain

public interface CityHistoryRepository {

    public suspend fun saveCity(entry: CityHistoryEntry): Result<Unit>

    public suspend fun recentCities(limit: Int): Result<List<CityHistoryEntry>>
}
