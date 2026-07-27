package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.domain.FavoriteCityEntry

internal class FavoriteCityEntryMapper {

    fun map(
        name: String,
        country: String?,
        latitude: Double,
        longitude: Double,
    ): FavoriteCityEntry =
        FavoriteCityEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
        )
}
