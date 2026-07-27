package com.sibgear.weather.feature.weather.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.map.MaplibreMap

@Composable
public actual fun WeatherMapScreen(modifier: Modifier) {
    MaplibreMap(modifier = modifier.fillMaxSize())
}
