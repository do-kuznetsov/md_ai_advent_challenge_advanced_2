package com.sibgear.weather.feature.weather.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.sibgear.weather.feature.weather.domain.AddFavoriteCityInteractor
import com.sibgear.weather.feature.weather.domain.CityHistoryEntry
import com.sibgear.weather.feature.weather.domain.CityHistoryRepository
import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.FavoriteCityEntry
import com.sibgear.weather.feature.weather.domain.FavoriteCityRepository
import com.sibgear.weather.feature.weather.domain.GetCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import com.sibgear.weather.feature.weather.domain.GetFavoriteCitiesInteractor
import com.sibgear.weather.feature.weather.domain.RemoveFavoriteCityInteractor
import com.sibgear.weather.feature.weather.domain.SaveCityHistoryInteractor
import com.sibgear.weather.feature.weather.domain.SearchCitiesInteractor
import com.sibgear.weather.feature.weather.domain.SelectedWeatherLocation
import com.sibgear.weather.feature.weather.domain.WeatherCityCandidate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
public class WeatherViewModelTest {

    @Test
    public fun screenOpenedRequestsLocationPermission(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel()
            val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.first() }

            viewModel.onViewEventOccurred(WeatherEvent.ScreenOpened)
            advanceUntilIdle()

            assertEquals(WeatherEffect.RequestLocationPermission, effect.await())
            assertEquals(WeatherState.LoadingLocation(), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun screenOpenedLoadsCityHistory(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val cityHistoryRepository = RecordingCityHistoryRepository(
                initialRecentEntries = createCityHistoryEntries(),
            )
            val viewModel = createViewModel(cityHistoryRepository = cityHistoryRepository)
            val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.first() }

            viewModel.onViewEventOccurred(WeatherEvent.ScreenOpened)
            advanceUntilIdle()

            assertEquals(WeatherEffect.RequestLocationPermission, effect.await())
            assertEquals(1, cityHistoryRepository.recentCitiesCount)
            assertEquals(5, cityHistoryRepository.lastLimit)
            assertEquals(
                listOf(
                    CityHistoryUiModel(
                        name = "Москва",
                        country = "Россия",
                        displayName = "Москва, Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                    ),
                    CityHistoryUiModel(
                        name = "Томск",
                        country = null,
                        displayName = "Томск",
                        latitude = 56.4846,
                        longitude = 84.9476,
                    ),
                ),
                viewModel.state.value.cityHistory,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun screenOpenedLoadsFavoriteCities(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val favoriteCityRepository = RecordingFavoriteCityRepository(
                initialEntries = listOf(createFavoriteEntry()),
            )
            val viewModel = createViewModel(favoriteCityRepository = favoriteCityRepository)
            val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.first() }

            viewModel.onViewEventOccurred(WeatherEvent.ScreenOpened)
            advanceUntilIdle()

            assertEquals(WeatherEffect.RequestLocationPermission, effect.await())
            assertEquals(1, favoriteCityRepository.favoriteCitiesCount)
            assertEquals(
                listOf(
                    FavoriteCityUiModel(
                        name = "Москва",
                        country = "Россия",
                        displayName = "Москва, Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                    ),
                ),
                viewModel.state.value.favoriteCities,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun grantedPermissionLoadsWeather(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(weatherResult = Result.success(createWeather()))

            viewModel.onViewEventOccurred(
                WeatherEvent.LocationPermissionResult(granted = true, permanentlyDenied = false),
            )
            advanceUntilIdle()

            val state = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals("Новосибирск", state.weather.cityName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun permanentPermissionDenialOffersSettings(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel()

            viewModel.onViewEventOccurred(
                WeatherEvent.LocationPermissionResult(granted = false, permanentlyDenied = true),
            )
            advanceUntilIdle()

            val state = assertIs<WeatherState.Error>(viewModel.state.value)
            assertEquals(true, state.canOpenSettings)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun failedWeatherLoadingShowsRetryableError(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(weatherResult = Result.failure(IllegalStateException("network failed")))

            viewModel.onViewEventOccurred(
                WeatherEvent.LocationPermissionResult(granted = true, permanentlyDenied = false),
            )
            advanceUntilIdle()

            val state = assertIs<WeatherState.Error>(viewModel.state.value)
            assertEquals("Не удалось получить погоду. Повторите попытку.", state.message)
            assertEquals(false, state.canOpenSettings)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun settingsClickedEmitsOpenAppSettingsEffect(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel()
            val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.first() }

            viewModel.onViewEventOccurred(WeatherEvent.SettingsClicked)
            advanceUntilIdle()

            assertEquals(WeatherEffect.OpenAppSettings, effect.await())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun cityQueryChangedUpdatesState(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel()

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("Томск"))
            advanceUntilIdle()

            assertEquals("Томск", viewModel.state.value.cityQuery)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun blankCitySearchSubmittedDoesNotLoadWeatherOrRequestPermission(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = CountingWeatherRepository(Result.success(createWeather()))
            val citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates()))
            val viewModel = createViewModel(
                repository = repository,
                citySearchRepository = citySearchRepository,
            )
            val effects = mutableListOf<WeatherEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effect.toList(effects)
            }

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("   "))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            assertEquals(0, repository.currentLocationLoadCount)
            assertEquals(0, repository.selectedLocationLoadCount)
            assertEquals(0, citySearchRepository.searchCount)
            assertEquals(emptyList(), effects)
            assertEquals(WeatherState.LoadingLocation(cityQuery = "   "), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun citySearchSubmittedLoadsWeatherForFirstCityCandidate(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = CountingWeatherRepository(
                weatherResult = Result.success(createWeather(cityName = "Москва")),
            )
            val citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates()))
            val viewModel = createViewModel(
                repository = repository,
                citySearchRepository = citySearchRepository,
            )

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("Москва"))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            val state = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals("Москва", state.weather.cityName)
            assertEquals("Москва", state.cityQuery)
            assertEquals(1, citySearchRepository.searchCount)
            assertEquals("Москва", citySearchRepository.lastQuery)
            assertEquals(0, repository.currentLocationLoadCount)
            assertEquals(1, repository.selectedLocationLoadCount)
            assertEquals(
                SelectedWeatherLocation.City(
                    name = "Москва",
                    latitude = 55.7558,
                    longitude = 37.6173,
                ),
                repository.lastSelectedLocation,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun historyCityClickedLoadsWeatherForHistoryCity(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = CountingWeatherRepository(
                weatherResult = Result.success(createWeather(cityName = "Москва")),
            )
            val citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates()))
            val cityHistoryRepository = RecordingCityHistoryRepository()
            val viewModel = createViewModel(
                repository = repository,
                citySearchRepository = citySearchRepository,
                cityHistoryRepository = cityHistoryRepository,
            )

            viewModel.onViewEventOccurred(
                WeatherEvent.HistoryCityClicked(
                    CityHistoryUiModel(
                        name = "Москва",
                        country = "Россия",
                        displayName = "Москва, Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                    ),
                ),
            )
            advanceUntilIdle()

            val state = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals("Москва", state.weather.cityName)
            assertEquals("Москва", state.cityQuery)
            assertEquals(0, citySearchRepository.searchCount)
            assertEquals(0, repository.currentLocationLoadCount)
            assertEquals(1, repository.selectedLocationLoadCount)
            assertEquals(
                SelectedWeatherLocation.City(
                    name = "Москва",
                    latitude = 55.7558,
                    longitude = 37.6173,
                ),
                repository.lastSelectedLocation,
            )
            assertEquals(
                listOf(
                    CityHistoryEntry(
                        name = "Москва",
                        country = "Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                        selectedAtEpochMillis = TEST_SELECTED_AT_EPOCH_MILLIS,
                    ),
                ),
                cityHistoryRepository.savedEntries,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun favoriteCityClickedLoadsWeatherForFavoriteCity(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = CountingWeatherRepository(
                weatherResult = Result.success(createWeather(cityName = "Москва")),
            )
            val cityHistoryRepository = RecordingCityHistoryRepository()
            val viewModel = createViewModel(
                repository = repository,
                cityHistoryRepository = cityHistoryRepository,
                favoriteCityRepository = RecordingFavoriteCityRepository(
                    initialEntries = listOf(createFavoriteEntry()),
                ),
            )

            viewModel.onViewEventOccurred(
                WeatherEvent.FavoriteCityClicked(
                    FavoriteCityUiModel(
                        name = "Москва",
                        country = "Россия",
                        displayName = "Москва, Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                    ),
                ),
            )
            advanceUntilIdle()

            val state = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals("Москва", state.weather.cityName)
            assertEquals("Москва", state.cityQuery)
            assertEquals(true, state.canToggleFavorite)
            assertEquals(true, state.isFavorite)
            assertEquals(0, repository.currentLocationLoadCount)
            assertEquals(1, repository.selectedLocationLoadCount)
            assertEquals(
                SelectedWeatherLocation.City(
                    name = "Москва",
                    latitude = 55.7558,
                    longitude = 37.6173,
                ),
                repository.lastSelectedLocation,
            )
            assertEquals(
                listOf(
                    CityHistoryEntry(
                        name = "Москва",
                        country = "Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                        selectedAtEpochMillis = TEST_SELECTED_AT_EPOCH_MILLIS,
                    ),
                ),
                cityHistoryRepository.savedEntries,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun favoriteClickedAddsCurrentSelectedCityAndUpdatesState(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val favoriteCityRepository = RecordingFavoriteCityRepository()
            val viewModel = createViewModel(
                weatherResult = Result.success(createWeather(cityName = "Москва")),
                citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates())),
                favoriteCityRepository = favoriteCityRepository,
            )

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("Москва"))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            val initialState = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals(true, initialState.canToggleFavorite)
            assertEquals(false, initialState.isFavorite)

            viewModel.onViewEventOccurred(WeatherEvent.FavoriteClicked)
            advanceUntilIdle()

            val updatedState = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals(true, updatedState.isFavorite)
            assertEquals(listOf(createFavoriteEntry()), favoriteCityRepository.entries)
            assertEquals(
                listOf(
                    FavoriteCityUiModel(
                        name = "Москва",
                        country = "Россия",
                        displayName = "Москва, Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                    ),
                ),
                updatedState.favoriteCities,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun favoriteClickedRemovesCurrentSelectedCityAndUpdatesState(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val favoriteCityRepository = RecordingFavoriteCityRepository(
                initialEntries = listOf(createFavoriteEntry()),
            )
            val viewModel = createViewModel(
                repository = CountingWeatherRepository(Result.success(createWeather(cityName = "Москва"))),
                favoriteCityRepository = favoriteCityRepository,
            )

            viewModel.onViewEventOccurred(
                WeatherEvent.FavoriteCityClicked(
                    FavoriteCityUiModel(
                        name = "Москва",
                        country = "Россия",
                        displayName = "Москва, Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(true, assertIs<WeatherState.Content>(viewModel.state.value).isFavorite)

            viewModel.onViewEventOccurred(WeatherEvent.FavoriteClicked)
            advanceUntilIdle()

            val updatedState = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals(false, updatedState.isFavorite)
            assertEquals(emptyList(), favoriteCityRepository.entries)
            assertEquals(emptyList(), updatedState.favoriteCities)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun favoriteClickedHasNoEffectForCurrentLocationWeather(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val favoriteCityRepository = RecordingFavoriteCityRepository()
            val viewModel = createViewModel(
                weatherResult = Result.success(createWeather()),
                favoriteCityRepository = favoriteCityRepository,
            )

            viewModel.onViewEventOccurred(
                WeatherEvent.LocationPermissionResult(granted = true, permanentlyDenied = false),
            )
            viewModel.onViewEventOccurred(WeatherEvent.FavoriteClicked)
            advanceUntilIdle()

            val state = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals(false, state.canToggleFavorite)
            assertEquals(false, state.isFavorite)
            assertEquals(emptyList(), favoriteCityRepository.entries)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun citySearchSubmittedSavesFirstCityCandidateAfterWeatherSuccess(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val cityHistoryRepository = RecordingCityHistoryRepository()
            val viewModel = createViewModel(
                weatherResult = Result.success(createWeather(cityName = "Москва")),
                citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates())),
                cityHistoryRepository = cityHistoryRepository,
            )

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("Москва"))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals(
                listOf(
                    CityHistoryEntry(
                        name = "Москва",
                        country = "Россия",
                        latitude = 55.7558,
                        longitude = 37.6173,
                        selectedAtEpochMillis = TEST_SELECTED_AT_EPOCH_MILLIS,
                    ),
                ),
                cityHistoryRepository.savedEntries,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun citySearchSubmittedDoesNotSaveCityWhenWeatherFails(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val cityHistoryRepository = RecordingCityHistoryRepository()
            val viewModel = createViewModel(
                weatherResult = Result.failure(IllegalStateException("network failed")),
                citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates())),
                cityHistoryRepository = cityHistoryRepository,
            )

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("Москва"))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            assertIs<WeatherState.Error>(viewModel.state.value)
            assertEquals(emptyList(), cityHistoryRepository.savedEntries)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun citySearchSubmittedKeepsWeatherContentWhenCityHistorySaveFails(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val cityHistoryRepository = RecordingCityHistoryRepository(
                saveResult = Result.failure(IllegalStateException("storage failed")),
            )
            val viewModel = createViewModel(
                weatherResult = Result.success(createWeather(cityName = "Москва")),
                citySearchRepository = CountingCitySearchRepository(Result.success(createCityCandidates())),
                cityHistoryRepository = cityHistoryRepository,
            )

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("Москва"))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            val state = assertIs<WeatherState.Content>(viewModel.state.value)
            assertEquals("Москва", state.weather.cityName)
            assertEquals(1, cityHistoryRepository.savedEntries.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    public fun citySearchSubmittedShowsRetryableErrorWhenNoCandidatesFound(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = CountingWeatherRepository(Result.success(createWeather()))
            val viewModel = createViewModel(
                repository = repository,
                citySearchRepository = CountingCitySearchRepository(Result.success(emptyList())),
            )

            viewModel.onViewEventOccurred(WeatherEvent.CityQueryChanged("НетТакогоГорода"))
            viewModel.onViewEventOccurred(WeatherEvent.CitySearchSubmitted)
            advanceUntilIdle()

            val state = assertIs<WeatherState.Error>(viewModel.state.value)
            assertEquals("Не удалось найти город. Повторите попытку.", state.message)
            assertEquals(false, state.canOpenSettings)
            assertEquals("НетТакогоГорода", state.cityQuery)
            assertEquals(0, repository.currentLocationLoadCount)
            assertEquals(0, repository.selectedLocationLoadCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        weatherResult: Result<CurrentWeather> = Result.success(createWeather()),
        repository: CurrentWeatherRepository = CountingWeatherRepository(weatherResult),
        citySearchRepository: CitySearchRepository = CountingCitySearchRepository(Result.success(emptyList())),
        cityHistoryRepository: CityHistoryRepository = RecordingCityHistoryRepository(),
        favoriteCityRepository: FavoriteCityRepository = RecordingFavoriteCityRepository(),
        currentTimeMillis: () -> Long = { TEST_SELECTED_AT_EPOCH_MILLIS },
    ): WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(
                repository = repository,
            ),
            searchCities = SearchCitiesInteractor(
                repository = citySearchRepository,
            ),
            getCityHistory = GetCityHistoryInteractor(
                repository = cityHistoryRepository,
            ),
            saveCityHistory = SaveCityHistoryInteractor(
                repository = cityHistoryRepository,
            ),
            getFavoriteCities = GetFavoriteCitiesInteractor(
                repository = favoriteCityRepository,
            ),
            addFavoriteCity = AddFavoriteCityInteractor(
                repository = favoriteCityRepository,
            ),
            removeFavoriteCity = RemoveFavoriteCityInteractor(
                repository = favoriteCityRepository,
            ),
            currentTimeMillis = currentTimeMillis,
            mapper = WeatherUiMapper(),
        )

    private fun createWeather(cityName: String = "Новосибирск"): CurrentWeather =
        CurrentWeather(
            cityName = cityName,
            temperatureCelsius = 18.4,
            cloudCoverPercent = 62,
            windSpeedKilometersPerHour = 12.6,
            precipitationMillimeters = 0.4,
        )

    private fun createCityCandidates(): List<WeatherCityCandidate> =
        listOf(
            WeatherCityCandidate(
                name = "Москва",
                country = "Россия",
                latitude = 55.7558,
                longitude = 37.6173,
            ),
            WeatherCityCandidate(
                name = "Москва",
                country = "США",
                latitude = 46.7324,
                longitude = -117.0002,
            ),
        )

    private fun createCityHistoryEntries(): List<CityHistoryEntry> =
        listOf(
            CityHistoryEntry(
                name = "Москва",
                country = "Россия",
                latitude = 55.7558,
                longitude = 37.6173,
                selectedAtEpochMillis = 1_723_000_000_000,
            ),
            CityHistoryEntry(
                name = "Томск",
                country = null,
                latitude = 56.4846,
                longitude = 84.9476,
                selectedAtEpochMillis = 1_722_000_000_000,
            ),
        )

    private fun createFavoriteEntry(): FavoriteCityEntry =
        FavoriteCityEntry(
            name = "Москва",
            country = "Россия",
            latitude = 55.7558,
            longitude = 37.6173,
        )

    private class CountingWeatherRepository(
        private val weatherResult: Result<CurrentWeather>,
    ) : CurrentWeatherRepository {

        var currentLocationLoadCount: Int = 0
            private set

        var selectedLocationLoadCount: Int = 0
            private set

        var lastSelectedLocation: SelectedWeatherLocation? = null
            private set

        override suspend fun loadCurrentWeather(): Result<CurrentWeather> {
            currentLocationLoadCount += 1
            return weatherResult
        }

        override suspend fun loadWeather(location: SelectedWeatherLocation): Result<CurrentWeather> {
            selectedLocationLoadCount += 1
            lastSelectedLocation = location
            return weatherResult
        }
    }

    private class CountingCitySearchRepository(
        private val result: Result<List<WeatherCityCandidate>>,
    ) : CitySearchRepository {

        var searchCount: Int = 0
            private set

        var lastQuery: String? = null
            private set

        override suspend fun searchCities(query: String): Result<List<WeatherCityCandidate>> {
            searchCount += 1
            lastQuery = query
            return result
        }
    }

    private class RecordingCityHistoryRepository(
        private val saveResult: Result<Unit> = Result.success(Unit),
        initialRecentEntries: List<CityHistoryEntry> = emptyList(),
        private val recentResult: Result<List<CityHistoryEntry>>? = null,
    ) : CityHistoryRepository {

        val savedEntries: MutableList<CityHistoryEntry> = mutableListOf()

        private val recentEntries: MutableList<CityHistoryEntry> = initialRecentEntries.toMutableList()

        var recentCitiesCount: Int = 0
            private set

        var lastLimit: Int? = null
            private set

        override suspend fun saveCity(entry: CityHistoryEntry): Result<Unit> {
            savedEntries += entry
            if (saveResult.isSuccess) {
                recentEntries.add(0, entry)
            }
            return saveResult
        }

        override suspend fun recentCities(limit: Int): Result<List<CityHistoryEntry>> {
            recentCitiesCount += 1
            lastLimit = limit
            return recentResult ?: Result.success(recentEntries.take(limit))
        }
    }

    private class RecordingFavoriteCityRepository(
        initialEntries: List<FavoriteCityEntry> = emptyList(),
    ) : FavoriteCityRepository {

        val entries: MutableList<FavoriteCityEntry> = initialEntries.toMutableList()

        var favoriteCitiesCount: Int = 0
            private set

        override suspend fun addCity(entry: FavoriteCityEntry): Result<Unit> {
            entries.removeAll { it.latitude == entry.latitude && it.longitude == entry.longitude }
            entries += entry
            return Result.success(Unit)
        }

        override suspend fun removeCity(entry: FavoriteCityEntry): Result<Unit> {
            entries.removeAll { it.latitude == entry.latitude && it.longitude == entry.longitude }
            return Result.success(Unit)
        }

        override suspend fun favoriteCities(): Result<List<FavoriteCityEntry>> {
            favoriteCitiesCount += 1
            return Result.success(entries)
        }
    }

    private companion object {

        const val TEST_SELECTED_AT_EPOCH_MILLIS: Long = 1_723_000_000_000
    }
}
