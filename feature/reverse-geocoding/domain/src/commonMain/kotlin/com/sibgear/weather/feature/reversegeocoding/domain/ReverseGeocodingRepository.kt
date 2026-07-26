package com.sibgear.weather.feature.reversegeocoding.domain

public data class CityName(
    public val value: String,
)

public interface ReverseGeocodingRepository {
    public suspend fun resolveCityName(
        latitude: Double,
        longitude: Double,
    ): CityName?
}

public class ResolveCityNameInteractor(
    private val repository: ReverseGeocodingRepository,
) {
    public suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
    ): CityName? = repository.resolveCityName(latitude, longitude)
}
