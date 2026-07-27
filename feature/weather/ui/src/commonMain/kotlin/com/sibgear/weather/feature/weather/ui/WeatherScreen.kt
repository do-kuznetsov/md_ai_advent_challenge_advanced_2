package com.sibgear.weather.feature.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CitySearchContent(
                cityQuery = state.cityQuery,
                onCityQueryChanged = { viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged(it)) },
                onCitySearchSubmitted = { viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted) },
            )

            when (val currentState = state) {
                is WeatherState.LoadingLocation -> LoadingContent(
                    message = "Определяем местоположение",
                    modifier = Modifier.weight(1f),
                )
                is WeatherState.LoadingWeather -> LoadingContent(
                    message = "Получаем погоду",
                    modifier = Modifier.weight(1f),
                )
                is WeatherState.Content -> WeatherContent(
                    weather = currentState.weather,
                    modifier = Modifier.weight(1f),
                )
                is WeatherState.Error -> ErrorContent(
                    state = currentState,
                    onRetryClicked = { viewModel.onViewEventOccurred(WeatherEvent.RetryClicked) },
                    onSettingsClicked = { viewModel.onViewEventOccurred(WeatherEvent.SettingsClicked) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CitySearchContent(
    cityQuery: String,
    onCityQueryChanged: (String) -> Unit,
    onCitySearchSubmitted: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = cityQuery,
            onValueChange = onCityQueryChanged,
            modifier = Modifier.weight(1f),
            label = { Text("Город") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onCitySearchSubmitted() }),
        )
        Button(
            onClick = onCitySearchSubmitted,
            enabled = cityQuery.isNotBlank(),
        ) {
            Text("Найти")
        }
    }
}

@Composable
private fun LoadingContent(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.h6)
    }
}

@Composable
private fun WeatherContent(weather: WeatherUiModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
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
