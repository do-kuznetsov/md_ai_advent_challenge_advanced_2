# UI Smoke Playbook

## Purpose

Reusable Codex playbook for Day 3, Level 2: UI smoke scenarios through MCP.

This playbook tells an agent how to:

1. Select the user-requested platform for a smoke run.
2. Prepare a mobile app artifact and device or simulator.
3. Run 3-5 user-path smoke scenarios through Claude in Mobile.
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
- 3-5 smoke scenarios matching the current weather app flow.
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

- app opens weather screen;
- screen requests coarse location permission;
- denied permission shows location-access error and retry path;
- granted permission loads current weather through real location, reverse geocoding, and Open-Meteo;
- content screen shows city or current-location label, weather metrics, and `Данные: Open-Meteo.com`;
- settings action opens platform app settings when permission is permanently denied.

Smoke scenarios use real platform permission UI, real location services, real reverse geocoding, and real Open-Meteo network unless the selected device or simulator provides a deterministic location override.

## Open-Meteo Attribution

Forecast data uses Open-Meteo Forecast API. City search uses Open-Meteo Geocoding API.

Both APIs are used without an API key.

Current visible attribution location is the weather content screen: `Данные: Open-Meteo.com`.

City search and geocoding are covered by the same visible provider attribution in the weather flow. If a future UI shows separate city-search results before opening weather content, that search UI must also keep Open-Meteo attribution visible.

## Smoke Scenarios

Run 3-5 scenarios. Prefer all five when environment supports them.

### 1. Cold Start / Permission Request

Path:

1. Reset app state.
2. Launch `Погода`.
3. Capture initial screen.
4. Verify loading or location text is visible, such as `Определяем местоположение`.
5. Verify OS location permission request appears.
6. Capture permission request.

Pass when app launches and requests location permission without crash.

### 2. Permission Denied

Path:

1. Start from fresh permission request.
2. Deny location permission.
3. Capture result screen.
4. Verify error text `Для прогноза нужен доступ к геолокации.`.
5. Verify `Повторить` is visible.
6. If platform exposes permanently denied state in this run, verify `Открыть настройки` is visible.

Pass when denied permission produces an actionable error state.

### 3. Retry Path

Path:

1. Start from denied permission error.
2. Tap `Повторить`.
3. Capture result.
4. Verify one of the valid platform outcomes:
   - OS permission dialog appears again;
   - same denied/settings state remains visible;
   - platform settings path is offered after permanent denial.

Pass when retry keeps user in a recoverable permission flow and app does not crash.

### 4. Permission Granted / Weather Content

Path:

1. Reset app state and location permission state.
2. Set emulator or simulator location when available.
3. Launch app.
4. Grant location permission.
5. Capture loading state.
6. Wait for weather content.
7. Capture content screen.
8. Verify city or `Текущее местоположение` label is visible.
9. Verify weather metrics are visible: temperature, cloud cover, wind, precipitation.
10. Verify Open-Meteo attribution `Данные: Open-Meteo.com` is visible.

Pass when granted permission reaches weather content with attribution.

### 5. Settings Escape Hatch

Path:

1. Force permanently denied location state where the platform supports it.
2. Launch app.
3. Capture error screen.
4. Tap `Открыть настройки`.
5. Capture destination screen.
6. Verify platform app settings for `Погода` or package `com.sibgear.weather` opened.

Pass when the settings action opens the platform app settings page.

If a platform cannot force permanent denial deterministically, mark this scenario `blocked`, not failed.

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
- File defines 3-5 smoke scenarios matching the current weather app flow.
- File requires screenshots for every meaningful step.
- File requires honest `passed`, `failed`, or `blocked` status per scenario.
- File states the smoke platform is chosen by the user at run time.
- File references Claude in Mobile for this mobile repository and does not use Playwright as the default.

For a later smoke-run task:

- Platform is explicitly selected by the user.
- Claude in Mobile device/simulator preflight is recorded.
- App artifact is built or an existing artifact is verified.
- 3-5 scenarios are executed or blocked with exact reason.
- Screenshot paths are included for every completed step.
- Final report follows the UI smoke report template.
