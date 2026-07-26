package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CurrentWeather
import kotlin.math.roundToInt

public class WeatherUiMapper {

    public fun map(source: CurrentWeather): WeatherUiModel =
        WeatherUiModel(
            cityName = source.cityName,
            temperature = "${source.temperatureCelsius.roundToInt()} C",
            cloudCover = "${source.cloudCoverPercent} %",
            windSpeed = "${source.windSpeedKilometersPerHour.roundToInt()} км/ч",
            precipitation = "${source.precipitationMillimeters} мм",
        )
}
