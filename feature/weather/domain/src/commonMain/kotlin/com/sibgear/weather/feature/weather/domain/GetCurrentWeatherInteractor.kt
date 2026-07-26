package com.sibgear.weather.feature.weather.domain

public class GetCurrentWeatherInteractor(
    private val repository: CurrentWeatherRepository,
) {

    public suspend operator fun invoke(): Result<CurrentWeather> = repository.loadCurrentWeather()
}
