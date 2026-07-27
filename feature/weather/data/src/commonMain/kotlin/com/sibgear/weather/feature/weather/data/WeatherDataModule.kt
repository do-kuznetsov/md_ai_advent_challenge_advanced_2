package com.sibgear.weather.feature.weather.data

import app.cash.sqldelight.db.SqlDriver
import com.sibgear.weather.core.location.CurrentLocationProvider
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository
import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.CityHistoryRepository
import com.sibgear.weather.feature.weather.domain.CitySearchRepository
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

    public fun provideCitySearchRepository(): CitySearchRepository =
        CitySearchRepositoryImpl(
            api = OpenMeteoGeocodingApi(createHttpClient()),
            mapper = OpenMeteoGeocodingDomainMapper(),
        )

    public fun provideCityHistoryRepository(driver: SqlDriver): CityHistoryRepository =
        CityHistoryRepositoryImpl(
            database = WeatherStorageDatabase(driver),
            mapper = CityHistoryEntryMapper(),
        )
}
