package com.sibgear.weather.feature.weather.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenMeteoGeocodingResponse(
    @SerialName("results") val candidates: List<OpenMeteoGeocodingCandidateDto> = emptyList(),
)

@Serializable
internal data class OpenMeteoGeocodingCandidateDto(
    @SerialName("name") val name: String,
    @SerialName("country") val country: String? = null,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
)
