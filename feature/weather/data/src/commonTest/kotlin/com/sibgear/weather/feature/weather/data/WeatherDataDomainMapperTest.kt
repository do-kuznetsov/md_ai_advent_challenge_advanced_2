package com.sibgear.weather.feature.weather.data

import kotlin.test.Test
import kotlin.test.assertEquals

public class WeatherDataDomainMapperTest {

    @Test
    public fun mapsOpenMeteoCurrentFields() {
        val result =
            WeatherDataDomainMapper().map(
                source =
                    ForecastDto(
                        current =
                            CurrentWeatherDto(
                                temperatureCelsius = 21.5,
                                cloudCoverPercent = 40,
                                windSpeedKilometersPerHour = 12.4,
                                precipitationMillimeters = 0.6,
                            ),
                    ),
                cityName = "Novosibirsk",
            )

        assertEquals("Novosibirsk", result.cityName)
        assertEquals(21.5, result.temperatureCelsius)
        assertEquals(40, result.cloudCoverPercent)
        assertEquals(12.4, result.windSpeedKilometersPerHour)
        assertEquals(0.6, result.precipitationMillimeters)
    }
}
