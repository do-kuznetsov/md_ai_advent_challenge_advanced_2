package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.BaseViewModel
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class WeatherViewModel(
    private val getCurrentWeather: GetCurrentWeatherInteractor,
    private val mapper: WeatherUiMapper,
) : BaseViewModel<WeatherState, WeatherEvent, WeatherEffect>() {

    private val mutableState: MutableStateFlow<WeatherState> = MutableStateFlow(WeatherState.LoadingLocation)

    public val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened,
            WeatherEvent.RetryClicked,
            -> requestPermission()

            is WeatherEvent.LocationPermissionResult -> handlePermissionResult(event)
            WeatherEvent.SettingsClicked -> emitEffect(WeatherEffect.OpenAppSettings)
        }
    }

    private suspend fun requestPermission() {
        mutableState.value = WeatherState.LoadingLocation
        emitEffect(WeatherEffect.RequestLocationPermission)
    }

    private suspend fun handlePermissionResult(event: WeatherEvent.LocationPermissionResult) {
        if (!event.granted) {
            mutableState.value = WeatherState.Error(
                message = "Для прогноза нужен доступ к геолокации.",
                canOpenSettings = event.permanentlyDenied,
            )
            return
        }

        mutableState.value = WeatherState.LoadingWeather
        mutableState.value = getCurrentWeather().fold(
            onSuccess = { WeatherState.Content(mapper.map(it)) },
            onFailure = { WeatherState.Error("Не удалось получить погоду. Повторите попытку.", false) },
        )
    }
}
