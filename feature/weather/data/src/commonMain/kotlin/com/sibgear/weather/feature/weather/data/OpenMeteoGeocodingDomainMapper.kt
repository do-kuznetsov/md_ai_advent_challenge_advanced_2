package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.domain.WeatherCityCandidate

internal class OpenMeteoGeocodingDomainMapper {

    public fun map(source: OpenMeteoGeocodingResponse): List<WeatherCityCandidate> =
        source.candidates.map(::mapCandidate)

    private fun mapCandidate(source: OpenMeteoGeocodingCandidateDto): WeatherCityCandidate =
        WeatherCityCandidate(
            name = source.name,
            country = source.country,
            latitude = source.latitude,
            longitude = source.longitude,
        )
}
