package com.sibgear.weather.feature.weather.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class OpenMeteoApi(
    private val client: HttpClient,
) {

    internal suspend fun getCurrentForecast(
        latitude: Double,
        longitude: Double,
    ): ForecastDto =
        client
            .get(FORECAST_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current", CURRENT_FIELDS)
                parameter("temperature_unit", "celsius")
                parameter("wind_speed_unit", "kmh")
                parameter("precipitation_unit", "mm")
                parameter("timezone", "auto")
            }.body()

    private companion object {

        const val FORECAST_URL: String = "https://api.open-meteo.com/v1/forecast"
        const val CURRENT_FIELDS: String = "temperature_2m,cloud_cover,wind_speed_10m,precipitation"
    }
}

@Serializable
internal data class ForecastDto(
    @SerialName("current") val current: CurrentWeatherDto,
)

@Serializable
internal data class CurrentWeatherDto(
    @SerialName("temperature_2m") val temperatureCelsius: Double,
    @SerialName("cloud_cover") val cloudCoverPercent: Int,
    @SerialName("wind_speed_10m") val windSpeedKilometersPerHour: Double,
    @SerialName("precipitation") val precipitationMillimeters: Double,
)
