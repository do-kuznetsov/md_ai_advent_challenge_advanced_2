package com.sibgear.weather.feature.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

@Composable
public fun WeatherScreen(
    viewModel: WeatherViewModel,
    onEffect: (WeatherEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state: WeatherState by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect(onEffect)
    }
    LaunchedEffect(viewModel) {
        viewModel.onViewEventOccurred(WeatherEvent.ScreenOpened)
    }

    Surface(modifier = modifier.fillMaxSize()) {
        when (val currentState = state) {
            WeatherState.LoadingLocation -> LoadingContent("Определяем местоположение")
            WeatherState.LoadingWeather -> LoadingContent("Получаем погоду")
            is WeatherState.Content -> WeatherContent(currentState.weather)
            is WeatherState.Error -> ErrorContent(
                state = currentState,
                onRetryClicked = { viewModel.onViewEventOccurred(WeatherEvent.RetryClicked) },
                onSettingsClicked = { viewModel.onViewEventOccurred(WeatherEvent.SettingsClicked) },
            )
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.h6)
    }
}

@Composable
private fun WeatherContent(weather: WeatherUiModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = weather.cityName, style = MaterialTheme.typography.h5)
        Text(
            text = weather.temperature,
            style = MaterialTheme.typography.h2,
            fontWeight = FontWeight.Bold,
        )
        Divider()
        WeatherMetric(label = "Облачность", value = weather.cloudCover)
        WeatherMetric(label = "Ветер", value = weather.windSpeed)
        WeatherMetric(label = "Осадки", value = weather.precipitation)
        Spacer(Modifier.weight(1f))
        Text(text = "Данные: Open-Meteo.com", style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun WeatherMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.body1)
        Text(text = value, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorContent(
    state: WeatherState.Error,
    onRetryClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = state.message, style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetryClicked) {
            Text("Повторить")
        }
        if (state.canOpenSettings) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSettingsClicked) {
                Text("Открыть настройки")
            }
        }
    }
}
