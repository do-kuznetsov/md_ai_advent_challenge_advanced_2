package com.sibgear.weather.feature.weather.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sibgear.weather.feature.weather.data.storage.WeatherStorageDatabase
import com.sibgear.weather.feature.weather.domain.FavoriteCityRepository

public fun WeatherDataModule.provideAndroidFavoriteCityRepository(context: Context): FavoriteCityRepository =
    provideFavoriteCityRepository(
        driver = AndroidSqliteDriver(
            schema = WeatherStorageDatabase.Schema,
            context = context,
            name = WEATHER_STORAGE_DATABASE_NAME,
        ),
    )
