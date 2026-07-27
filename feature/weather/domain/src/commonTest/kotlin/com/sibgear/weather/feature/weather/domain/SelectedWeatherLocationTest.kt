package com.sibgear.weather.feature.weather.domain

import kotlin.test.Test
import kotlin.test.assertEquals

public class SelectedWeatherLocationTest {

    @Test
    public fun cityKeepsNameAndCoordinates(): Unit {
        val location: SelectedWeatherLocation = SelectedWeatherLocation.City(
            name = "Новосибирск",
            latitude = 55.0302,
            longitude = 82.9204,
        )

        assertEquals(55.0302, location.latitude)
        assertEquals(82.9204, location.longitude)
        assertEquals(
            SelectedWeatherLocation.City(
                name = "Новосибирск",
                latitude = 55.0302,
                longitude = 82.9204,
            ),
            location,
        )
    }

    @Test
    public fun coordinatesKeepSelectedPointWithoutCityName(): Unit {
        val location: SelectedWeatherLocation = SelectedWeatherLocation.Coordinates(
            latitude = 54.9833,
            longitude = 82.8964,
        )

        assertEquals(54.9833, location.latitude)
        assertEquals(82.8964, location.longitude)
        assertEquals(
            SelectedWeatherLocation.Coordinates(
                latitude = 54.9833,
                longitude = 82.8964,
            ),
            location,
        )
    }
}
