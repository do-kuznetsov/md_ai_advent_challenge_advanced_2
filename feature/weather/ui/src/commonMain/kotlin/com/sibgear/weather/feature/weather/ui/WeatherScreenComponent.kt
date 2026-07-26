package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor

public class WeatherScreenComponent(
    repository: CurrentWeatherRepository,
) {
    public val viewModel: WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(repository),
            mapper = WeatherUiMapper(),
        )
}
