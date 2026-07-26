plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            api(libs.lifecycle.viewmodel)
        }
    }
}

android {
    namespace = "com.sibgear.weather.core.mvvm"
    compileSdk = 37
    defaultConfig.minSdk = 26
}
