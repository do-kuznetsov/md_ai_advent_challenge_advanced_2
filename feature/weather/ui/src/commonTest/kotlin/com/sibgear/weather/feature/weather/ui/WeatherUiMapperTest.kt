package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CurrentWeather
import kotlin.test.Test
import kotlin.test.assertEquals

public class WeatherUiMapperTest {

    @Test
    public fun mapsWeatherForRussianScreen(): Unit {
        val model = WeatherUiMapper().map(
            CurrentWeather(
                cityName = "Новосибирск",
                temperatureCelsius = 18.4,
                cloudCoverPercent = 62,
                windSpeedKilometersPerHour = 12.6,
                precipitationMillimeters = 0.4,
            ),
        )

        assertEquals("18 C", model.temperature)
        assertEquals("62 %", model.cloudCover)
        assertEquals("13 км/ч", model.windSpeed)
        assertEquals("0.4 мм", model.precipitation)
    }
}
