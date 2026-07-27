package com.sibgear.weather.feature.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
public actual fun WeatherMapScreen(modifier: Modifier) {
    WeatherFallbackMap(modifier = modifier)
}
