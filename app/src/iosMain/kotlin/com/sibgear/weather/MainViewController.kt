package com.sibgear.weather

import androidx.compose.ui.window.ComposeUIViewController
import com.sibgear.weather.core.location.LocationCoreModule
import com.sibgear.weather.feature.reversegeocoding.data.ReverseGeocodingDataModule
import com.sibgear.weather.feature.weather.data.WeatherDataModule
import com.sibgear.weather.feature.weather.ui.WeatherEffect
import com.sibgear.weather.feature.weather.ui.WeatherEvent
import com.sibgear.weather.feature.weather.ui.WeatherScreenComponent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
public fun MainViewController(): UIViewController {
    val component = WeatherScreenComponent(
        weatherRepository = WeatherDataModule.provide(
            currentLocationProvider = LocationCoreModule.provide(),
            reverseGeocodingRepository = ReverseGeocodingDataModule.provide(),
        ),
        citySearchRepository = WeatherDataModule.provideCitySearchRepository(),
    )
    val permissionHandler = IosWeatherPermissionHandler(component)

    return ComposeUIViewController {
        WeatherApp(component = component, onEffect = permissionHandler::handle)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosWeatherPermissionHandler(
    private val component: WeatherScreenComponent,
) : NSObject(), CLLocationManagerDelegateProtocol {

    private val locationManager: CLLocationManager = CLLocationManager()

    init {
        locationManager.delegate = this
    }

    fun handle(effect: WeatherEffect) {
        when (effect) {
            WeatherEffect.RequestLocationPermission -> requestLocationPermission()
            WeatherEffect.OpenAppSettings -> openAppSettings()
        }
    }

    override fun locationManager(
        manager: CLLocationManager,
        didChangeAuthorizationStatus: CLAuthorizationStatus,
    ) {
        val granted = didChangeAuthorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
            didChangeAuthorizationStatus == kCLAuthorizationStatusAuthorizedAlways
        component.viewModel.onViewEventOccurred(
            WeatherEvent.LocationPermissionResult(
                granted = granted,
                permanentlyDenied = didChangeAuthorizationStatus == kCLAuthorizationStatusDenied,
            ),
        )
    }

    private fun requestLocationPermission() {
        val status = locationManager.authorizationStatus
        if (status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways) {
            component.viewModel.onViewEventOccurred(
                WeatherEvent.LocationPermissionResult(granted = true, permanentlyDenied = false),
            )
        } else {
            locationManager.requestWhenInUseAuthorization()
        }
    }

    private fun openAppSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url)
        }
    }
}
