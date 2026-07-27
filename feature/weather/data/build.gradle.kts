plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "com.sibgear.weather.feature.weather.data"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:location"))
            implementation(project(":feature:reverse-geocoding:domain"))
            implementation(project(":feature:weather:domain"))
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("WeatherStorageDatabase") {
            packageName.set("com.sibgear.weather.feature.weather.data.storage")
        }
    }
}
