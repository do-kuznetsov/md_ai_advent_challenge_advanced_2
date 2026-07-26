package com.sibgear.weather

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.sibgear.weather.core.location.LocationCoreModule
import com.sibgear.weather.feature.reversegeocoding.data.ReverseGeocodingDataModule
import com.sibgear.weather.feature.reversegeocoding.domain.ResolveCityNameInteractor
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
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSURL
import platform.Foundation.NSURL.Companion.URLWithString
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Suppress("ktlint:standard:function-naming")
public fun MainViewController(): UIViewController {
    val locationProvider = LocationCoreModule.provide()
    val reverseGeocodingRepository = ReverseGeocodingDataModule.provide()
    val weatherRepository =
        WeatherDataModule.provide(
            currentLocationProvider = locationProvider,
            resolveCityName = ResolveCityNameInteractor(reverseGeocodingRepository),
        )
    val component = WeatherScreenComponent(weatherRepository)

    return ComposeUIViewController {
        val permissionHandler =
            remember {
                IosLocationPermissionHandler { granted ->
                    component.viewModel.onViewEventOccurred(WeatherEvent.LocationPermissionResult(granted))
                }
            }

        MaterialTheme {
            WeatherApp(viewModel = component.viewModel) { effect ->
                when (effect) {
                    WeatherEffect.RequestLocationPermission -> permissionHandler.request()
                    WeatherEffect.OpenAppSettings -> permissionHandler.openSettings()
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosLocationPermissionHandler(
    private val onPermissionResult: (Boolean) -> Unit,
) : NSObject(),
    CLLocationManagerDelegateProtocol {
    private val locationManager: CLLocationManager = CLLocationManager()

    init {
        locationManager.delegate = this
    }

    fun request() {
        when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse,
            -> onPermissionResult(true)

            kCLAuthorizationStatusNotDetermined -> locationManager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusDenied -> onPermissionResult(false)
            else -> onPermissionResult(false)
        }
    }

    override fun locationManager(
        manager: CLLocationManager,
        didChangeAuthorizationStatus: CLAuthorizationStatus,
    ) {
        onPermissionResult(
            didChangeAuthorizationStatus == kCLAuthorizationStatusAuthorizedAlways ||
                didChangeAuthorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse,
        )
    }

    fun openSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(settingsUrl)
    }
}
