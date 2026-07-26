package com.sibgear.weather.feature.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WindPower
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun WeatherScreen(
    viewModel: WeatherViewModel,
    onEffect: (WeatherEffect) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect(onEffect)
    }
    LaunchedEffect(viewModel) {
        viewModel.onViewEventOccurred(WeatherEvent.ScreenOpened)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Погода сейчас") },
                actions = {
                    IconButton(onClick = { viewModel.onViewEventOccurred(WeatherEvent.RetryClicked) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить погоду")
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val currentState = state) {
            WeatherState.LoadingLocation,
            WeatherState.LoadingWeather,
            -> LoadingContent(modifier = Modifier.padding(paddingValues))

            WeatherState.LocationAccessRequired ->
                PermissionContent(
                    modifier = Modifier.padding(paddingValues),
                    onRetry = { viewModel.onViewEventOccurred(WeatherEvent.RetryClicked) },
                    onSettings = { viewModel.onViewEventOccurred(WeatherEvent.SettingsClicked) },
                )

            WeatherState.LocationUnavailable ->
                ErrorContent(
                    modifier = Modifier.padding(paddingValues),
                    message = "Не удалось определить местоположение",
                    onRetry = { viewModel.onViewEventOccurred(WeatherEvent.RetryClicked) },
                )

            WeatherState.WeatherUnavailable ->
                ErrorContent(
                    modifier = Modifier.padding(paddingValues),
                    message = "Не удалось загрузить погоду",
                    onRetry = { viewModel.onViewEventOccurred(WeatherEvent.RetryClicked) },
                )

            is WeatherState.Content ->
                WeatherContent(
                    modifier = Modifier.padding(paddingValues),
                    weather = currentState.weather,
                )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = "Определяем местоположение",
        )
    }
}

@Composable
private fun PermissionContent(
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Нужен доступ к геолокации", style = MaterialTheme.typography.titleLarge)
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "Покажем погоду для текущего города. Доступ используется только пока открыт экран.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(modifier = Modifier.padding(top = 24.dp), onClick = onRetry) {
            Text("Разрешить доступ")
        }
        IconButton(modifier = Modifier.padding(top = 8.dp), onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Открыть настройки приложения")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(modifier = Modifier.padding(top = 16.dp), onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun WeatherContent(
    weather: WeatherUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            modifier = Modifier.padding(top = 20.dp),
            text = weather.cityName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MetricCard(
            icon = Icons.Outlined.Thermostat,
            label = "Температура",
            value = weather.temperature,
        )
        MetricCard(
            icon = Icons.Outlined.Cloud,
            label = "Облачность",
            value = weather.cloudCover,
        )
        MetricCard(
            icon = Icons.Outlined.WindPower,
            label = "Ветер",
            value = weather.windSpeed,
        )
        MetricCard(
            icon = Icons.Outlined.Umbrella,
            label = "Осадки",
            value = weather.precipitation,
        )
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = "Данные: Open-Meteo",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(28.dp),
                imageVector = icon,
                contentDescription = null,
            )
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
