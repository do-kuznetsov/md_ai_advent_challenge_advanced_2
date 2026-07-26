package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.SideEffect

public sealed interface WeatherEffect : SideEffect {
    public data object RequestLocationPermission : WeatherEffect

    public data object OpenAppSettings : WeatherEffect
}
