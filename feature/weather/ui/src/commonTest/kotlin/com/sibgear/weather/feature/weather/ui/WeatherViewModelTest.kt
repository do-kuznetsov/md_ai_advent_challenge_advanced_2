package com.sibgear.weather.feature.weather.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor

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
            assertEquals(WeatherState.LoadingLocation, viewModel.state.value)
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

    private fun createViewModel(
        weatherResult: Result<CurrentWeather> = Result.success(createWeather()),
    ): WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(
                repository = object : CurrentWeatherRepository {
                    override suspend fun loadCurrentWeather(): Result<CurrentWeather> =
                        weatherResult
                },
            ),
            mapper = WeatherUiMapper(),
        )

    private fun createWeather(): CurrentWeather =
        CurrentWeather(
            cityName = "Новосибирск",
            temperatureCelsius = 18.4,
            cloudCoverPercent = 62,
            windSpeedKilometersPerHour = 12.6,
            precipitationMillimeters = 0.4,
        )
}
