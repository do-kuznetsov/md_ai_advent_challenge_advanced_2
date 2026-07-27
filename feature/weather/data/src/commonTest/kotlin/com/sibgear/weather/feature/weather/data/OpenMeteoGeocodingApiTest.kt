package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

public class OpenMeteoGeocodingApiTest {

    @Test
    public fun requestsCitySearchParametersAndDecodesCandidates(): Unit = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/v1/search", request.url.encodedPath)
                assertEquals("Novosibirsk", request.url.parameters["name"])
                assertEquals("10", request.url.parameters["count"])
                assertEquals("ru", request.url.parameters["language"])
                assertEquals("json", request.url.parameters["format"])
                respond(
                    content = """
                        {
                            "results": [
                                {
                                    "id": 1496747,
                                    "name": "Новосибирск",
                                    "latitude": 55.0415,
                                    "longitude": 82.9346,
                                    "country": "Россия",
                                    "admin1": "Новосибирская область"
                                },
                                {
                                    "id": 1496990,
                                    "name": "Novosibirskaya",
                                    "latitude": 44.723,
                                    "longitude": 39.291,
                                    "country": "Россия"
                                }
                            ]
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = OpenMeteoGeocodingApi(client).searchCities("Novosibirsk")

        assertEquals(2, result.candidates.size)
        assertEquals("Новосибирск", result.candidates.first().name)
        assertEquals("Россия", result.candidates.first().country)
        assertEquals(55.0415, result.candidates.first().latitude)
        assertEquals(82.9346, result.candidates.first().longitude)
    }
}
