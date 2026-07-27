package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CityHistoryEntry
import com.sibgear.weather.feature.weather.domain.CurrentWeather
import kotlin.math.roundToInt

public class WeatherUiMapper {

    public fun map(source: CurrentWeather): WeatherUiModel =
        WeatherUiModel(
            cityName = source.cityName,
            temperature = "${source.temperatureCelsius.roundToInt()} C",
            conditionIcon = source.mapConditionIcon(),
            cloudCover = "${source.cloudCoverPercent} %",
            cloudCoverIcon = source.mapConditionIcon(),
            windSpeed = "${source.windSpeedKilometersPerHour.roundToInt()} км/ч",
            windSpeedIcon = WeatherIcon.Wind,
            precipitation = "${source.precipitationMillimeters} мм",
            precipitationIcon = WeatherIcon.Precipitation,
        )

    public fun map(source: CityHistoryEntry): CityHistoryUiModel =
        CityHistoryUiModel(
            name = source.name,
            country = source.country,
            displayName = source.displayName(),
            latitude = source.latitude,
            longitude = source.longitude,
        )

    private fun CurrentWeather.mapConditionIcon(): WeatherIcon =
        if (cloudCoverPercent < CLOUDY_CLOUD_COVER_PERCENT) {
            WeatherIcon.Sunny
        } else {
            WeatherIcon.Cloud
        }

    private fun CityHistoryEntry.displayName(): String =
        if (country.isNullOrBlank()) {
            name
        } else {
            "$name, $country"
        }

    private companion object {

        private const val CLOUDY_CLOUD_COVER_PERCENT: Int = 50
    }
}
