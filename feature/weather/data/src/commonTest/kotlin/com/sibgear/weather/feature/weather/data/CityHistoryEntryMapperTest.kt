package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertEquals

public class CityHistoryEntryMapperTest {

    @Test
    public fun mapsStorageRowValuesToDomainEntry(): Unit {
        val entry = CityHistoryEntryMapper().map(
            name = "Москва",
            country = "Россия",
            latitude = 55.7558,
            longitude = 37.6173,
            selectedAtEpochMillis = 1_723_000_000_000,
        )

        assertEquals("Москва", entry.name)
        assertEquals("Россия", entry.country)
        assertEquals(55.7558, entry.latitude)
        assertEquals(37.6173, entry.longitude)
        assertEquals(1_723_000_000_000, entry.selectedAtEpochMillis)
    }
}
