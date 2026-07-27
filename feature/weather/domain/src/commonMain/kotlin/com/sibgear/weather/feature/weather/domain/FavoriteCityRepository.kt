package com.sibgear.weather.feature.weather.domain

public interface FavoriteCityRepository {

    public suspend fun addCity(entry: FavoriteCityEntry): Result<Unit>

    public suspend fun removeCity(entry: FavoriteCityEntry): Result<Unit>

    public suspend fun favoriteCities(): Result<List<FavoriteCityEntry>>
}
