package com.sibgear.weather.feature.reversegeocoding.data

import android.content.Context
import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository

public object ReverseGeocodingDataModule {

    public fun provide(context: Context): ReverseGeocodingRepository =
        AndroidReverseGeocodingRepository(context.applicationContext)
}
