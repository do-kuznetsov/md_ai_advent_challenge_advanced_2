package com.sibgear.weather.feature.weather.ui

public sealed interface WeatherState {

    public data object LoadingLocation : WeatherState

    public data object LoadingWeather : WeatherState

    public data class Content(
        public val weather: WeatherUiModel,
    ) : WeatherState

    public data class Error(
        public val message: String,
        public val canOpenSettings: Boolean,
    ) : WeatherState
}

public data class WeatherUiModel(
    public val cityName: String,
    public val temperature: String,
    public val cloudCover: String,
    public val windSpeed: String,
    public val precipitation: String,
)
