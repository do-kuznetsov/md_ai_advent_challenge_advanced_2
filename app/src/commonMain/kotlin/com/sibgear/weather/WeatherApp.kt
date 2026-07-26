package com.sibgear.weather

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherScreen
import com.sibgear.weather.feature.weather.ui.WeatherScreenComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
private data object WeatherRoute : NavKey

private val navigationSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(WeatherRoute::class, WeatherRoute.serializer())
        }
    }
}

@Composable
public fun WeatherApp(
    component: WeatherScreenComponent,
    onEffect: (WeatherEffect) -> Unit,
) {
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, WeatherRoute)

    MaterialTheme {
        NavDisplay(
            backStack = backStack,
            entryProvider = entryProvider {
                entry<WeatherRoute> {
                    WeatherScreen(
                        viewModel = component.viewModel,
                        onEffect = onEffect,
                    )
                }
            },
        )
    }
}
