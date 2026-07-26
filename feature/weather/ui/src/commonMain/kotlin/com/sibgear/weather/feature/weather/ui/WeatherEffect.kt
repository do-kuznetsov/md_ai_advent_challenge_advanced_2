package com.sibgear.weather.feature.weather.ui

public sealed interface WeatherEffect {

    public data object RequestLocationPermission : WeatherEffect

    public data object OpenAppSettings : WeatherEffect
}
