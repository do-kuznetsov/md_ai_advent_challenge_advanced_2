package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

public class CitySearchRepositoryImplTest {

    @Test
    public fun returnsMappedCityCandidates(): Unit = runTest {
        val repository = createRepository(
            engine = MockEngine {
                respond(
                    content = """
                        {
                            "results": [
                                {
                                    "name": "Москва",
                                    "latitude": 55.7558,
                                    "longitude": 37.6173,
                                    "country": "Россия"
                                }
                            ]
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        val candidates = repository.searchCities("Москва").getOrThrow()

        assertEquals(1, candidates.size)
        assertEquals("Москва", candidates.first().name)
        assertEquals("Россия", candidates.first().country)
        assertEquals(55.7558, candidates.first().latitude)
        assertEquals(37.6173, candidates.first().longitude)
    }

    @Test
    public fun returnsFailureWhenApiFails(): Unit = runTest {
        val repository = createRepository(
            engine = MockEngine {
                respondError(HttpStatusCode.InternalServerError)
            },
        )

        val result = repository.searchCities("Москва")

        assertTrue(result.isFailure)
    }

    private fun createRepository(engine: MockEngine): CitySearchRepositoryImpl {
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        return CitySearchRepositoryImpl(
            api = OpenMeteoGeocodingApi(client),
            mapper = OpenMeteoGeocodingDomainMapper(),
        )
    }
}
