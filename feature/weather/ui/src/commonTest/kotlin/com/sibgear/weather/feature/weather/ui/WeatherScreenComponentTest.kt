package com.sibgear.weather.feature.weather.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository

public class WeatherScreenComponentTest {

    @Test
    public fun createsWeatherViewModel(): Unit {
        val component = WeatherScreenComponent(
            repository = object : CurrentWeatherRepository {
                override suspend fun loadCurrentWeather(): Result<CurrentWeather> =
                    Result.success(
                        CurrentWeather(
                            cityName = "Новосибирск",
                            temperatureCelsius = 18.4,
                            cloudCoverPercent = 62,
                            windSpeedKilometersPerHour = 12.6,
                            precipitationMillimeters = 0.4,
                        ),
                    )
            },
        )

        assertIs<WeatherViewModel>(component.viewModel)
        assertEquals(WeatherState.LoadingLocation(), component.viewModel.state.value)
    }
}
