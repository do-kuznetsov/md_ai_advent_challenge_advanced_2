import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<KtlintExtension> {
        additionalEditorconfig.put("ktlint_function_naming_ignore_when_annotated_with", "Composable")
        filter {
            exclude("**/build/generated/**")
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
    }
}
