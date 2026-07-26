package com.sibgear.weather

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sibgear.weather.core.location.LocationCoreModule
import com.sibgear.weather.feature.reversegeocoding.data.ReverseGeocodingDataModule
import com.sibgear.weather.feature.weather.data.WeatherDataModule
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherEvent
import com.sibgear.weather.feature.weather.ui.WeatherScreenComponent

public class MainActivity : ComponentActivity() {

    private lateinit var component: WeatherScreenComponent

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        component.viewModel.onViewEventOccurred(
            WeatherEvent.LocationPermissionResult(
                granted = granted,
                permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component = WeatherScreenComponent(
            repository = WeatherDataModule.provide(
                currentLocationProvider = LocationCoreModule.provide(this),
                reverseGeocodingRepository = ReverseGeocodingDataModule.provide(this),
            ),
        )
        setContent {
            WeatherApp(component = component, onEffect = ::handleEffect)
        }
    }

    private fun handleEffect(effect: WeatherEffect) {
        when (effect) {
            WeatherEffect.RequestLocationPermission -> requestLocationPermission()
            WeatherEffect.OpenAppSettings -> openAppSettings()
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            component.viewModel.onViewEventOccurred(
                WeatherEvent.LocationPermissionResult(granted = true, permanentlyDenied = false),
            )
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
    }
}
