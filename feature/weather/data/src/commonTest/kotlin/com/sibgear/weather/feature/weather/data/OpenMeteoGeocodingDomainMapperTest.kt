package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.sibgear.weather.feature.weather.domain.WeatherCityCandidate

public class OpenMeteoGeocodingDomainMapperTest {

    @Test
    public fun mapsFullResponseToCityCandidates(): Unit {
        val candidates = OpenMeteoGeocodingDomainMapper().map(
            OpenMeteoGeocodingResponse(
                candidates = listOf(
                    OpenMeteoGeocodingCandidateDto(
                        name = "Новосибирск",
                        country = "Россия",
                        latitude = 55.0415,
                        longitude = 82.9346,
                    ),
                    OpenMeteoGeocodingCandidateDto(
                        name = "Novosibirskaya",
                        country = "Россия",
                        latitude = 44.723,
                        longitude = 39.291,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                WeatherCityCandidate(
                    name = "Новосибирск",
                    country = "Россия",
                    latitude = 55.0415,
                    longitude = 82.9346,
                ),
                WeatherCityCandidate(
                    name = "Novosibirskaya",
                    country = "Россия",
                    latitude = 44.723,
                    longitude = 39.291,
                ),
            ),
            candidates,
        )
    }

    @Test
    public fun mapsEmptyResponseToEmptyCityCandidates(): Unit {
        val candidates = OpenMeteoGeocodingDomainMapper().map(OpenMeteoGeocodingResponse())

        assertEquals(emptyList(), candidates)
    }

    @Test
    public fun mapsMissingOptionalFieldsToCityCandidateWithNullCountry(): Unit {
        val response = json.decodeFromString<OpenMeteoGeocodingResponse>(
            """
                {
                    "results": [
                        {
                            "name": "Новосибирск",
                            "latitude": 55.0415,
                            "longitude": 82.9346
                        }
                    ]
                }
            """.trimIndent(),
        )

        val candidate = OpenMeteoGeocodingDomainMapper().map(response).single()

        assertEquals("Новосибирск", candidate.name)
        assertNull(candidate.country)
        assertEquals(55.0415, candidate.latitude)
        assertEquals(82.9346, candidate.longitude)
    }

    private companion object {

        private val json: Json = Json {
            ignoreUnknownKeys = true
        }
    }
}
