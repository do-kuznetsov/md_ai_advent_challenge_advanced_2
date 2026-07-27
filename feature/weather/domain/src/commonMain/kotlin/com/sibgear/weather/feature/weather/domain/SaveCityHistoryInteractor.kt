package com.sibgear.weather.feature.weather.domain

public class SaveCityHistoryInteractor(
    private val repository: CityHistoryRepository,
) {

    public suspend operator fun invoke(entry: CityHistoryEntry): Result<Unit> =
        repository.saveCity(entry)
}
