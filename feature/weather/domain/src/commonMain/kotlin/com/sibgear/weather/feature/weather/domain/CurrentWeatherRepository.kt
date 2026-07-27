package com.sibgear.weather.feature.weather.domain

public interface CurrentWeatherRepository {

    public suspend fun loadCurrentWeather(): Result<CurrentWeather>

    public suspend fun loadWeather(location: SelectedWeatherLocation): Result<CurrentWeather> =
        Result.failure(UnsupportedOperationException("Selected weather location is not supported"))
}
