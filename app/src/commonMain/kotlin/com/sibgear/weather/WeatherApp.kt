package com.sibgear.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherRoute
import com.sibgear.weather.feature.weather.ui.WeatherScreen
import com.sibgear.weather.feature.weather.ui.WeatherScreenComponent
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val weatherAppRoutes: List<WeatherRoute> = listOf(WeatherRoute.Map, WeatherRoute.List)

private val navigationSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(WeatherRoute.List::class, WeatherRoute.List.serializer())
            subclass(WeatherRoute.Map::class, WeatherRoute.Map.serializer())
        }
    }
}

@Composable
public fun WeatherApp(
    component: WeatherScreenComponent,
    onEffect: (WeatherEffect) -> Unit,
) {
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, WeatherRoute.List)
    val selectedRoute = backStack.lastOrNull() as? WeatherRoute ?: WeatherRoute.List

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = weatherAppRoutes.indexOf(selectedRoute)) {
                weatherAppRoutes.forEach { route ->
                    Tab(
                        selected = route == selectedRoute,
                        onClick = {
                            backStack.clear()
                            backStack.add(route)
                        },
                        text = { Text(route.title) },
                    )
                }
            }

            NavDisplay(
                backStack = backStack,
                modifier = Modifier.weight(1f),
                entryProvider = entryProvider {
                    entry<WeatherRoute.Map> {
                        MapPlaceholder(modifier = Modifier.fillMaxSize())
                    }
                    entry<WeatherRoute.List> {
                        WeatherScreen(
                            viewModel = component.viewModel,
                            onEffect = onEffect,
                        )
                    }
                },
            )
        }
    }
}

private val WeatherRoute.title: String
    get() = when (this) {
        WeatherRoute.Map -> "Карта"
        WeatherRoute.List -> "Список"
    }

@Composable
private fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text("Карта")
    }
}
