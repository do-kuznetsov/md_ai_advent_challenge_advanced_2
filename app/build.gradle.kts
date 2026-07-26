plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:location"))
            implementation(project(":core:mvvm"))
            implementation(project(":feature:reverse-geocoding:data"))
            implementation(project(":feature:reverse-geocoding:domain"))
            implementation(project(":feature:weather:data"))
            implementation(project(":feature:weather:domain"))
            implementation(project(":feature:weather:ui"))
            implementation(compose.material3)
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.navigation3.ui)
            implementation(libs.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.sibgear.weather"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sibgear.weather"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}
