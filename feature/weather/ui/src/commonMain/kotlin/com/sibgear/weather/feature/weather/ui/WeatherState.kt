package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.ViewState

public sealed interface WeatherState : ViewState {

    public data object LoadingLocation : WeatherState

    public data object LoadingWeather : WeatherState

    public data object LocationAccessRequired : WeatherState

    public data object LocationUnavailable : WeatherState

    public data object WeatherUnavailable : WeatherState

    public data class Content(
        public val weather: WeatherUiModel,
    ) : WeatherState
}

public data class WeatherUiModel(
    public val cityName: String,
    public val temperature: String,
    public val cloudCover: String,
    public val windSpeed: String,
    public val precipitation: String,
)
