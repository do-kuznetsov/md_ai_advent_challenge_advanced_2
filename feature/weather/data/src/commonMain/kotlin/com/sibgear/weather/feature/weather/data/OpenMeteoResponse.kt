package com.sibgear.weather.feature.weather.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenMeteoResponse(
    @SerialName("current") val current: OpenMeteoCurrentDto,
)

@Serializable
internal data class OpenMeteoCurrentDto(
    @SerialName("temperature_2m") val temperatureCelsius: Double,
    @SerialName("cloud_cover") val cloudCoverPercent: Int,
    @SerialName("wind_speed_10m") val windSpeedKilometersPerHour: Double,
    @SerialName("precipitation") val precipitationMillimeters: Double,
)
