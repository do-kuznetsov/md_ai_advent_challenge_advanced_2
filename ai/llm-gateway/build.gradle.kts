import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec

plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm {
        binaries {
            executable {
                mainClass.set("com.sibgear.weather.ai.gateway.MainKt")
            }
        }
    }
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "llm-gateway-ui.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.slf4j.nop)
            implementation(libs.sqldelight.sqlite.driver)
        }
        jsMain.dependencies {
            implementation(libs.jetbrains.compose.html.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.server.test.host)
        }
    }
}

sqldelight {
    databases {
        create("GatewayDatabase") {
            packageName.set("com.sibgear.weather.ai.gateway.storage")
        }
    }
}

detekt {
    config.setFrom("detekt.yml")
    source.setFrom(
        "src/commonMain/kotlin",
        "src/jvmMain/kotlin",
        "src/jsMain/kotlin",
        "src/jvmTest/kotlin",
        "src/jsTest/kotlin",
    )
}

tasks.named<Copy>("jvmProcessResources") {
    dependsOn("jsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/js/productionExecutable")) {
        into("static")
    }
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}

extensions.configure<NodeJsEnvSpec> {
    download.set(false)
    command.set("node")
}
