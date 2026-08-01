plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.sqldelight) apply false
}

dependencies {
    kover(project(":feature:reverse-geocoding:domain"))
    kover(project(":feature:weather:data"))
    kover(project(":feature:weather:domain"))
    kover(project(":feature:weather:ui"))
}
