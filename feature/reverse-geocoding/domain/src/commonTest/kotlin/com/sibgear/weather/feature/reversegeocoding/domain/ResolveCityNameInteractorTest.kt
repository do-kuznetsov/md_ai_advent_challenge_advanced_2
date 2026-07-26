package com.sibgear.weather.feature.reversegeocoding.domain

import kotlin.test.Test
import kotlin.test.assertEquals

public class ResolveCityNameInteractorTest {
    @Test
    public fun returnsRepositoryValue() =
        kotlinx.coroutines.test.runTest {
            val expected = CityName("Novosibirsk")
            val interactor =
                ResolveCityNameInteractor(
                    repository =
                        object : ReverseGeocodingRepository {
                            override suspend fun resolveCityName(
                                latitude: Double,
                                longitude: Double,
                            ): CityName? = expected
                        },
                )

            assertEquals(expected, interactor(55.03, 82.92))
        }
}
