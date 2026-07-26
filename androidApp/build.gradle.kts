plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.sibgear.weather.androidapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sibgear.weather"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":app"))
}
