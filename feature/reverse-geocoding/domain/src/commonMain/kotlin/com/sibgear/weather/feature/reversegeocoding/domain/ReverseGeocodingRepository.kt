package com.sibgear.weather.feature.reversegeocoding.domain

public interface ReverseGeocodingRepository {

    public suspend fun resolveCityName(latitude: Double, longitude: Double): Result<CityName?>
}
