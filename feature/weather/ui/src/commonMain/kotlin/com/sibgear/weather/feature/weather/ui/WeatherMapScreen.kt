package com.sibgear.weather.feature.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
public expect fun WeatherMapScreen(
    onPointSelected: (WeatherMapPoint) -> Unit,
    modifier: Modifier = Modifier,
)
