# Day 5. Weather Feature Decomposition

## Purpose

This document fixes target decomposition for city search, city history, favorites, tabs, and weather icons. It is a planning boundary for future tasks, not an implementation task.

Current MVP flow stays unchanged until a dedicated implementation task changes it:

```text
WeatherApp -> WeatherScreen -> WeatherViewModel -> GetCurrentWeatherInteractor
-> CurrentWeatherRepositoryImpl -> CurrentLocationProvider
-> ReverseGeocodingRepository + OpenMeteoApi
-> WeatherDataDomainMapper -> WeatherUiMapper -> WeatherState.Content
```

Current `WeatherRoute` in `app` is temporary. New navigation work must introduce feature-owned `NavKey` routes instead of copying private app-owned route ownership.

## Implementation Order

1. Weather location contract
   - Add a weather-domain value type for selected forecast location.
   - Support two explicit paths: current device location and selected coordinates.
   - Keep current-location permission flow separate from city-selection flow.

2. City search
   - Add search contracts, data implementation, mapper, and UI as an independent flow.
   - Search returns stable city candidates with coordinates and display name.
   - Selecting a city only emits a selected-location result; it must not add history, favorites, tabs, or icon work.

3. City history storage
   - Add persistent storage boundary after city selection exists.
   - History stores selected city value objects, not DTOs, ViewModels, or Compose state.
   - History update policy is a separate domain rule: dedupe, ordering, and max size belong here.

4. Favorites storage
   - Add favorites after history storage boundary is clear.
   - Favorites reuse the selected city value type but own separate repository and domain rules.
   - Favorite state must not be inferred from history.

5. Weather tabs
   - Add tab/navigation composition after search, history, and favorites have stable contracts.
   - Tabs coordinate feature entrypoints, back stack, and selected location state.
   - App remains composition owner; feature modules own their UI and navigation keys.

6. Weather icons
   - Add icon mapping after weather content model is stable.
   - Weather data maps provider weather codes to domain conditions.
   - Weather UI maps domain conditions to UI icon models or drawable resources.

## Module Boundaries

Target module graph should grow by explicit feature boundaries only:

```text
app -> core:location, core:mvvm,
       feature:reverse-geocoding:{domain,data},
       feature:weather:{domain,data,ui},
       feature:city-search:{domain,data,ui},
       feature:city-history:{domain,data,ui},
       feature:favorites:{domain,data,ui}

feature:weather:data -> core:location,
                        feature:reverse-geocoding:domain,
                        feature:weather:domain

feature:weather:ui -> core:mvvm,
                      feature:weather:domain

feature:city-search:data -> feature:city-search:domain
feature:city-search:ui -> core:mvvm,
                          feature:city-search:domain

feature:city-history:data -> feature:city-history:domain
feature:city-history:ui -> core:mvvm,
                           feature:city-history:domain

feature:favorites:data -> feature:favorites:domain
feature:favorites:ui -> core:mvvm,
                      feature:favorites:domain
```

Rules:

- `app` owns manual DI, Navigation 3 back stack, serializers aggregation, and cross-feature composition.
- Feature `domain` owns public contracts and stable value types.
- Feature `data` owns API clients, DTOs, storage drivers, repository implementations, and data-domain mappers; these stay `internal`.
- Feature `ui` owns ViewModel, UDF state/events/effects, UI mappers, and composables.
- `ui` never depends on `data`.
- A feature never depends on another feature's `data` or `ui`.
- `feature:*` modules use `implementation` dependencies only.

## Area Ownership

### Weather Location

Owner: `feature:weather:domain` for selected forecast location contract; `feature:weather:data` for loading forecast by location.

Weather repository/usecase must expose explicit selected-coordinates loading instead of hiding selected city behind `CurrentLocationProvider`.

### City Search

Owner: new `feature:city-search`.

Search is a new forward-geocoding flow. Existing reverse geocoding remains coordinates-to-city-name only and should not become a general search feature.

### City History

Owner: new `feature:city-history`.

History owns storage and recency policy. It may expose selected city entries to `app`, but it must not trigger weather loading directly.

### Favorites

Owner: new `feature:favorites`.

Favorites owns saved-city membership and ordering. It must not duplicate history policy or depend on history storage internals.

### Tabs

Owner: `app` for composition; feature modules for tab content.

Tabs should compose existing feature entrypoints. They should not introduce new repositories, storage rules, or weather API behavior.

### Weather Icons

Owner: `feature:weather:data` for provider-code-to-domain mapping; `feature:weather:ui` for domain-condition-to-icon mapping.

Provider codes must not leak into UI state. UI resources must not leak into domain.

## Do Not Mix In One Task

- City search and history persistence.
- City search and favorites.
- History and favorites storage.
- Tabs and new storage behavior.
- Tabs and weather forecast loading semantics.
- Weather icons and city search.
- Weather icons and navigation.
- Feature-owned `NavKey` migration and unrelated UI redesign.
- Current-location permission changes and selected-city forecast loading.
- Reverse geocoding cleanup and forward city search provider integration.
- Dependency graph changes and visual polish.
- Source implementation and execution-loop reporting artifacts.

Each future task should have one owner area, one module boundary change, and one verification target.
