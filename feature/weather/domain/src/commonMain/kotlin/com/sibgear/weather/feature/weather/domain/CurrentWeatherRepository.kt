package com.sibgear.weather.feature.weather.domain

public data class CurrentWeather(
    public val cityName: String,
    public val temperatureCelsius: Double,
    public val cloudCoverPercent: Int,
    public val windSpeedKilometersPerHour: Double,
    public val precipitationMillimeters: Double,
)

public interface CurrentWeatherRepository {
    public suspend fun loadCurrentWeather(): Result<CurrentWeather>
}

public class CurrentWeatherLocationUnavailableException : IllegalStateException("Current weather location is unavailable")

public class GetCurrentWeatherInteractor(
    private val repository: CurrentWeatherRepository,
) {
    public suspend operator fun invoke(): Result<CurrentWeather> = repository.loadCurrentWeather()
}
