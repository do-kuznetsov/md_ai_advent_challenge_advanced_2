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
import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
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
    ): WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(
                repository = repository,
            ),
            searchCities = SearchCitiesInteractor(
                repository = citySearchRepository,
            ),
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
}
