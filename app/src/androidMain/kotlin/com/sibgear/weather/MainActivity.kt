package com.sibgear.weather

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import com.sibgear.weather.core.location.LocationCoreModule
import com.sibgear.weather.feature.reversegeocoding.data.ReverseGeocodingDataModule
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
import com.sibgear.weather.feature.weather.data.WeatherDataModule
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherEvent
import com.sibgear.weather.feature.weather.ui.WeatherScreenComponent

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val component =
                remember {
                    val locationProvider = LocationCoreModule.provide(applicationContext)
                    val reverseGeocodingRepository = ReverseGeocodingDataModule.provide(applicationContext)
                    val weatherRepository =
                        WeatherDataModule.provide(
                            currentLocationProvider = locationProvider,
                            resolveCityName = ResolveCityNameInteractor(reverseGeocodingRepository),
                        )
                    WeatherScreenComponent(weatherRepository)
                }
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    component.viewModel.onViewEventOccurred(WeatherEvent.LocationPermissionResult(granted))
                }

            MaterialTheme {
                WeatherApp(viewModel = component.viewModel) { effect ->
                    when (effect) {
                        WeatherEffect.RequestLocationPermission -> {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }

                        WeatherEffect.OpenAppSettings -> openApplicationSettings()
                    }
                }
            }
        }
    }

    private fun openApplicationSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
    }
}
