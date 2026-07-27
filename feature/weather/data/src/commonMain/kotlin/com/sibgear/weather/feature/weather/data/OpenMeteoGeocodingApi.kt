package com.sibgear.weather.feature.weather.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal class OpenMeteoGeocodingApi(
    private val client: HttpClient,
) {

    public suspend fun searchCities(query: String): OpenMeteoGeocodingResponse =
        client.get("https://geocoding-api.open-meteo.com/v1/search") {
            parameter("name", query)
            parameter("count", DEFAULT_CANDIDATE_COUNT)
            parameter("language", DEFAULT_LANGUAGE)
            parameter("format", RESPONSE_FORMAT)
        }.body()

    private companion object {

        private const val DEFAULT_CANDIDATE_COUNT: Int = 10
        private const val DEFAULT_LANGUAGE: String = "ru"
        private const val RESPONSE_FORMAT: String = "json"
    }
}
