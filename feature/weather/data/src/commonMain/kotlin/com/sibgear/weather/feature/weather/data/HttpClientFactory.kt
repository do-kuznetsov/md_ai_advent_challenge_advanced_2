package com.sibgear.weather.feature.weather.data

import io.ktor.client.HttpClient

internal expect fun createHttpClient(): HttpClient
