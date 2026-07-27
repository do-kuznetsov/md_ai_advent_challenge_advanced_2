# UI Smoke Playbook

## Purpose

Reusable Codex playbook for Day 3, Level 2: UI smoke scenarios through MCP.

This playbook tells an agent how to:

1. Select the user-requested platform for a smoke run.
2. Prepare a mobile app artifact and device or simulator.
3. Run the current five user-path smoke scenario groups through Claude in Mobile.
4. Capture screenshot evidence for every step.
5. Report passed, failed, or blocked status honestly.

This file is documentation only. Do not add, edit, or delete production source, unit-test source, or Gradle configuration while creating or updating this playbook.

Level 2 does not measure unit coverage and must not update Kover numbers. Use [unit-tests.md](unit-tests.md) for Level 1 business-logic tests and Kover coverage.

## Platform Selection

The smoke platform is chosen by the user at run time.

If the user does not explicitly choose a platform, ask which platform to use before launching any app or device tooling:

- `Android`
- `iOS`

Do not choose a platform by default. Do not run both platforms unless the user explicitly asks for both.

Use Claude in Mobile for this mobile repository:

- Android: ADB backend.
- iOS: `simctl` backend.

Do not use Playwright MCP for this repository unless a future web target becomes the selected smoke surface.

## Codex Prompt

Use this prompt in a fresh Codex task when running Level 2 UI smoke:

```text
Run Day 3 / Level 2 UI smoke for this repository.

Goal:
- Run user-path smoke scenarios through Claude in Mobile.
- Do not write Kotlin, Gradle, or production/test source code.
- Capture screenshots for every meaningful step.
- Produce a UI smoke report with pass/fail/blocked status per scenario.

Platform:
- Ask the user for Android or iOS if not explicitly provided.

Constraints:
- Inspect applicable AGENTS.md first.
- Preserve staged, unstaged, and untracked user changes.
- Use Claude in Mobile, not Playwright, for this mobile app.
- If Claude in Mobile, device/simulator, app artifact, permissions, or required services are unavailable, report `blocked`.
- Do not invent UI results, screenshots, logs, or device state.
- Do not update Kover coverage.

Required result:
- Five smoke scenarios matching the current weather app flow: current location, city input, history, favorites, and map tab.
- Screenshot evidence for every completed step.
- Report with baseline, scenario status, executed steps, screenshot paths, failures, risks, and next actions.
```

## Preflight

Start every smoke task with checkout and environment evidence:

```bash
git status --short
git diff --cached --name-status
git diff --name-status
rg --files -g 'AGENTS.md'
claude-in-mobile devices
```

Then prepare the selected platform.

Android:

```bash
./gradlew :androidApp:assembleDebug
claude-in-mobile devices android
claude-in-mobile install androidApp/build/outputs/apk/debug/androidApp-debug.apk
claude-in-mobile stop com.sibgear.weather
claude-in-mobile shell pm clear com.sibgear.weather
claude-in-mobile launch com.sibgear.weather
```

iOS:

```bash
xcodebuild -project iosApp/WeatherIos.xcodeproj -scheme WeatherIos -configuration Debug -sdk iphonesimulator
claude-in-mobile devices ios
claude-in-mobile install <built WeatherIos.app path>
claude-in-mobile stop <bundle id>
claude-in-mobile launch <bundle id>
```

If any command cannot run because a tool, device, simulator, artifact, permission, or build dependency is missing, stop the smoke run and report `blocked` with the exact blocker.

Store screenshots and transient artifacts outside tracked source, for example:

```text
build/reports/ui-smoke/<timestamp>/<platform>/
```

## Current App Surface

Current app name: `Погода`.

Android package id: `com.sibgear.weather`.

Current user-visible flow:

- app opens on the `Список` tab with a city input field labeled `Город`;
- the `Список` tab contains the weather screen and starts by requesting coarse location permission;
- denied permission shows location-access error and retry path;
- permanently denied permission additionally shows `Открыть настройки`;
- granted permission loads current weather through real location, reverse geocoding, and Open-Meteo;
- manual city input uses `Найти`, Open-Meteo Geocoding API, and loads weather for the first city candidate;
- successful manual city selection is saved to `Недавние города`;
- selected manual/history/favorite city weather can be added to or removed from `Избранное`;
- `Карта` is a separate app tab with an interactive no-key map surface;
- Android uses MapLibre Compose with the default demo style; iOS/JVM use an in-app interactive fallback map until native MapLibre framework setup is added;
- content screen shows city or current-location label, weather metrics, weather icons, and `Данные: Open-Meteo.com`;
- settings action opens platform app settings when permission is permanently denied.

Smoke scenarios use real platform permission UI, real location services, real reverse geocoding, Open-Meteo Forecast API, and Open-Meteo Geocoding API unless the selected device or simulator provides deterministic overrides.

## Open-Meteo Attribution

Forecast data uses Open-Meteo Forecast API. City search uses Open-Meteo Geocoding API.

Both APIs are used without an API key.

Current visible attribution location is the weather content screen: `Данные: Open-Meteo.com`.

City search and geocoding are covered by the same visible provider attribution in the weather flow. If a future UI shows separate city-search results before opening weather content, that search UI must also keep Open-Meteo attribution visible.

## Smoke Scenarios

Run these five scenario groups. Each group may be `passed`, `failed`, or `blocked`.

### 1. Current Location Permission And Weather

Path:

1. Reset app state.
2. Launch `Погода`.
3. Capture initial screen.
4. Verify the `Список` tab is selected and the city field `Город` plus `Найти` button are visible.
5. Verify loading or location text is visible, such as `Определяем местоположение`.
6. Verify OS location permission request appears.
7. Capture permission request.
8. Grant location permission.
9. Capture loading state `Получаем погоду` if visible.
10. Wait for weather content.
11. Capture content screen.
12. Verify city or `Текущее местоположение` label is visible.
13. Verify weather metrics are visible: temperature, `Облачность`, `Ветер`, `Осадки`.
14. Verify Open-Meteo attribution `Данные: Open-Meteo.com` is visible.

Pass when app launches, requests permission, and granted permission reaches weather content with attribution.

### 2. Permission Denied, Permanent Denied, And Retry

Path:

1. Start from fresh permission request.
2. Deny location permission.
3. Capture result screen.
4. Verify error text `Для прогноза нужен доступ к геолокации.`.
5. Verify `Повторить` is visible.
6. Tap `Повторить`.
7. Capture result.
8. Verify one of the valid platform outcomes:
   - OS permission dialog appears again;
   - same denied/settings state remains visible;
   - platform settings path is offered after permanent denial.
9. Force permanently denied location state where the platform supports it.
10. Launch app.
11. Capture error screen.
12. Verify `Открыть настройки` is visible.
13. Tap `Открыть настройки`.
14. Capture destination screen.
15. Verify platform app settings for `Погода` or package `com.sibgear.weather` opened.

Pass when denied permission stays recoverable, permanent denial exposes settings, and retry does not crash.

If a platform cannot force permanent denial deterministically, mark only the permanent-denial portion `blocked` and keep the denial/retry portion evaluated.

### 3. Manual City Input

Path:

1. Start from the `Список` tab.
2. Tap the `Город` input.
3. Type a real city name, for example `Москва`.
4. Capture filled input.
5. Tap `Найти` or submit the search IME action.
6. Capture loading state `Получаем погоду` if visible.
7. Wait for weather content.
8. Capture content screen.
9. Verify the requested city name is visible.
10. Verify the favorite button is visible with content description `Добавить в избранное` or `Убрать из избранного`.
11. Verify weather metrics and `Данные: Open-Meteo.com` are visible.

Pass when manual city search reaches city weather content without requesting location permission again.

### 4. History And Favorites

Path:

1. Complete a successful manual city search.
2. Capture the `Недавние города` block.
3. Verify the searched city appears as a history item.
4. Tap the history city item or its `Показать` action.
5. Capture weather content.
6. Verify weather loads for the history city.
7. Tap `Добавить в избранное`.
8. Capture the updated screen.
9. Verify `Избранное` appears and contains the city.
10. Tap the favorite city item.
11. Capture weather content.
12. Verify weather loads for the favorite city and the favorite button exposes `Убрать из избранного`.
13. Tap `Убрать из избранного`.
14. Capture updated list.
15. Verify the city is no longer shown in `Избранное`.

Pass when history survives within the run, favorites can be added and removed, and favorite/history selection loads city weather.

### 5. Map Tab

Path:

1. Launch app.
2. Capture the tab row.
3. Tap `Карта`.
4. Capture the map tab.
5. Pan the map surface.
6. Capture the panned map.
7. Pinch or use platform zoom gesture when available.
8. Capture the zoomed map.
9. Tap `Список`.
10. Capture the list tab.
11. Verify the weather screen surface is visible again: `Город`, `Найти`, and the current weather/loading/error content.

Pass when tab switching works and the map surface responds to pan or zoom.

Future map-selection tasks must update this scenario when tapping a map point loads weather.

## Execution Rules

- Use `screenshot` after launch, after every permission decision, after every tap, and at final state.
- Prefer `ui-dump`, `analyze-screen`, `find-and-tap`, or `tap-text` over raw coordinates when available.
- On Android, `analyze-screen` and `find-and-tap` are allowed.
- On iOS, use `ui-dump`, `find`, `tap-text`, `tap`, and screenshots; `find-and-tap` is not supported.
- Use short waits between app launch, permission actions, network loading, and settings transitions.
- Keep screenshots named by scenario and step, for example `01-cold-start-02-permission-dialog.png`.
- Do not report a scenario as passed without visible screenshot or UI dump evidence.
- Do not modify repository source as part of the smoke run.

## Blocked And Failed Rules

Use `blocked` when the scenario cannot be executed because required environment is unavailable:

- no selected platform;
- Claude in Mobile is not installed or not callable;
- no device or simulator is available;
- app artifact cannot be built or installed;
- location permission state cannot be reset;
- network, location services, or simulator location are unavailable and scenario depends on them.

Use `failed` when the environment works but the app behavior is wrong:

- app crashes;
- expected permission request never appears;
- denied permission does not show recoverable error;
- granted permission never leaves loading after a reasonable wait;
- weather content is missing required user-visible data or Open-Meteo attribution;
- manual city search cannot load weather for a valid city while the environment has network access;
- history or favorites do not reflect a successful city selection within the same smoke run;
- `Карта` tab cannot be opened or returned from;
- settings action does not open app settings.

Never invent screenshots, UI text, logs, or likely pass status.

## Report Template

Use this final report shape:

```markdown
## UI Smoke Report

Baseline:
- Platform: <Android / iOS>
- Device/simulator: <name, OS, id>
- App artifact: <path, build command, build result>
- Package/bundle id: <id>
- Git status: <clean / dirty, with scoped paths>
- Evidence dir: <build/reports/ui-smoke/...>

Scenarios:
- <scenario name>: <passed / failed / blocked>
  Steps: <executed steps>
  Screenshots: <paths>
  Last visible state: <short description>

Failures:
- <exact broken step, visible evidence, likely area>

Risks:
- <real network/geocoder/location/OS permission UI risks>

Next actions:
- <rerun command, required environment fix, or scenario update after new feature/deploy>
```

## Acceptance Criteria

For this playbook task:

- `ai/testing/ui-smoke.md` exists.
- File states this task is documentation only.
- File does not instruct the agent to write Kotlin, Gradle, production, or unit-test source.
- File defines five smoke scenario groups matching the current weather app flow.
- File requires screenshots for every meaningful step.
- File requires honest `passed`, `failed`, or `blocked` status per scenario.
- File states the smoke platform is chosen by the user at run time.
- File references Claude in Mobile for this mobile repository and does not use Playwright as the default.

For a later smoke-run task:

- Platform is explicitly selected by the user.
- Claude in Mobile device/simulator preflight is recorded.
- App artifact is built or an existing artifact is verified.
- Five scenario groups are executed or blocked with exact reason.
- Screenshot paths are included for every completed step.
- Final report follows the UI smoke report template.
