import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
    }
}
