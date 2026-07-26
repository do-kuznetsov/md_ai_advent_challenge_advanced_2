# Weather KMP Project Rules

## Scope And Configuration

This is an active production-like Kotlin Multiplatform weather application scaffold. Targets: Android and iOS. UI: Compose Multiplatform.

Follow instructions in this order:

1. Global `AGENTS.md`.
2. This root `AGENTS.md`.
3. Nearest `AGENTS.md` inside the edited directory, if present.

Lower-level rules add detail; they never override higher-level rules. Before editing, inspect applicable rules and current staged and unstaged Git changes.

Use `ast-index` first for Kotlin symbols, implementations, usages, module maps, and project conventions. Use `rg` for literal strings, regular expressions, and Gradle or Markdown configuration. Apply a skill only when its trigger matches the task, and read its `SKILL.md` first.

Run `./gradlew` and `ast-index` without asking when their required access is already granted by the current sandbox. Build artifacts inside this repository are allowed. Request approval only when the command requires ungranted network access, filesystem access outside configured paths, or destructive operations.

Delegate only independent multi-module architecture research or a final diff review. Do not delegate a local change or a simple one-file edit. Do not add a project-specific skill: `ast-index` covers the reusable code-navigation workflow, and Gradle verification has no stable custom procedure yet.

The root file is the only local project contract for now. Do not create path-local `AGENTS.md` files while the eight modules share these rules. Revisit `app/AGENTS.md` when several feature routes exist, and `core/location/AGENTS.md` when permission or location workflows become independently complex.

## Bug Fix Profile

Use `ai/profile/bug-fix.md` for an explicit bug report about this repository when the user asks to fix it. Triggers are a message beginning with `/bugfix` or `Bugfix:`, or a clear description of current behavior, expected behavior, and intent to correct a defect. Do not activate it for a research question, code review, feature request, or an external-service outage with no evidence of a project defect.

The profile is an explicit user-approved exception to the normal delegation rule: orchestrate one fresh subagent at a time for `plan`, `execute`, `validate`, and `done`. Follow the profile's Git isolation, TDD, verification, retry, and reporting rules exactly.

## Research Profile

Use [ai/profile/research.md](ai/profile/research.md) for a question about this repository's code, structure, behavior, dependencies, symbols, data flow, or architecture. Explicit triggers are `/research <question>` and `Research: <question>`; they take priority over automatic selection. Otherwise, activate the profile when the user asks to investigate or explain the current codebase without requesting a change.

Do not activate Research for a bug report that requests a fix, a feature request, a code review, or an external-service outage without evidence of a project defect. Use `Bug Fix` when its trigger matches. Research is standalone: it investigates and answers in chat, but does not modify the checkout, create a report file, or run subagents.

## Stack

- Kotlin Multiplatform, Compose Multiplatform, Android, iOS.
- Ktor and `kotlinx.serialization` for HTTP and JSON.
- Open-Meteo Forecast API for current weather. It is used in the non-commercial educational tier: no API key, required visible attribution, no commercial use.
- Native reverse geocoding: Android `Geocoder`, iOS `CLGeocoder`.
- Navigation 3 for Compose Multiplatform.
- Gradle Kotlin DSL and `gradle/libs.versions.toml`. Dependency and plugin versions exist only in the version catalog.
- `detekt` and relevant unit or UI tests for production changes.

Do not add cache, persistent storage, favorites, settings, a map, search, or another weather provider without an explicit task.

## Modules And Dependencies

Current layout:

```text
androidApp/ (Android APK host; depends only on app)
app/
core/
  location/
  mvvm/
feature/
  reverse-geocoding/
    domain/
    data/
  weather/
    domain/
    data/
    ui/
```

Allowed dependencies:

```text
app -> core:location, core:mvvm, feature:reverse-geocoding:{domain,data}, feature:weather:{domain,data,ui}
androidApp -> app
feature:reverse-geocoding:data -> feature:reverse-geocoding:domain
feature:weather:data -> core:location, feature:reverse-geocoding:domain, feature:weather:domain
feature:weather:ui -> core:mvvm, feature:weather:domain
```

`app` owns application composition: manual DI graph, Navigation 3 back stack, and feature route serializers. `ui` never depends on `data`; a feature never depends on another feature's `data` or `ui`; no unlisted edge is allowed.

Each `data` module exposes one manual DI module. Keep API clients, DTOs, repository implementations, and data-to-domain mappers `internal`. `app` assembles data modules and UI construction entrypoints. Add a UI component only when it provides a real construction or lifecycle boundary.

Gradle dependency declarations:

- `feature:*` modules use `implementation` only; `api` is prohibited.
- `core:*` and `app` may use `api` only when a public API intentionally exposes that dependency. `core:mvvm` exposing `lifecycle.viewmodel` through public `BaseViewModel : ViewModel` is a valid example.

## UI And Navigation

Each screen uses UDF through `BaseViewModel<State, Event, Effect>`:

- `state` is immutable `StateFlow<State>`.
- `State`, `Event`, and `Effect` are sealed interfaces.
- `onViewEventOccurred` is the only UI entrypoint for events.
- `SideEffect` is for one-shot commands, never persistent screen state.
- Start loading from an explicit screen event. Do not make a network request from `ViewModel.init`.

Navigation 3 target architecture:

- Each feature owns an `@Serializable sealed interface` extending `NavKey`.
- `app` owns a user-managed back stack and aggregates feature serializers.
- Android and iOS use `SavedStateConfiguration` with a `SerializersModule`; do not rely on JVM reflection serialization.
- Route arguments are stable value types. Do not pass ViewModels, repositories, DTOs, or Compose state through routes.

The private `WeatherRoute` in `app` is a temporary MVP implementation. Do not copy that ownership model into new or changed features; introduce a feature-owned `NavKey` when extending weather navigation.

## Naming And Source Layout

Root package: `com.sibgear.weather`.

- Contracts: `CurrentWeatherRepository`, `GetCurrentWeatherInteractor`.
- Implementations: `CurrentWeatherRepositoryImpl`, `WeatherDataModule`.
- UI: `WeatherViewModel`, `WeatherState`, `WeatherEvent`, `WeatherEffect`.
- Mappers: `WeatherDataDomainMapper`, `WeatherUiMapper`.
- Public types use explicit `public`; implementation details use explicit `internal`.
- Keep one independent public contract or type per file; the file name matches its primary type. A sealed subtype or a closely coupled UI model may be co-located.
- `feature/weather/domain/.../CurrentWeatherRepository.kt` currently contains several independent public types. Treat it as technical debt, not as a template; do not add another independent type there.
- Prefer immutable `data class`, `data object`, `val`, expression bodies for simple forwarding methods, and named constructor arguments when an object has several dependencies.
- In every non-empty `class`, `interface`, `object`, `sealed interface`, and `companion object` body, leave one blank line after `{` before the first declaration.

Typical file shape:

```kotlin
package com.sibgear.weather.feature.weather.ui

import com.sibgear.weather.feature.weather.domain.CurrentWeather
import kotlin.math.roundToInt

public class WeatherUiMapper {

    public fun map(source: CurrentWeather): WeatherUiModel =
        WeatherUiModel(
            cityName = source.cityName,
            temperature = "${source.temperatureCelsius.roundToInt()} C",
        )
}
```

Order imports: Kotlin and AndroidX, third-party libraries, then project imports. Keep a single primary constructor and no wildcard imports.

## Normative Examples

The examples below are real patterns from this repository. Update them together with approved architectural changes.

### 1. Domain Contract And Interactor

```kotlin
public interface CurrentWeatherRepository {

    public suspend fun loadCurrentWeather(): Result<CurrentWeather>
}

public class GetCurrentWeatherInteractor(
    private val repository: CurrentWeatherRepository,
) {

    public suspend operator fun invoke(): Result<CurrentWeather> = repository.loadCurrentWeather()
}
```

### 2. Repository Implementation

```kotlin
internal class CurrentWeatherRepositoryImpl(
    private val currentLocationProvider: CurrentLocationProvider,
    private val resolveCityName: ResolveCityNameInteractor,
    private val api: OpenMeteoApi,
    private val mapper: WeatherDataDomainMapper,
) : CurrentWeatherRepository {

    override suspend fun loadCurrentWeather(): Result<CurrentWeather> {
        val coordinates = currentLocationProvider.currentLocation().getOrElse {
            return Result.failure(CurrentWeatherLocationUnavailableException())
        }

        return runCatching {
            val cityName = resolveCityName(coordinates.latitude, coordinates.longitude)?.value
                ?: CURRENT_LOCATION_NAME
            mapper.map(
                source = api.getCurrentForecast(coordinates.latitude, coordinates.longitude),
                cityName = cityName,
            )
        }
    }
}
```

### 3. UDF ViewModel

```kotlin
public class WeatherViewModel(
    private val getCurrentWeather: GetCurrentWeatherInteractor,
    private val mapper: WeatherUiMapper,
) : BaseViewModel<WeatherState, WeatherEvent, WeatherEffect>() {

    private val mutableState: MutableStateFlow<WeatherState> = MutableStateFlow(WeatherState.LoadingLocation)

    override val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened,
            WeatherEvent.RetryClicked,
            -> emitEffect(WeatherEffect.RequestLocationPermission)

            is WeatherEvent.LocationPermissionResult -> handlePermissionResult(event.granted)
            WeatherEvent.SettingsClicked -> emitEffect(WeatherEffect.OpenAppSettings)
        }
    }
}
```

### 4. Domain-UI Mapper

```kotlin
public class WeatherUiMapper {

    public fun map(source: CurrentWeather): WeatherUiModel =
        WeatherUiModel(
            cityName = source.cityName,
            temperature = "${source.temperatureCelsius.roundToInt()} C",
            cloudCover = "${source.cloudCoverPercent} %",
            windSpeed = "${source.windSpeedKilometersPerHour.roundToInt()} км/ч",
            precipitation = "${source.precipitationMillimeters} мм",
        )
}
```

### 5. Manual DI Module And Component

```kotlin
public object WeatherDataModule {

    public fun provide(
        currentLocationProvider: CurrentLocationProvider,
        resolveCityName: ResolveCityNameInteractor,
    ): CurrentWeatherRepository =
        CurrentWeatherRepositoryImpl(
            currentLocationProvider = currentLocationProvider,
            resolveCityName = resolveCityName,
            api = OpenMeteoApi(createHttpClient()),
            mapper = WeatherDataDomainMapper(),
        )
}

public class WeatherScreenComponent(
    repository: CurrentWeatherRepository,
) {

    public val viewModel: WeatherViewModel =
        WeatherViewModel(
            getCurrentWeather = GetCurrentWeatherInteractor(repository),
            mapper = WeatherUiMapper(),
        )
}
```

## Prohibited Patterns

- Ktor calls, Open-Meteo DTOs, or HTTP error parsing in `ui` or `domain`.
- DTOs or `HttpClient` outside `data`.
- Calling a repository directly from Compose UI or bypassing an Interactor.
- `GlobalScope`, unmanaged coroutines, public mutable state, or `MutableStateFlow` exposed as `StateFlow`.
- Network loading in `ViewModel.init`; use `WeatherEvent.ScreenOpened`.
- Version literals in `build.gradle.kts`, wildcard imports, `TODO()` in shipped code, or unchecked casts.
- Android or iOS platform APIs in `commonMain` without an `expect`/`actual` boundary.
- Dependencies outside the allowed module graph.

## Change Checklist

For each production change:

1. Keep module dependency rules and visibility boundaries.
2. Add or update tests for domain/data mapping and ViewModel state; add Compose UI tests for user-visible behavior.
3. Run relevant `detekt`, tests, and compilation tasks configured by the project.
4. Check Open-Meteo attribution remains visible in the product when the provider is used.
5. Update this file when the approved architecture, source patterns, or verification workflow changes.
