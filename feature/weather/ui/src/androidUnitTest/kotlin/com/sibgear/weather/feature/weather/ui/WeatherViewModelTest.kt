package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherLocationUnavailableException
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.GetCurrentWeatherInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
public class WeatherViewModelTest {

    @Test
    public fun screenOpenedRequestsLocationPermission() =
        runWeatherViewModelTest { viewModel ->
            viewModel.onViewEventOccurred(WeatherEvent.ScreenOpened)
            runCurrent()

            assertEquals(WeatherEffect.RequestLocationPermission, viewModel.effects.first())
        }

    @Test
    public fun grantedPermissionShowsWeatherContent() =
        runWeatherViewModelTest(
            result = Result.success(weather),
        ) { viewModel ->
            viewModel.onViewEventOccurred(WeatherEvent.LocationPermissionResult(granted = true))
            runCurrent()

            assertEquals(
                WeatherState.Content(
                    WeatherUiModel(
                        cityName = "Новосибирск",
                        temperature = "20 C",
                        cloudCover = "35 %",
                        windSpeed = "12 км/ч",
                        precipitation = "0.0 мм",
                    ),
                ),
                viewModel.state.value,
            )
        }

    @Test
    public fun deniedPermissionShowsAccessRequiredState() =
        runWeatherViewModelTest { viewModel ->
            viewModel.onViewEventOccurred(WeatherEvent.LocationPermissionResult(granted = false))
            runCurrent()

            assertEquals(WeatherState.LocationAccessRequired, viewModel.state.value)
        }

    @Test
    public fun unavailableLocationShowsLocationError() =
        runWeatherViewModelTest(
            result = Result.failure(CurrentWeatherLocationUnavailableException()),
        ) { viewModel ->
            viewModel.onViewEventOccurred(WeatherEvent.LocationPermissionResult(granted = true))
            runCurrent()

            assertEquals(WeatherState.LocationUnavailable, viewModel.state.value)
        }

    @Test
    public fun weatherFailureShowsWeatherErrorAndRetryRequestsPermission() =
        runWeatherViewModelTest(
            result = Result.failure(IllegalStateException()),
        ) { viewModel ->
            viewModel.onViewEventOccurred(WeatherEvent.LocationPermissionResult(granted = true))
            runCurrent()

            assertEquals(WeatherState.WeatherUnavailable, viewModel.state.value)

            viewModel.onViewEventOccurred(WeatherEvent.RetryClicked)
            runCurrent()

            assertEquals(WeatherEffect.RequestLocationPermission, viewModel.effects.first())
        }

    @Test
    public fun settingsActionEmitsOpenAppSettingsEffect() =
        runWeatherViewModelTest { viewModel ->
            viewModel.onViewEventOccurred(WeatherEvent.SettingsClicked)
            runCurrent()

            assertEquals(WeatherEffect.OpenAppSettings, viewModel.effects.first())
        }

    private fun runWeatherViewModelTest(
        result: Result<CurrentWeather> = Result.success(weather),
        block: suspend kotlinx.coroutines.test.TestScope.(WeatherViewModel) -> Unit,
    ): Unit =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel =
                    WeatherViewModel(
                        getCurrentWeather = GetCurrentWeatherInteractor(FakeCurrentWeatherRepository(result)),
                        mapper = WeatherUiMapper(),
                    )
                runCurrent()
                block(viewModel)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class FakeCurrentWeatherRepository(
        private val result: Result<CurrentWeather>,
    ) : CurrentWeatherRepository {

        override suspend fun loadCurrentWeather(): Result<CurrentWeather> = result
    }

    private companion object {

        val weather: CurrentWeather =
            CurrentWeather(
                cityName = "Новосибирск",
                temperatureCelsius = 19.6,
                cloudCoverPercent = 35,
                windSpeedKilometersPerHour = 11.7,
                precipitationMillimeters = 0.0,
            )
    }
}
