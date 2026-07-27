package com.sibgear.weather.feature.reversegeocoding.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

public class ResolveCityNameInteractorTest {

    @Test
    public fun resolvesCityNameFromRepository(): Unit = runTest {
        var requestedLatitude: Double? = null
        var requestedLongitude: Double? = null
        val interactor = ResolveCityNameInteractor(
            repository = object : ReverseGeocodingRepository {
                override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> {
                    requestedLatitude = latitude
                    requestedLongitude = longitude
                    return Result.success(CityName("Новосибирск"))
                }
            },
        )

        assertEquals(CityName("Новосибирск"), interactor(55.03, 82.92).getOrThrow())
        assertEquals(55.03, requestedLatitude)
        assertEquals(82.92, requestedLongitude)
    }

    @Test
    public fun returnsRepositoryFailure(): Unit = runTest {
        val failure = IllegalStateException("geocoder failed")
        val interactor = ResolveCityNameInteractor(
            repository = object : ReverseGeocodingRepository {
                override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> =
                    Result.failure(failure)
            },
        )

        assertSame(failure, interactor(55.03, 82.92).exceptionOrNull())
    }
}
