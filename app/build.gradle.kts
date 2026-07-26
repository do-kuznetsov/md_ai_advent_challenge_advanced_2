plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.sibgear.weather"
        compileSdk = 37
        minSdk = 26
    }
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "WeatherShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:location"))
            implementation(project(":core:mvvm"))
            implementation(project(":feature:reverse-geocoding:data"))
            implementation(project(":feature:reverse-geocoding:domain"))
            implementation(project(":feature:weather:data"))
            implementation(project(":feature:weather:domain"))
            implementation(project(":feature:weather:ui"))
            implementation(compose.material)
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.androidx.savedstate)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
