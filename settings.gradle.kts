pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "weather-kmp"

include(":app")
include(":androidApp")
include(":core:location")
include(":core:mvvm")
include(":feature:reverse-geocoding:domain")
include(":feature:reverse-geocoding:data")
include(":feature:weather:domain")
include(":feature:weather:data")
include(":feature:weather:ui")
include(":ai:prompt-injection-lab")
include(":ai:quality-cli")
