package com.sibgear.weather.feature.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
public actual fun WeatherMapScreen(
    onPointSelected: (WeatherMapPoint) -> Unit,
    modifier: Modifier,
) {
    WeatherFallbackMap(
        onPointSelected = onPointSelected,
        modifier = modifier,
    )
}
