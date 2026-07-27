package com.sibgear.weather.feature.weather.domain

public interface CitySearchRepository {

    public suspend fun searchCities(query: String): Result<List<WeatherCityCandidate>>
}
