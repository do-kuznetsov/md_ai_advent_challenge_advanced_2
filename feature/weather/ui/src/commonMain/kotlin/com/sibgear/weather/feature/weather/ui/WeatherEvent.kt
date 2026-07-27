package com.sibgear.weather.feature.weather.ui

public sealed interface WeatherEvent {

    public data object ScreenOpened : WeatherEvent

    public data object RetryClicked : WeatherEvent

    public data class CityQueryChanged(
        public val query: String,
    ) : WeatherEvent

    public data object CitySearchSubmitted : WeatherEvent

    public data class HistoryCityClicked(
        public val city: CityHistoryUiModel,
    ) : WeatherEvent

    public data class FavoriteCityClicked(
        public val city: FavoriteCityUiModel,
    ) : WeatherEvent

    public data object FavoriteClicked : WeatherEvent

    public data class LocationPermissionResult(
        public val granted: Boolean,
        public val permanentlyDenied: Boolean,
    ) : WeatherEvent

    public data object SettingsClicked : WeatherEvent
}
