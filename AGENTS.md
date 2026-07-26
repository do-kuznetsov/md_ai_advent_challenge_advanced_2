# Weather KMP Project Rules

## Scope And Configuration

This repository contains a Kotlin Multiplatform weather application. Targets: Android and iOS. UI: Compose Multiplatform.

Follow instructions in this order:

1. Global `AGENTS.md`.
2. This root `AGENTS.md`.
3. Nearest `AGENTS.md` inside edited directory, if present.

Lower-level rules add detail; they never override higher-level rules. Before editing, inspect the applicable rules and current staged and unstaged Git changes.

Use a skill only when its trigger matches the task, and read its `SKILL.md` first. Delegate independent repository research or a final diff review to subagents when that saves time. Do not delegate a trivial one-file change.

## Stack

- Kotlin Multiplatform, Compose Multiplatform, Android, iOS.
- Ktor and `kotlinx.serialization` for HTTP and JSON.
- Open-Meteo Forecast and Geocoding APIs. This project uses their non-commercial educational tier: no API key, required attribution, no commercial use.
- Navigation 3 for Compose Multiplatform.
- Gradle Kotlin DSL and `gradle/libs.versions.toml`. Dependency and plugin versions exist only in the version catalog.
- `ktlint`, `detekt`, and relevant unit or UI tests are required for production changes.

Do not add cache, persistent storage, favorites, settings, or another weather provider without an explicit task.

## Modules And Dependencies

Project layout:

```text
app/
core/<shared-concern>/
feature/<feature-name>/
  domain/
  data/
  ui/
```

Examples of feature modules: city weather, map, city search.

Dependency graph:

```text
data -> domain
ui -> domain
app -> core + feature modules
```

`app` owns application composition: manual DI graph, Navigation 3 back stack, feature route serializers. Only listed edges are allowed. In particular, `ui` never depends on `data`, and one feature never reaches into another feature's `data` or `ui` module.

`data` may also depend on `core:*` and another feature's `domain` module. This is reserved for reusable platform services and feature contracts, such as location and reverse geocoding. `ui` still depends only on its own `domain` module and `core:mvvm`.

`data` exposes one manual DI module per feature. Keep API clients, DTOs, repository implementations, and data-to-domain mappers `internal`. `ui` exposes a DI module; add an `internal` component only when runtime arguments or lifecycle-scoped objects are needed. `app` assembles those modules and components.

## UI And Navigation

Each screen uses UDF through `BaseViewModel<State, Event, Effect>`:

- `state` is immutable `StateFlow<State>`.
- `State`, `Event`, and `Effect` are sealed interfaces.
- `onViewEventOccurred` is the only UI entrypoint for events.
- `SideEffect` is for one-shot commands, never persistent screen state.
- Start loading from an explicit screen event. Do not make a network request from `ViewModel.init`.

Navigation 3 rules:

- Each feature owns an `@Serializable sealed interface` extending `NavKey`.
- `app` owns a user-managed back stack and aggregates feature serializers.
- Android and iOS use `SavedStateConfiguration` with a `SerializersModule`; do not rely on JVM reflection serialization.
- Route arguments are stable value types. Do not pass ViewModels, repositories, DTOs, or Compose state through routes.

## Naming And Source Layout

Root package: `com.sibgear.weather`.

- Contracts: `WeatherRepository`, `GetWeatherInteractor`.
- Implementations: `WeatherRepositoryImpl`, `WeatherDataModule`.
- UI: `WeatherViewModel`, `WeatherState`, `WeatherEvent`, `WeatherEffect`.
- Mappers: `WeatherDataDomainMapper`, `WeatherUiMapper`.
- Public types use explicit `public`; implementation details use explicit `internal`.
- One public type per file. File name matches its primary type.
- Prefer immutable `data class`, `data object`, `val`, expression bodies for simple forwarding methods, and named constructor arguments when an object has several dependencies.

Typical file shape:

```kotlin
package com.sibgear.weather.feature.cityweather.domain

import com.sibgear.weather.feature.cityweather.domain.model.Weather

public interface WeatherRepository {
    public suspend fun getWeather(city: City): Result<Weather>
}

internal class WeatherRepositoryDecorator(
    private val delegate: WeatherRepository,
) : WeatherRepository {

    override suspend fun getWeather(city: City): Result<Weather> = delegate.getWeather(city)
}
```

Order imports: Kotlin and AndroidX, third-party libraries, then project imports. Keep a single primary constructor and no wildcard imports.

## Normative Examples

### 1. Domain Contract And Interactor

```kotlin
public interface WeatherRepository {
    public suspend fun getWeather(city: City): Result<Weather>
}

public class GetWeatherInteractor(
    private val repository: WeatherRepository,
) {

    public suspend operator fun invoke(city: City): Result<Weather> = repository.getWeather(city)
}
```

### 2. Data Implementation And Data-Domain Mapper

```kotlin
internal class WeatherRepositoryImpl(
    private val api: OpenMeteoApi,
    private val mapper: WeatherDataDomainMapper,
) : WeatherRepository {

    override suspend fun getWeather(city: City): Result<Weather> = runCatching {
        mapper.map(api.getForecast(latitude = city.latitude, longitude = city.longitude))
    }
}

internal class WeatherDataDomainMapper {

    internal fun map(source: ForecastDto): Weather = Weather(
        temperatureCelsius = source.current.temperatureCelsius,
        weatherCode = source.current.weatherCode,
    )
}
```

### 3. UDF ViewModel, State, And Event

```kotlin
public sealed interface WeatherState : ViewState {
    public data object Loading : WeatherState
    public data class Content(val weather: WeatherUiModel) : WeatherState
    public data object Error : WeatherState
}

public sealed interface WeatherEvent : ViewEvent {
    public data object ScreenOpened : WeatherEvent
    public data object RefreshClicked : WeatherEvent
}

public class WeatherViewModel(
    private val city: City,
    private val getWeather: GetWeatherInteractor,
    private val mapper: WeatherUiMapper,
) : BaseViewModel<WeatherState, WeatherEvent, WeatherEffect>() {

    private val mutableState = MutableStateFlow<WeatherState>(WeatherState.Loading)
    override val state: StateFlow<WeatherState> = mutableState.asStateFlow()

    override suspend fun handleViewEvent(event: WeatherEvent) {
        when (event) {
            WeatherEvent.ScreenOpened,
            WeatherEvent.RefreshClicked,
            -> loadWeather()
        }
    }

    private suspend fun loadWeather() {
        mutableState.value = WeatherState.Loading
        mutableState.value = mapper(getWeather(city))
    }
}
```

### 4. Domain-UI Mapper

```kotlin
public class WeatherUiMapper {
    public operator fun invoke(source: Result<Weather>): WeatherState = source.fold(
        onSuccess = { weather -> WeatherState.Content(weather.toUiModel()) },
        onFailure = { WeatherState.Error },
    )

    private fun Weather.toUiModel(): WeatherUiModel = WeatherUiModel(
        temperature = "${temperatureCelsius} C",
        weatherCode = weatherCode,
    )
}
```

### 5. Manual DI Module And Component

```kotlin
public object WeatherDataModule {
    public fun provideRepository(client: HttpClient): WeatherRepository = WeatherRepositoryImpl(
        api = OpenMeteoApi(client),
        mapper = WeatherDataDomainMapper(),
    )
}

internal class WeatherScreenComponent(
    repository: WeatherRepository,
    city: City,
) {
    internal val viewModel: WeatherViewModel = WeatherViewModel(
        city = city,
        getWeather = GetWeatherInteractor(repository),
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
3. Run relevant `ktlint`, `detekt`, tests, and compilation tasks configured by the project.
4. Check Open-Meteo attribution remains visible in the product when the provider is used.

This repository currently has no Gradle scaffold. These examples are normative until production modules exist; replace or supplement them with local production examples after implementation begins.
