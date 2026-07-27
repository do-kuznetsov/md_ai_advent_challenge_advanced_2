package com.sibgear.weather.feature.weather.ui

public sealed interface WeatherState {

    public val cityQuery: String

    public data class LoadingLocation(
        public override val cityQuery: String = "",
    ) : WeatherState

    public data class LoadingWeather(
        public override val cityQuery: String = "",
    ) : WeatherState

    public data class Content(
        public val weather: WeatherUiModel,
        public override val cityQuery: String = "",
    ) : WeatherState

    public data class Error(
        public val message: String,
        public val canOpenSettings: Boolean,
        public override val cityQuery: String = "",
    ) : WeatherState
}

public data class WeatherUiModel(
    public val cityName: String,
    public val temperature: String,
    public val cloudCover: String,
    public val windSpeed: String,
    public val precipitation: String,
)
