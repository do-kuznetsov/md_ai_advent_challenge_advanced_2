package com.sibgear.weather.feature.reversegeocoding.domain

import kotlin.test.Test
import kotlin.test.assertEquals

public class ResolveCityNameInteractorTest {

    @Test
    public fun resolvesCityNameFromRepository(): Unit = kotlinx.coroutines.test.runTest {
        val interactor = ResolveCityNameInteractor(
            repository = object : ReverseGeocodingRepository {
                override suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?> =
                    Result.success(CityName("Новосибирск"))
            },
        )

        assertEquals(CityName("Новосибирск"), interactor(55.03, 82.92).getOrThrow())
    }
}
