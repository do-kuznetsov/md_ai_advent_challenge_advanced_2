package com.sibgear.weather.feature.weather.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

public class OpenMeteoApiTest {

    @Test
    public fun requestsCurrentWeatherFieldsAndUnits(): Unit = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/v1/forecast", request.url.encodedPath)
                assertEquals("55.03", request.url.parameters["latitude"])
                assertEquals("82.92", request.url.parameters["longitude"])
                assertEquals(
                    "temperature_2m,cloud_cover,wind_speed_10m,precipitation",
                    request.url.parameters["current"],
                )
                assertEquals("celsius", request.url.parameters["temperature_unit"])
                assertEquals("kmh", request.url.parameters["wind_speed_unit"])
                assertEquals("mm", request.url.parameters["precipitation_unit"])
                assertEquals("auto", request.url.parameters["timezone"])
                respond(
                    content = """
                        {"current":{"temperature_2m":18.4,"cloud_cover":62,"wind_speed_10m":12.6,"precipitation":0.4}}
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = OpenMeteoApi(client).getCurrentForecast(55.03, 82.92)

        assertEquals(18.4, result.current.temperatureCelsius)
    }
}
