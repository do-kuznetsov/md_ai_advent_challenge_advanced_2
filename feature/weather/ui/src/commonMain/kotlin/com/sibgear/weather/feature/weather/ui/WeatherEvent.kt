package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.ViewEvent

public sealed interface WeatherEvent : ViewEvent {

    public data object ScreenOpened : WeatherEvent

    public data class LocationPermissionResult(
        public val granted: Boolean,
    ) : WeatherEvent

    public data object RetryClicked : WeatherEvent

    public data object SettingsClicked : WeatherEvent
}
