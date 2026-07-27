package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.domain.CityHistoryEntry

internal class CityHistoryEntryMapper {

    internal fun map(
        name: String,
        country: String?,
        latitude: Double,
        longitude: Double,
        selectedAtEpochMillis: Long,
    ): CityHistoryEntry =
        CityHistoryEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            selectedAtEpochMillis = selectedAtEpochMillis,
        )
}
