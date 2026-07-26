package com.sibgear.weather.feature.weather.domain

public data class CurrentWeather(
    public val cityName: String,
    public val temperatureCelsius: Double,
    public val cloudCoverPercent: Int,
    public val windSpeedKilometersPerHour: Double,
    public val precipitationMillimeters: Double,
)
