package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.AddFavoriteCityInteractor
import com.sibgear.weather.feature.weather.domain.CityHistoryRepository
import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.FavoriteCityRepository
import com.sibgear.weather.feature.weather.domain.GetCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import com.sibgear.weather.feature.weather.domain.GetFavoriteCitiesInteractor
import com.sibgear.weather.feature.weather.domain.RemoveFavoriteCityInteractor
import com.sibgear.weather.feature.weather.domain.SaveCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.SearchCitiesInteractor

public class WeatherScreenComponent(
    weatherRepository: CurrentWeatherRepository,
    citySearchRepository: CitySearchRepository,
    cityHistoryRepository: CityHistoryRepository,
    favoriteCityRepository: FavoriteCityRepository,
    currentTimeMillis: () -> Long,
) {

    public val viewModel: WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(weatherRepository),
            searchCities = SearchCitiesInteractor(citySearchRepository),
            getCityHistory = GetCityHistoryInteractor(cityHistoryRepository),
            saveCityHistory = SaveCityHistoryInteractor(cityHistoryRepository),
            getFavoriteCities = GetFavoriteCitiesInteractor(favoriteCityRepository),
            addFavoriteCity = AddFavoriteCityInteractor(favoriteCityRepository),
            removeFavoriteCity = RemoveFavoriteCityInteractor(favoriteCityRepository),
            currentTimeMillis = currentTimeMillis,
            mapper = WeatherUiMapper(),
        )
}
