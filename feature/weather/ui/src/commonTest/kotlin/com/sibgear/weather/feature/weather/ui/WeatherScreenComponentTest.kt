package com.sibgear.weather.feature.weather.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.sibgear.weather.feature.weather.domain.CityHistoryEntry
import com.sibgear.weather.feature.weather.domain.CityHistoryRepository
import com.sibgear.weather.feature.weather.domain.CitySearchRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.WeatherCityCandidate

public class WeatherScreenComponentTest {

    @Test
    public fun createsWeatherViewModel(): Unit {
        val component = WeatherScreenComponent(
            weatherRepository = object : CurrentWeatherRepository {
                override suspend fun loadCurrentWeather(): Result<CurrentWeather> =
                    Result.success(
                        CurrentWeather(
                            cityName = "Новосибирск",
                            temperatureCelsius = 18.4,
                            cloudCoverPercent = 62,
                            windSpeedKilometersPerHour = 12.6,
                            precipitationMillimeters = 0.4,
                        ),
                    )
            },
            citySearchRepository = object : CitySearchRepository {
                override suspend fun searchCities(query: String): Result<List<WeatherCityCandidate>> =
                    Result.success(emptyList())
            },
            cityHistoryRepository = object : CityHistoryRepository {
                override suspend fun saveCity(entry: CityHistoryEntry): Result<Unit> =
                    Result.success(Unit)

                override suspend fun recentCities(limit: Int): Result<List<CityHistoryEntry>> =
                    Result.success(emptyList())
            },
            currentTimeMillis = { 1_723_000_000_000 },
        )

        assertIs<WeatherViewModel>(component.viewModel)
        assertEquals(WeatherState.LoadingLocation(), component.viewModel.state.value)
    }
}
