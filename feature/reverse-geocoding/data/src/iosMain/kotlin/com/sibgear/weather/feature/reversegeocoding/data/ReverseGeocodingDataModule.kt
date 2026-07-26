package com.sibgear.weather.feature.reversegeocoding.data

import com.sibgear.weather.feature.reversegeocoding.domain.ReverseGeocodingRepository

public object ReverseGeocodingDataModule {

    public fun provide(): ReverseGeocodingRepository = IosReverseGeocodingRepository()
}
