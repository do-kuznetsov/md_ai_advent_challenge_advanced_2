package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CityHistoryRepository
import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.GetCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import com.sibgear.weather.feature.weather.domain.SaveCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.SearchCitiesInteractor

public class WeatherScreenComponent(
    weatherRepository: CurrentWeatherRepository,
    citySearchRepository: CitySearchRepository,
    cityHistoryRepository: CityHistoryRepository,
    currentTimeMillis: () -> Long,
) {

    public val viewModel: WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(weatherRepository),
            searchCities = SearchCitiesInteractor(citySearchRepository),
            getCityHistory = GetCityHistoryInteractor(cityHistoryRepository),
            saveCityHistory = SaveCityHistoryInteractor(cityHistoryRepository),
            currentTimeMillis = currentTimeMillis,
            mapper = WeatherUiMapper(),
        )
}
