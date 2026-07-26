package com.sibgear.weather.core.location

public data class LocationCoordinates(
    public val latitude: Double,
    public val longitude: Double,
)

public interface CurrentLocationProvider {
    public suspend fun currentLocation(): Result<LocationCoordinates>
}

public class LocationUnavailableException : IllegalStateException("Current location is unavailable")
