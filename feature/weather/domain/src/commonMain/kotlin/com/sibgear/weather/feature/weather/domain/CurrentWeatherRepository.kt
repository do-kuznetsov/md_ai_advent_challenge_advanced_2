package com.sibgear.weather.feature.weather.domain

public interface CurrentWeatherRepository {

    public suspend fun loadCurrentWeather(): Result<CurrentWeather>
}
