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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherScreen
import com.sibgear.weather.feature.weather.ui.WeatherScreenComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
private data object WeatherRoute : NavKey

private enum class WeatherAppTab(
    val title: String,
) {
    Map(title = "Карта"),
    List(title = "Список"),
}

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
    var selectedTab by remember { mutableStateOf(WeatherAppTab.List) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = WeatherAppTab.entries.indexOf(selectedTab)) {
                WeatherAppTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    WeatherAppTab.Map -> MapPlaceholder(modifier = Modifier.fillMaxSize())
                    WeatherAppTab.List -> NavDisplay(
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
        }
    }
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
