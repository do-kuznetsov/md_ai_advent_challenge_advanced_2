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

    private val mutableState: MutableStateFlow<WeatherState> = MutableStateFlow(WeatherState.LoadingLocation())

    public val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened,
            WeatherEvent.RetryClicked,
            -> requestPermission()

            is WeatherEvent.CityQueryChanged -> updateCityQuery(event.query)
            WeatherEvent.CitySearchSubmitted -> submitCitySearch()
            is WeatherEvent.LocationPermissionResult -> handlePermissionResult(event)
            WeatherEvent.SettingsClicked -> emitEffect(WeatherEffect.OpenAppSettings)
        }
    }

    private suspend fun requestPermission() {
        mutableState.value = WeatherState.LoadingLocation(cityQuery = mutableState.value.cityQuery)
        emitEffect(WeatherEffect.RequestLocationPermission)
    }

    private fun updateCityQuery(query: String) {
        mutableState.value = mutableState.value.withCityQuery(query)
    }

    private fun submitCitySearch() {
        if (mutableState.value.cityQuery.isBlank()) {
            return
        }
    }

    private suspend fun handlePermissionResult(event: WeatherEvent.LocationPermissionResult) {
        val cityQuery = mutableState.value.cityQuery

        if (!event.granted) {
            mutableState.value = WeatherState.Error(
                message = "Для прогноза нужен доступ к геолокации.",
                canOpenSettings = event.permanentlyDenied,
                cityQuery = cityQuery,
            )
            return
        }

        mutableState.value = WeatherState.LoadingWeather(cityQuery = cityQuery)
        mutableState.value = getCurrentWeather().fold(
            onSuccess = { WeatherState.Content(weather = mapper.map(it), cityQuery = cityQuery) },
            onFailure = {
                WeatherState.Error(
                    message = "Не удалось получить погоду. Повторите попытку.",
                    canOpenSettings = false,
                    cityQuery = cityQuery,
                )
            },
        )
    }

    private fun WeatherState.withCityQuery(query: String): WeatherState =
        when (this) {
            is WeatherState.LoadingLocation -> copy(cityQuery = query)
            is WeatherState.LoadingWeather -> copy(cityQuery = query)
            is WeatherState.Content -> copy(cityQuery = query)
            is WeatherState.Error -> copy(cityQuery = query)
        }
}
