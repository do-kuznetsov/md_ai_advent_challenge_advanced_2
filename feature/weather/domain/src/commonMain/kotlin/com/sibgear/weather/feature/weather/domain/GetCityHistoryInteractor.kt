package com.sibgear.weather.feature.weather.domain

public class GetCityHistoryInteractor(
    private val repository: CityHistoryRepository,
) {

    public suspend operator fun invoke(limit: Int): Result<List<CityHistoryEntry>> =
        repository.recentCities(limit)
}
