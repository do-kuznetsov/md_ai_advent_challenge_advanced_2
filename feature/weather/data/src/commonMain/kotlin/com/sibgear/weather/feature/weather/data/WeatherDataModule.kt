package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository

public object WeatherDataModule {

    public fun provide(
        currentLocationProvider: CurrentLocationProvider,
        resolveCityName: ResolveCityNameInteractor,
    ): CurrentWeatherRepository =
        CurrentWeatherRepositoryImpl(
            currentLocationProvider = currentLocationProvider,
            resolveCityName = resolveCityName,
            api = OpenMeteoApi(createHttpClient()),
            mapper = WeatherDataDomainMapper(),
        )
}

internal expect fun createHttpClient(): io.ktor.client.HttpClient
