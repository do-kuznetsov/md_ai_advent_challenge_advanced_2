package com.sibgear.weather.feature.weather.domain

public class RemoveFavoriteCityInteractor(
    private val repository: FavoriteCityRepository,
) {

    public suspend operator fun invoke(entry: FavoriteCityEntry): Result<Unit> =
        repository.removeCity(entry)
}
