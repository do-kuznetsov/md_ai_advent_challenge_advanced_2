package com.sibgear.weather.feature.weather.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.util.ClickResult

@Composable
public actual fun WeatherMapScreen(
    onPointSelected: (WeatherMapPoint) -> Unit,
    modifier: Modifier,
) {
    MaplibreMap(
        modifier = modifier.fillMaxSize(),
        onMapClick = { position, _ ->
            onPointSelected(
                WeatherMapPoint(
                    latitude = position.latitude,
                    longitude = position.longitude,
                ),
            )
            ClickResult.Consume
        },
    )
}
