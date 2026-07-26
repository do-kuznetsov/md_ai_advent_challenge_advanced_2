pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "weather-mvp"

include(":app")
include(":core:mvvm")
include(":core:location")
include(":feature:reverse-geocoding:domain")
include(":feature:reverse-geocoding:data")
include(":feature:weather:domain")
include(":feature:weather:data")
include(":feature:weather:ui")
