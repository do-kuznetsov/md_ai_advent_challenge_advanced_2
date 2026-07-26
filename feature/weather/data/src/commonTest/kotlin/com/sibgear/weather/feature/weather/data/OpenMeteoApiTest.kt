package com.sibgear.weather.feature.weather.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

public class OpenMeteoApiTest {

    @Test
    public fun requestsCurrentWeatherFieldsAndMapsResponse() =
        runTest {
            var requestedCurrentFields: String? = null
            val client =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            requestedCurrentFields = request.url.parameters["current"]
                            respond(
                                content =
                                    """
                                    {"current":{"temperature_2m":18.5,"cloud_cover":70,"wind_speed_10m":9.0,"precipitation":0.2}}
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", "application/json"),
                            )
                        }
                    }
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val result = OpenMeteoApi(client).getCurrentForecast(latitude = 55.03, longitude = 82.92)

            assertEquals("temperature_2m,cloud_cover,wind_speed_10m,precipitation", requestedCurrentFields)
            assertEquals(18.5, result.current.temperatureCelsius)
            assertEquals(70, result.current.cloudCoverPercent)
            assertEquals(9.0, result.current.windSpeedKilometersPerHour)
            assertEquals(0.2, result.current.precipitationMillimeters)
        }
}
