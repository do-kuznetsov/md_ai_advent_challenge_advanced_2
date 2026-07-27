package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.WeatherCityCandidate

internal class CitySearchRepositoryImpl(
    private val api: OpenMeteoGeocodingApi,
    private val mapper: OpenMeteoGeocodingDomainMapper,
) : CitySearchRepository {

    override suspend fun searchCities(query: String): Result<List<WeatherCityCandidate>> =
        runCatching {
            mapper.map(api.searchCities(query))
        }
}
