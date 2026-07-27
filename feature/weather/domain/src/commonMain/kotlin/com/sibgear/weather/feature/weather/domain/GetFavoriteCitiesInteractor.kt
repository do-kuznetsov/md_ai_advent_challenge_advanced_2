package com.sibgear.weather.feature.weather.domain

public class GetFavoriteCitiesInteractor(
    private val repository: FavoriteCityRepository,
) {

    public suspend operator fun invoke(): Result<List<FavoriteCityEntry>> =
        repository.favoriteCities()
}
