package com.sibgear.weather.core.location

import android.content.Context

public object LocationCoreModule {

    public fun provide(context: Context): CurrentLocationProvider =
        AndroidCurrentLocationProvider(context.applicationContext)
}
