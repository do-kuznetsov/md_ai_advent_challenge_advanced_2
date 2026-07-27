package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.core.mvvm.BaseViewModel
import com.sibgear.weather.feature.weather.domain.CityHistoryEntry
import com.sibgear.weather.feature.weather.domain.AddFavoriteCityInteractor
import com.sibgear.weather.feature.weather.domain.FavoriteCityEntry
import com.sibgear.weather.feature.weather.domain.GetCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import com.sibgear.weather.feature.weather.domain.GetFavoriteCitiesInteractor
import com.sibgear.weather.feature.weather.domain.RemoveFavoriteCityInteractor
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
    private val getFavoriteCities: GetFavoriteCitiesInteractor,
    private val addFavoriteCity: AddFavoriteCityInteractor,
    private val removeFavoriteCity: RemoveFavoriteCityInteractor,
    private val currentTimeMillis: () -> Long,
    private val mapper: WeatherUiMapper,
) : BaseViewModel<WeatherState, WeatherEvent, WeatherEffect>() {

    private val mutableState: MutableStateFlow<WeatherState> = MutableStateFlow(WeatherState.LoadingLocation())

    private var currentFavoriteCandidate: FavoriteCityEntry? = null

    public val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened -> handleScreenOpened()
            WeatherEvent.RetryClicked -> requestPermission()

            is WeatherEvent.CityQueryChanged -> updateCityQuery(event.query)
            WeatherEvent.CitySearchSubmitted -> submitCitySearch()
            is WeatherEvent.HistoryCityClicked -> loadHistoryCityWeather(event.city)
            is WeatherEvent.FavoriteCityClicked -> loadFavoriteCityWeather(event.city)
            WeatherEvent.FavoriteClicked -> toggleFavoriteCity()
            is WeatherEvent.MapLocationSelected -> loadMapLocationWeather(event.point)
            is WeatherEvent.LocationPermissionResult -> handlePermissionResult(event)
            WeatherEvent.SettingsClicked -> emitEffect(WeatherEffect.OpenAppSettings)
        }
    }

    private suspend fun handleScreenOpened() {
        requestPermission()
        loadRecentCities()
        loadFavoriteCities()
    }

    private suspend fun loadRecentCities() {
        val cityHistory = getCityHistory(RECENT_CITIES_LIMIT)
            .getOrElse { emptyList() }
            .map(mapper::map)

        mutableState.value = mutableState.value.withCityHistory(cityHistory)
    }

    private suspend fun requestPermission() {
        currentFavoriteCandidate = null
        mutableState.value = WeatherState.LoadingLocation(
            cityQuery = mutableState.value.cityQuery,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
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
            favoriteCities = mutableState.value.favoriteCities,
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
            favoriteCities = mutableState.value.favoriteCities,
        )
        val favoriteCity = city.toFavoriteEntry()

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

        currentFavoriteCandidate = favoriteCity
        mutableState.value = WeatherState.Content(
            weather = mapper.map(weather),
            canToggleFavorite = true,
            isFavorite = mutableState.value.favoriteCities.containsLocation(favoriteCity),
            cityQuery = city.name,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )
        saveCityToHistory(city.toHistoryEntry())
    }

    private suspend fun loadFavoriteCityWeather(city: FavoriteCityUiModel) {
        mutableState.value = WeatherState.LoadingWeather(
            cityQuery = city.name,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )
        val favoriteCity = city.toFavoriteEntry()

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

        currentFavoriteCandidate = favoriteCity
        mutableState.value = WeatherState.Content(
            weather = mapper.map(weather),
            canToggleFavorite = true,
            isFavorite = true,
            cityQuery = city.name,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
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

        val favoriteCity = candidate.toFavoriteEntry()
        currentFavoriteCandidate = favoriteCity
        mutableState.value = WeatherState.Content(
            weather = mapper.map(weather),
            canToggleFavorite = true,
            isFavorite = mutableState.value.favoriteCities.containsLocation(favoriteCity),
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )
        saveCityToHistory(candidate.toHistoryEntry())
    }

    private suspend fun loadMapLocationWeather(point: WeatherMapPoint) {
        val query = point.displayQuery()
        mutableState.value = WeatherState.LoadingWeather(
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )

        val weather = getCurrentWeather(
            SelectedWeatherLocation.Coordinates(
                latitude = point.latitude,
                longitude = point.longitude,
            ),
        ).getOrElse {
            mutableState.value = createWeatherLoadingError(query)
            return
        }

        currentFavoriteCandidate = null
        mutableState.value = WeatherState.Content(
            weather = mapper.map(weather),
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )
    }

    private suspend fun toggleFavoriteCity() {
        val content = mutableState.value as? WeatherState.Content ?: return
        val favoriteCity = currentFavoriteCandidate ?: return

        if (content.isFavorite) {
            removeFavoriteCity(favoriteCity)
        } else {
            addFavoriteCity(favoriteCity)
        }
        loadFavoriteCities()
        val updatedContent = mutableState.value as? WeatherState.Content ?: return
        mutableState.value = updatedContent.copy(
            isFavorite = updatedContent.favoriteCities.containsLocation(favoriteCity),
        )
    }

    private suspend fun saveCityToHistory(entry: CityHistoryEntry) {
        runCatching {
            saveCityHistory(entry)
        }
        loadRecentCities()
    }

    private suspend fun loadFavoriteCities() {
        val favoriteCities = getFavoriteCities()
            .getOrElse { emptyList() }
            .map(mapper::map)

        mutableState.value = mutableState.value.withFavoriteCities(favoriteCities)
    }

    private fun createCitySearchError(query: String): WeatherState.Error =
        WeatherState.Error(
            message = "Не удалось найти город. Повторите попытку.",
            canOpenSettings = false,
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )

    private fun createWeatherLoadingError(query: String): WeatherState.Error =
        WeatherState.Error(
            message = "Не удалось получить погоду. Повторите попытку.",
            canOpenSettings = false,
            cityQuery = query,
            cityHistory = mutableState.value.cityHistory,
            favoriteCities = mutableState.value.favoriteCities,
        )

    private suspend fun handlePermissionResult(event: WeatherEvent.LocationPermissionResult) {
        val cityQuery = mutableState.value.cityQuery
        val cityHistory = mutableState.value.cityHistory
        val favoriteCities = mutableState.value.favoriteCities

        if (!event.granted) {
            currentFavoriteCandidate = null
            mutableState.value = WeatherState.Error(
                message = "Для прогноза нужен доступ к геолокации.",
                canOpenSettings = event.permanentlyDenied,
                cityQuery = cityQuery,
                cityHistory = cityHistory,
                favoriteCities = favoriteCities,
            )
            return
        }

        currentFavoriteCandidate = null
        mutableState.value = WeatherState.LoadingWeather(
            cityQuery = cityQuery,
            cityHistory = cityHistory,
            favoriteCities = favoriteCities,
        )
        mutableState.value = getCurrentWeather().fold(
            onSuccess = {
                WeatherState.Content(
                    weather = mapper.map(it),
                    cityQuery = cityQuery,
                    cityHistory = cityHistory,
                    favoriteCities = favoriteCities,
                )
            },
            onFailure = {
                WeatherState.Error(
                    message = "Не удалось получить погоду. Повторите попытку.",
                    canOpenSettings = false,
                    cityQuery = cityQuery,
                    cityHistory = cityHistory,
                    favoriteCities = favoriteCities,
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

    private fun WeatherState.withFavoriteCities(favoriteCities: List<FavoriteCityUiModel>): WeatherState =
        when (this) {
            is WeatherState.LoadingLocation -> copy(favoriteCities = favoriteCities)
            is WeatherState.LoadingWeather -> copy(favoriteCities = favoriteCities)
            is WeatherState.Content -> copy(
                favoriteCities = favoriteCities,
                isFavorite = currentFavoriteCandidate?.let { favoriteCities.containsLocation(it) } ?: isFavorite,
            )
            is WeatherState.Error -> copy(favoriteCities = favoriteCities)
        }

    private fun WeatherCityCandidate.toHistoryEntry(): CityHistoryEntry =
        CityHistoryEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            selectedAtEpochMillis = currentTimeMillis(),
        )

    private fun WeatherCityCandidate.toFavoriteEntry(): FavoriteCityEntry =
        FavoriteCityEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
        )

    private fun CityHistoryUiModel.toHistoryEntry(): CityHistoryEntry =
        CityHistoryEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            selectedAtEpochMillis = currentTimeMillis(),
        )

    private fun CityHistoryUiModel.toFavoriteEntry(): FavoriteCityEntry =
        FavoriteCityEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
        )

    private fun FavoriteCityUiModel.toHistoryEntry(): CityHistoryEntry =
        CityHistoryEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            selectedAtEpochMillis = currentTimeMillis(),
        )

    private fun FavoriteCityUiModel.toFavoriteEntry(): FavoriteCityEntry =
        FavoriteCityEntry(
            name = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
        )

    private fun WeatherMapPoint.displayQuery(): String =
        "${latitude.formatCoordinate()}, ${longitude.formatCoordinate()}"

    private fun Double.formatCoordinate(): String =
        (this * COORDINATE_SCALE).toLong().toDouble()
            .let { it / COORDINATE_SCALE }
            .toString()

    private fun List<FavoriteCityUiModel>.containsLocation(entry: FavoriteCityEntry): Boolean =
        any { favorite ->
            favorite.latitude == entry.latitude && favorite.longitude == entry.longitude
        }

    private companion object {

        private const val RECENT_CITIES_LIMIT: Int = 5
        private const val COORDINATE_SCALE: Double = 10_000.0
    }
}
