package com.sibgear.weather.feature.weather.ui

public sealed interface WeatherEvent {

    public data object ScreenOpened : WeatherEvent

    public data object RetryClicked : WeatherEvent

    public data class LocationPermissionResult(
        public val granted: Boolean,
        public val permanentlyDenied: Boolean,
    ) : WeatherEvent

    public data object SettingsClicked : WeatherEvent
}
