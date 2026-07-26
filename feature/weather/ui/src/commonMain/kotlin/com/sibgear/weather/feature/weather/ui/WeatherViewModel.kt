package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.BaseViewModel
import com.sibgear.weather.feature.weather.domain.CurrentWeatherLocationUnavailableException
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class WeatherViewModel(
    private val getCurrentWeather: GetCurrentWeatherInteractor,
    private val mapper: WeatherUiMapper,
) : BaseViewModel<WeatherState, WeatherEvent, WeatherEffect>() {

    private val mutableState: MutableStateFlow<WeatherState> = MutableStateFlow(WeatherState.LoadingLocation)

    override val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened,
            WeatherEvent.RetryClicked,
            -> emitEffect(WeatherEffect.RequestLocationPermission)

            is WeatherEvent.LocationPermissionResult -> handlePermissionResult(event.granted)
            WeatherEvent.SettingsClicked -> emitEffect(WeatherEffect.OpenAppSettings)
        }
    }

    private suspend fun handlePermissionResult(granted: Boolean) {
        if (!granted) {
            mutableState.value = WeatherState.LocationAccessRequired
            return
        }

        mutableState.value = WeatherState.LoadingWeather
        val result = getCurrentWeather()
        mutableState.value =
            result.fold(
                onSuccess = { WeatherState.Content(mapper.map(it)) },
                onFailure = {
                    if (it is CurrentWeatherLocationUnavailableException) {
                        WeatherState.LocationUnavailable
                    } else {
                        WeatherState.WeatherUnavailable
                    }
                },
            )
    }
}
