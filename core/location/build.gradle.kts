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
        }
        androidMain.dependencies {
            implementation(libs.androidx.core)
        }
    }
}

android {
    namespace = "com.sibgear.weather.core.location"
    compileSdk = 37
    defaultConfig.minSdk = 26
}
