package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherLocationUnavailableException
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository
import com.sibgear.weather.feature.weather.domain.SelectedWeatherLocation

internal class CurrentWeatherRepositoryImpl(
    private val currentLocationProvider: CurrentLocationProvider,
    private val resolveCityName: ResolveCityNameInteractor,
    private val api: OpenMeteoApi,
    private val mapper: WeatherDataDomainMapper,
) : CurrentWeatherRepository {

    override suspend fun loadCurrentWeather(): Result<CurrentWeather> {
        val coordinates = currentLocationProvider.currentLocation().getOrElse {
            return Result.failure(CurrentWeatherLocationUnavailableException())
        }

        return loadWeather(
            location = SelectedWeatherLocation.Coordinates(
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
            ),
        )
    }

    override suspend fun loadWeather(location: SelectedWeatherLocation): Result<CurrentWeather> =
        runCatching {
            val cityName = when (location) {
                is SelectedWeatherLocation.City -> location.name
                is SelectedWeatherLocation.Coordinates -> resolveCityName(location.latitude, location.longitude)
                    .getOrNull()
                    ?.value
                    ?: CURRENT_LOCATION_NAME
            }
            mapper.map(
                source = api.getCurrentForecast(location.latitude, location.longitude),
                cityName = cityName,
            )
        }

    private companion object {

        private const val CURRENT_LOCATION_NAME: String = "Текущее местоположение"
    }
}
