plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    android {
        namespace = "com.sibgear.weather.feature.weather.domain"
        compileSdk = 37
        minSdk = 26
    }
    iosArm64()
    iosSimulatorArm64()
}
