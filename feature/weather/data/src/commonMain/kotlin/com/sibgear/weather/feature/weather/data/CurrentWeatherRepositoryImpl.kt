package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import com.sibgear.weather.feature.weather.domain.CurrentWeatherLocationUnavailableException
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository

internal class CurrentWeatherRepositoryImpl(
    private val currentLocationProvider: CurrentLocationProvider,
    private val resolveCityName: ResolveCityNameInteractor,
    private val api: OpenMeteoApi,
    private val mapper: WeatherDataDomainMapper,
) : CurrentWeatherRepository {

    override suspend fun loadCurrentWeather(): Result<CurrentWeather> {
        val coordinates =
            currentLocationProvider.currentLocation().getOrElse {
                return Result.failure(CurrentWeatherLocationUnavailableException())
            }

        return runCatching {
            val cityName = resolveCityName(coordinates.latitude, coordinates.longitude)?.value ?: CURRENT_LOCATION_NAME
            mapper.map(
                source = api.getCurrentForecast(coordinates.latitude, coordinates.longitude),
                cityName = cityName,
            )
        }
    }

    private companion object {

        const val CURRENT_LOCATION_NAME: String = "Текущее местоположение"
    }
}
