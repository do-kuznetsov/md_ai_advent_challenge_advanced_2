package com.sibgear.weather.feature.weather.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal class OpenMeteoApi(
    private val client: HttpClient,
) {

    public suspend fun getCurrentForecast(latitude: Double, longitude: Double): OpenMeteoResponse =
        client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter(
                "current",
                "temperature_2m,cloud_cover,wind_speed_10m,precipitation",
            )
            parameter("temperature_unit", "celsius")
            parameter("wind_speed_unit", "kmh")
            parameter("precipitation_unit", "mm")
            parameter("timezone", "auto")
        }.body()
}
