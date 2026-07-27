package com.sibgear.weather.feature.weather.domain

public class SearchCitiesInteractor(
    private val repository: CitySearchRepository,
) {

    public suspend operator fun invoke(query: String): Result<List<WeatherCityCandidate>> =
        repository.searchCities(query)
}
