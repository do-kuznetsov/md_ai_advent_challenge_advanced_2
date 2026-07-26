package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CurrentWeather
import kotlin.test.Test
import kotlin.test.assertEquals

public class WeatherUiMapperTest {
    @Test
    public fun formatsAllWeatherMetrics() {
        val result =
            WeatherUiMapper().map(
                CurrentWeather(
                    cityName = "Novosibirsk",
                    temperatureCelsius = 20.4,
                    cloudCoverPercent = 60,
                    windSpeedKilometersPerHour = 4.5,
                    precipitationMillimeters = 0.8,
                ),
            )

        assertEquals("Novosibirsk", result.cityName)
        assertEquals("20 C", result.temperature)
        assertEquals("60 %", result.cloudCover)
        assertEquals("5 км/ч", result.windSpeed)
        assertEquals("0.8 мм", result.precipitation)
    }
}
