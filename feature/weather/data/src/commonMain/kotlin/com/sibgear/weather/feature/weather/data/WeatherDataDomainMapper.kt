package com.sibgear.weather.feature.weather.data

import com.sibgear.weather.feature.weather.domain.CurrentWeather

internal class WeatherDataDomainMapper {

    public fun map(source: OpenMeteoResponse, cityName: String): CurrentWeather =
        CurrentWeather(
            cityName = cityName,
            temperatureCelsius = source.current.temperatureCelsius,
            cloudCoverPercent = source.current.cloudCoverPercent,
            windSpeedKilometersPerHour = source.current.windSpeedKilometersPerHour,
            precipitationMillimeters = source.current.precipitationMillimeters,
        )
}
