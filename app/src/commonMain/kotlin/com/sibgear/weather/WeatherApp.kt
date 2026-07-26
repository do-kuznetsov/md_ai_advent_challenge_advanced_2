package com.sibgear.weather

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherScreen
import com.sibgear.weather.feature.weather.ui.WeatherViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
private data object WeatherRoute : NavKey

@Composable
public fun WeatherApp(
    viewModel: WeatherViewModel,
    onEffect: (WeatherEffect) -> Unit,
) {
    val backStack =
        rememberNavBackStack(
            SavedStateConfiguration {
                serializersModule =
                    SerializersModule {
                        polymorphic(NavKey::class) {
                            subclass(WeatherRoute::class, WeatherRoute.serializer())
                        }
                    }
            },
            WeatherRoute,
        )

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<WeatherRoute> {
                    WeatherScreen(viewModel = viewModel, onEffect = onEffect)
                }
            },
    )
}
