package com.sibgear.weather.feature.weather.domain

public sealed interface SelectedWeatherLocation {

    public val latitude: Double
    public val longitude: Double

    public data class City(
        public val name: String,
        public override val latitude: Double,
        public override val longitude: Double,
    ) : SelectedWeatherLocation

    public data class Coordinates(
        public override val latitude: Double,
        public override val longitude: Double,
    ) : SelectedWeatherLocation
}
