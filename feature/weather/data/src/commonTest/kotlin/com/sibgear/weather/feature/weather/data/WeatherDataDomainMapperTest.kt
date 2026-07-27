package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
import com.sibgear.weather.core.location.Coordinates
import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.CityName
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeatherLocationUnavailableException

public class WeatherDataDomainMapperTest {

    @Test
    public fun mapsCurrentWeatherData(): Unit {
        val weather = WeatherDataDomainMapper().map(
            source = OpenMeteoResponse(
                current = OpenMeteoCurrentDto(
                    temperatureCelsius = 18.4,
                    cloudCoverPercent = 62,
                    windSpeedKilometersPerHour = 12.6,
                    precipitationMillimeters = 0.4,
                ),
            ),
            cityName = "Новосибирск",
        )

        assertEquals("Новосибирск", weather.cityName)
        assertEquals(18.4, weather.temperatureCelsius)
        assertEquals(62, weather.cloudCoverPercent)
        assertEquals(12.6, weather.windSpeedKilometersPerHour)
        assertEquals(0.4, weather.precipitationMillimeters)
    }

    @Test
    public fun usesCurrentLocationLabelWhenReverseGeocodingFails(): Unit = runTest {
        val api = OpenMeteoApi(
            HttpClient(
                MockEngine {
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
            },
        )
        val repository = CurrentWeatherRepositoryImpl(
            currentLocationProvider = object : CurrentLocationProvider {
                override suspend fun currentLocation(): Result<Coordinates> = Result.success(Coordinates(55.03, 82.92))
            },
            resolveCityName = ResolveCityNameInteractor(
                object : ReverseGeocodingRepository {
                    override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> =
                        Result.failure(IllegalStateException())
                },
            ),
            api = api,
            mapper = WeatherDataDomainMapper(),
        )

        assertEquals("Текущее местоположение", repository.loadCurrentWeather().getOrThrow().cityName)
    }

    @Test
    public fun returnsLocationUnavailableWhenLocationProviderFails(): Unit = runTest {
        val repository = CurrentWeatherRepositoryImpl(
            currentLocationProvider = object : CurrentLocationProvider {
                override suspend fun currentLocation(): Result<Coordinates> =
                    Result.failure(IllegalStateException("no permission"))
            },
            resolveCityName = ResolveCityNameInteractor(
                object : ReverseGeocodingRepository {
                    override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> =
                        Result.success(CityName("Новосибирск"))
                },
            ),
            api = createSuccessfulApi(),
            mapper = WeatherDataDomainMapper(),
        )

        assertIs<CurrentWeatherLocationUnavailableException>(repository.loadCurrentWeather().exceptionOrNull())
    }

    @Test
    public fun returnsApiFailureWhenForecastRequestFails(): Unit = runTest {
        val failure = IllegalStateException("api failed")
        val repository = CurrentWeatherRepositoryImpl(
            currentLocationProvider = object : CurrentLocationProvider {
                override suspend fun currentLocation(): Result<Coordinates> = Result.success(Coordinates(55.03, 82.92))
            },
            resolveCityName = ResolveCityNameInteractor(
                object : ReverseGeocodingRepository {
                    override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> =
                        Result.success(CityName("Новосибирск"))
                },
            ),
            api = OpenMeteoApi(
                HttpClient(
                    MockEngine {
                        throw failure
                    },
                ) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                },
            ),
            mapper = WeatherDataDomainMapper(),
        )

        assertEquals(failure.message, repository.loadCurrentWeather().exceptionOrNull()?.message)
    }

    private fun createSuccessfulApi(): OpenMeteoApi =
        OpenMeteoApi(
            HttpClient(
                MockEngine {
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
            },
        )
}
