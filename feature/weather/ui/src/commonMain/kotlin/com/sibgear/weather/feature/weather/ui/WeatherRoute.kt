package com.sibgear.weather.feature.weather.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
public sealed interface WeatherRoute : NavKey {

    @Serializable
    public data object List : WeatherRoute

    @Serializable
    public data object Map : WeatherRoute
}
