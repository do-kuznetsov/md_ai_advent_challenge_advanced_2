package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.BaseViewModel
import com.sibgear.weather.feature.weather.domain.CityHistoryEntry
import com.sibgear.weather.feature.weather.domain.GetCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import com.sibgear.weather.feature.weather.domain.SaveCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.SearchCitiesInteractor
import com.sibgear.weather.feature.weather.domain.SelectedWeatherLocation
import com.sibgear.weather.feature.weather.domain.WeatherCityCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class WeatherViewModel(
    private val getCurrentWeather: GetCurrentWeatherInteractor,
    private val searchCities: SearchCitiesInteractor,
    private val getCityHistory: GetCityHistoryInteractor,
    private val saveCityHistory: SaveCityHistoryInteractor,
    private val currentTimeMillis: () -> Long,
    private val mapper: WeatherUiMapper,
) : BaseViewModel<WeatherState, WeatherEvent, WeatherEffect>() {

    private val mutableState: MutableStateFlow<WeatherState> = MutableStateFlow(WeatherState.LoadingLocation())

    public val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened -> handleScreenOpened()
            WeatherEvent.RetryClicked -> requestPermission()

            is WeatherEvent.CityQueryChanged -> updateCityQuery(event.query)
            WeatherEvent.CitySearchSubmitted -> submitCitySearch()
            is WeatherEvent.HistoryCityClicked -> loadHistoryCityWeather(event.city)
            is WeatherEvent.LocationPermissionResult -> handlePermissionResult(event)
            WeatherEvent.SettingsClicked -> emitEffect(WeatherEffect.OpenAppSettings)
        }
    }

    private suspend fun handleScreenOpened() {
        requestPermission()
        loadRecentCities()
    }

    private suspend fun loadRecentCities() {
        val cityHistory = getCityHistory(RECENT_CITIES_LIMIT)
            .getOrElse { emptyList() }
            .map(mapper::map)

        mutableState.value = mutableState.value.withCityHistory(cityHistory)
    }

    private suspend fun requestPermission() {
        mutableState.value = WeatherState.LoadingLocation(
            cityQuery = mutableState.value.cityQuery,
            cityHistory = mutableState.value.cityHistory,
        )
        emitEffect(WeatherEffect.RequestLocationPermission)
    }

    private fun updateCityQuery(query: String) {
        mutableState.value = mutableState.value.withCityQuery(query)
    }

    private suspend fun submitCitySearch() {
        val query = mutableState.value.cityQuery
        if (query.isBlank()) {
            return
        }

        mutableState.value = WeatherState.LoadingWeather(
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
        )
        val candidate = searchCities(query).getOrElse {
            mutableState.value = createCitySearchError(query)
            return
        }.firstOrNull()

        if (candidate == null) {
            mutableState.value = createCitySearchError(query)
            return
        }

        loadCityWeather(query = query, candidate = candidate)
    }

    private suspend fun loadHistoryCityWeather(city: CityHistoryUiModel) {
        mutableState.value = WeatherState.LoadingWeather(
            cityQuery = city.name,
            cityHistory = mutableState.value.cityHistory,
        )

        val weather = getCurrentWeather(
            SelectedWeatherLocation.City(
                name = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
            ),
        ).getOrElse {
            mutableState.value = createWeatherLoadingError(city.name)
            return
        }

        mutableState.value = WeatherState.Content(
            weather = mapper.map(weather),
            cityQuery = city.name,
            cityHistory = mutableState.value.cityHistory,
        )
        saveCityToHistory(city.toHistoryEntry())
    }

    private suspend fun loadCityWeather(query: String, candidate: WeatherCityCandidate) {
        val location = SelectedWeatherLocation.City(
            name = candidate.name,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
        )

        val weather = getCurrentWeather(location).getOrElse {
            mutableState.value = createCitySearchError(query)
            return
        }

        mutableState.value = WeatherState.Content(
            weather = mapper.map(weather),
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
        )
        saveCityToHistory(candidate.toHistoryEntry())
    }

    private suspend fun saveCityToHistory(entry: CityHistoryEntry) {
        runCatching {
            saveCityHistory(entry)
        }
        loadRecentCities()
    }

    private fun createCitySearchError(query: String): WeatherState.Error =
        WeatherState.Error(
            message = "Не удалось найти город. Повторите попытку.",
            canOpenSettings = false,
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
        )

    private fun createWeatherLoadingError(query: String): WeatherState.Error =
        WeatherState.Error(
            message = "Не удалось получить погоду. Повторите попытку.",
            canOpenSettings = false,
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
        )

    private suspend fun handlePermissionResult(event: WeatherEvent.LocationPermissionResult) {
        val cityQuery = mutableState.value.cityQuery
        val cityHistory = mutableState.value.cityHistory

        if (!event.granted) {
            mutableState.value = WeatherState.Error(
                message = "Для прогноза нужен доступ к геолокации.",
                canOpenSettings = event.permanentlyDenied,
                cityQuery = cityQuery,
                cityHistory = cityHistory,
            )
            return
        }

        mutableState.value = WeatherState.LoadingWeather(cityQuery = cityQuery, cityHistory = cityHistory)
        mutableState.value = getCurrentWeather().fold(
            onSuccess = {
                WeatherState.Content(
                    weather = mapper.map(it),
                    cityQuery = cityQuery,
                    cityHistory = cityHistory,
                )
            },
            onFailure = {
                WeatherState.Error(
                    message = "Не удалось получить погоду. Повторите попытку.",
                    canOpenSettings = false,
                    cityQuery = cityQuery,
                    cityHistory = cityHistory,
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

    private fun WeatherState.withCityHistory(cityHistory: List<CityHistoryUiModel>): WeatherState =
        when (this) {
            is WeatherState.LoadingLocation -> copy(cityHistory = cityHistory)
            is WeatherState.LoadingWeather -> copy(cityHistory = cityHistory)
            is WeatherState.Content -> copy(cityHistory = cityHistory)
            is WeatherState.Error -> copy(cityHistory = cityHistory)
        }

    private fun WeatherCityCandidate.toHistoryEntry(): CityHistoryEntry =
        CityHistoryEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            selectedAtEpochMillis = currentTimeMillis(),
        )

    private fun CityHistoryUiModel.toHistoryEntry(): CityHistoryEntry =
        CityHistoryEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            selectedAtEpochMillis = currentTimeMillis(),
        )

    private companion object {

        private const val RECENT_CITIES_LIMIT: Int = 5
    }
}
