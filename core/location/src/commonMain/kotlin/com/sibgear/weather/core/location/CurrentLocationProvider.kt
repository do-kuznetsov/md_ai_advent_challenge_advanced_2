package com.sibgear.weather.core.location

public interface CurrentLocationProvider {

    public suspend fun currentLocation(): Result<Coordinates>
}
