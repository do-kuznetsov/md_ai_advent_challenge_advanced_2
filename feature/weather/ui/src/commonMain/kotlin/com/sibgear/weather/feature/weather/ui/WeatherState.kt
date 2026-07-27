package com.sibgear.weather.feature.weather.ui

public sealed interface WeatherState {

    public val cityQuery: String
    public val cityHistory: List<CityHistoryUiModel>

    public data class LoadingLocation(
        public override val cityQuery: String = "",
        public override val cityHistory: List<CityHistoryUiModel> = emptyList(),
    ) : WeatherState

    public data class LoadingWeather(
        public override val cityQuery: String = "",
        public override val cityHistory: List<CityHistoryUiModel> = emptyList(),
    ) : WeatherState

    public data class Content(
        public val weather: WeatherUiModel,
        public override val cityQuery: String = "",
        public override val cityHistory: List<CityHistoryUiModel> = emptyList(),
    ) : WeatherState

    public data class Error(
        public val message: String,
        public val canOpenSettings: Boolean,
        public override val cityQuery: String = "",
        public override val cityHistory: List<CityHistoryUiModel> = emptyList(),
    ) : WeatherState
}

public data class WeatherUiModel(
    public val cityName: String,
    public val temperature: String,
    public val conditionIcon: WeatherIcon,
    public val cloudCover: String,
    public val cloudCoverIcon: WeatherIcon,
    public val windSpeed: String,
    public val windSpeedIcon: WeatherIcon,
    public val precipitation: String,
    public val precipitationIcon: WeatherIcon,
)

public enum class WeatherIcon {
    Sunny,
    Cloud,
    Wind,
    Precipitation,
}
