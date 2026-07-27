package com.sibgear.weather.feature.weather.domain

public class AddFavoriteCityInteractor(
    private val repository: FavoriteCityRepository,
) {

    public suspend operator fun invoke(entry: FavoriteCityEntry): Result<Unit> =
        repository.addCity(entry)
}
