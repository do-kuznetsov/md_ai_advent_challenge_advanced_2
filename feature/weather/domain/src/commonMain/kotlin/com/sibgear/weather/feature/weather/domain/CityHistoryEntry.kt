package com.sibgear.weather.feature.weather.domain

public data class CityHistoryEntry(
    public val name: String,
    public val country: String? = null,
    public val latitude: Double,
    public val longitude: Double,
    public val selectedAtEpochMillis: Long,
)
