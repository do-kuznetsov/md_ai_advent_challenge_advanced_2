package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import com.sibgear.weather.feature.weather.domain.SearchCitiesInteractor

public class WeatherScreenComponent(
    weatherRepository: CurrentWeatherRepository,
    citySearchRepository: CitySearchRepository,
) {

    public val viewModel: WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(weatherRepository),
            searchCities = SearchCitiesInteractor(citySearchRepository),
            mapper = WeatherUiMapper(),
        )
}
