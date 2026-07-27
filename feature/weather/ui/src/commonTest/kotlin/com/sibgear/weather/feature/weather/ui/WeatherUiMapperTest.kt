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
        assertEquals(WeatherIcon.Cloud, model.conditionIcon)
        assertEquals("62 %", model.cloudCover)
        assertEquals(WeatherIcon.Cloud, model.cloudCoverIcon)
        assertEquals("13 км/ч", model.windSpeed)
        assertEquals(WeatherIcon.Wind, model.windSpeedIcon)
        assertEquals("0.4 мм", model.precipitation)
        assertEquals(WeatherIcon.Precipitation, model.precipitationIcon)
    }

    @Test
    public fun mapsSunnyConditionIconForLowCloudCover(): Unit {
        val model = WeatherUiMapper().map(
            CurrentWeather(
                cityName = "Новосибирск",
                temperatureCelsius = 21.0,
                cloudCoverPercent = 12,
                windSpeedKilometersPerHour = 3.0,
                precipitationMillimeters = 0.0,
            ),
        )

        assertEquals(WeatherIcon.Sunny, model.conditionIcon)
        assertEquals(WeatherIcon.Sunny, model.cloudCoverIcon)
    }
}
