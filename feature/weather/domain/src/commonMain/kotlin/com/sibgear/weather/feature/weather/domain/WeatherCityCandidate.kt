package com.sibgear.weather.feature.weather.domain

public data class WeatherCityCandidate(
    public val name: String,
    public val country: String? = null,
    public val latitude: Double,
    public val longitude: Double,
)
