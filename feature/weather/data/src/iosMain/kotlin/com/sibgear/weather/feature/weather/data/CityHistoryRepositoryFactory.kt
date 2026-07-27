package com.sibgear.weather.feature.weather.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.CityHistoryRepository

public fun WeatherDataModule.provideIosCityHistoryRepository(): CityHistoryRepository =
    provideCityHistoryRepository(
        driver = NativeSqliteDriver(
            schema = WeatherStorageDatabase.Schema,
            name = WEATHER_STORAGE_DATABASE_NAME,
        ),
    )
