package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import com.sibgear.weather.feature.weather.domain.CurrentWeatherRepository

public object WeatherDataModule {

    public fun provide(
        currentLocationProvider: CurrentLocationProvider,
        reverseGeocodingRepository: ReverseGeocodingRepository,
    ): CurrentWeatherRepository =
        CurrentWeatherRepositoryImpl(
            currentLocationProvider = currentLocationProvider,
            resolveCityName = ResolveCityNameInteractor(reverseGeocodingRepository),
            api = OpenMeteoApi(createHttpClient()),
            mapper = WeatherDataDomainMapper(),
        )
}
