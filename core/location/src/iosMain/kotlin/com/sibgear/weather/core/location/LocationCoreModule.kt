package com.sibgear.weather.core.location

public object LocationCoreModule {

    public fun provide(): CurrentLocationProvider = IosCurrentLocationProvider()
}
