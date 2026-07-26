package com.sibgear.weather.feature.reversegeocoding.domain

public class ResolveCityNameInteractor(
    private val repository: ReverseGeocodingRepository,
) {

    public suspend operator fun invoke(latitude: Double, longitude: Double): Result<CityName?> =
        repository.resolveCityName(latitude, longitude)
}
