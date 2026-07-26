# Сравнение веток `day/1` и `day/1-v2`

Контекст: сравнение выполнено по структуре репозитория и Gradle-зависимостям между ветками `day/1` и `day/1-v2`.

| Область | Общее | Разное | Вероятное объяснение |
|---|---|---|---|
| Архитектура модулей | Оба имеют `app`, `core:location`, `core:mvvm`, `feature:reverse-geocoding:{domain,data}`, `feature:weather:{domain,data,ui}` | `day/1-v2` добавляет `androidApp`; всего 9 include вместо 8 | Разделение Android host и shared KMP app. `app` стал shared library, `androidApp` стал APK host |
| Android сборка | Android target, Compose UI, `compileSdk=37`, `minSdk=26` | `day/1`: `app` = `com.android.application`; `day/1-v2`: `androidApp` = application, `app` = `com.android.kotlin.multiplatform.library` | Более правильная KMP схема: shared module отдельно, platform app отдельно |
| iOS структура | Оба имеют iOS entrypoint через `MainViewController` | `day/1`: только `iosApp/Info.plist`; `day/1-v2`: Xcode project, SwiftUI app files, `WeatherShared` static framework config | `day/1-v2` готовит реальный iOS host, не только KMP controller |
| Module graph | Фичи разделены на `domain/data/ui`; `ui` не зависит от `data`; `data` зависит от domain | `androidApp -> app` добавлен; остальные основные project-deps почти те же | Новый host слой, бизнес-разбиение сохранено |
| Gradle plugins | Kotlin MPP, Compose, serialization, Android app, detekt есть в catalog | `day/1`: `com.android.library`; `day/1-v2`: `com.android.kotlin.multiplatform.library`; detekt подключен явно в каждом модуле | Переход на новый Android KMP library plugin и явное подключение detekt вместо root `subprojects` |
| Version catalog | Kotlin `2.4.10`, coroutines `1.11.0`, detekt `1.23.8`, Navigation3 `1.1.1`, serialization `1.11.0` | `day/1-v2`: AGP `9.1.1` вместо `9.2.0`, Compose `1.11.1` вместо `1.11.0`, Ktor `3.5.1` вместо `3.5.0`, lifecycle `2.10.0` вместо `2.11.0`, AndroidX core `1.19.0` вместо `1.18.0`, добавлен savedstate `1.4.0` | Alignment под доступный/совместимый KMP stack; savedstate нужен для Navigation3 state config |
| Compose deps | Оба используют Compose runtime/ui и Material | `day/1`: `compose.material3`, `materialIconsExtended`; `day/1-v2`: `compose.material`, `compose.foundation`, без icons | UI упрощен/переписан под Material 2/common Compose совместимость |
| Navigation deps | Оба используют Navigation3 UI и kotlinx serialization | `day/1`: `lifecycle-viewmodel-navigation3`; `day/1-v2`: `androidx.savedstate`, без `lifecycle-viewmodel-navigation3` | Навигационное состояние вынесено через `SavedStateConfiguration`, без extra lifecycle navigation helper |
| Ktor/data deps | Оба: Ktor core, content negotiation, okhttp, darwin, mock, serialization JSON | `day/1-v2` только переименовал aliases и обновил Ktor `3.5.0` -> `3.5.1` | Содержательно тот же HTTP stack |
| Domain source layout | Те же контракты: weather repo/interactor/model, reverse geocoding repo/interactor/model | `day/1`: несколько public типов в одном файле; `day/1-v2`: `CurrentWeather`, `GetCurrentWeatherInteractor`, exceptions, `CityName` вынесены в отдельные файлы | Приведение к правилу "один независимый public type на файл" |
| Location layer | Оба имеют `CurrentLocationProvider` и platform modules | `day/1-v2`: добавлены `Coordinates`, `LocationUnavailableException`, `AndroidCurrentLocationProvider`, `IosCurrentLocationProvider` отдельными файлами | Больше явных platform implementations, меньше "все в Module.kt" |
| Reverse geocoding contract | Оба используют native reverse geocoding | `day/1`: repository возвращает `CityName?`; `day/1-v2`: `Result<CityName?>` | Ошибки geocoder стали частью доменного результата, не скрытый exception/null |
| Weather data models | Оба используют Open-Meteo API | `day/1-v2`: `OpenMeteoResponse` вынесен отдельным файлом | Та же декомпозиция public/internal типов и читаемость data layer |
| Tests | Есть mapper/API/ViewModel tests | `day/1-v2`: `WeatherViewModelTest` переехал из `androidUnitTest` в `commonTest`; удален `GetCurrentWeatherInteractorTest`; reverse geocoding/data common test deps частично убраны | Больше common тестирования UI logic; простые interactor tests могли посчитать лишними |
| Tooling/files | Gradle wrapper и AGENTS есть в обеих | `day/1-v2`: удалены `.editorconfig`, `gradle-wrapper.jar`, `gradlew.bat`; wrapper properties обновлены | Возможная нормализация под macOS/Linux repo, но удаление wrapper jar делает wrapper менее самодостаточным |

## Почему `day/1` собрался в Android Studio Otter, а `day/1-v2` потребовал Quail

Ключевое различие не только в версиях зависимостей, а в Android/KMP plugin model.

| Фактор | `day/1` | `day/1-v2` | Влияние на Android Studio |
|---|---|---|---|
| Роль `app` | `app` одновременно Android application и KMP module | `app` стал shared KMP library, Android APK host вынесен в `androidApp` | `day/1-v2` ближе к новой recommended KMP layout, IDE должна корректно понимать shared framework + отдельный host |
| Android Gradle plugin | `9.2.0` | `9.1.1` | Оба значения относятся к свежему AGP 9.x; по официальной матрице Otter не является целевой IDE для AGP 9.1/9.2 |
| Gradle wrapper | `9.4.1` | `9.3.1` | Не главная причина требования Quail; обе версии свежие |
| Android plugin для KMP library | Обычный `com.android.library` в feature/core модулях | Новый `com.android.kotlin.multiplatform.library` | Это главный сдвиг: новый Android-KMP plugin требует более новой IDE/model sync |
| Kotlin DSL Android target | `androidTarget()` + отдельный блок `android { ... }` | `kotlin { android { namespace/compileSdk/minSdk } }` | `day/1-v2` использует новый DSL, который старый Otter хуже понимает |
| Legacy flags | `android.newDsl=false`, `android.builtInKotlin=false` | Эти флаги удалены | `day/1` мог обходить новую AGP/KMP модель; `day/1-v2` уже включает новый путь |
| Android app plugin | `com.android.application` прямо в `app` | `com.android.application` только в `androidApp` | `day/1-v2` требует IDE, которая корректно синхронизирует Android app + shared KMP library graph |
| `compileSdk` | `37` | `37` | API 37 сам по себе тоже тянет tooling вверх; Quail подходит лучше, чем Otter |

Вывод: `day/1` мог собраться в Otter потому, что оставался в legacy-режиме AGP/KMP: обычные Android plugins, `androidTarget()`, плюс флаги `android.newDsl=false` и `android.builtInKotlin=false`. Даже при свежем AGP это могло пройти через CLI Gradle build или частичный IDE sync.

`day/1-v2` убрал legacy-флаги и перешел на `com.android.kotlin.multiplatform.library`, отдельный `androidApp`, новый `kotlin { android { ... } }` DSL и iOS framework configuration. Поэтому Android Studio должна понимать новую AGP 9 Android-KMP модель; для этого потребовался Quail.

Официальные ориентиры:

- Android Studio / AGP compatibility: https://developer.android.com/build/releases/about-agp
- Kotlin Multiplatform AGP 9 migration: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- JetBrains AGP 9 migration notes: https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/

## Почему при одинаковом prompt и модели получились разные результаты

Ключевая причина: входные данные не были одинаковыми. `day/1` действительно стартовал с общего коммита `cc9c2c0a33401c076df7bcc8774b378650ac9987`, а `day/1-v2` стартовал с `925059491d171f81dc1a231667dec2a32a946548`.

Коммит `925059491d171f81dc1a231667dec2a32a946548` меняет только `AGENTS.md` и переносит в правила проекта выводы после первой реализации (`AGENTS.md from v1`). Проверка показала, что `AGENTS.md` в `9250594` совпадает с обновленным `AGENTS.md` из финала `day/1`: diff между `e0bc65e` и `9250594` по `AGENTS.md` пустой.

| Причина | Эффект |
|---|---|
| `AGENTS.md` стал конкретным, не абстрактным | Модель уже видела точный модульный layout: `core:location`, `core:mvvm`, `feature:weather`, `feature:reverse-geocoding` |
| В правила попали реальные имена типов | Вместо generic `WeatherRepository` модель шла к `CurrentWeatherRepository`, `GetCurrentWeatherInteractor`, `WeatherDataModule` |
| В правила попали ограничения dependency graph | Меньше свободы придумать архитектуру, больше следования заданному graph |
| Forecast API отделен от native reverse geocoding | В `day/1-v2` естественнее появились Android `Geocoder` и iOS `CLGeocoder` реализации |
| Уточнено правило one-public-type-per-file и указана техдолговая точка | `day/1-v2` разложил domain/data типы по отдельным файлам |
| Уточнена target-архитектура Navigation 3 | Второй запуск осторожнее относился к route ownership |
| Убраны/изменены tooling hints | Появились другие Gradle/IDE решения, включая новый Android-KMP plugin model |

Вывод: `9250594` был не тем же стартовым состоянием, а промежуточной спецификацией, обогащенной результатом `day/1`. Prompt, model settings и общий исходный замысел могли быть одинаковыми, но project context уже изменился. Для Codex `AGENTS.md` является частью управляющего контекста, поэтому второй запуск получил более жесткий и конкретный local contract.

Дополнительный фактор: LLM-генерация path-dependent. Даже при одинаковых настройках agent exploration, tool outputs, Gradle errors, IDE/tooling constraints и локальные файлы меняют дальнейшие решения каскадом. Но в этом сравнении главный фактор виден в Git: `day/1-v2` был вторым проходом с уточненным `AGENTS.md`, а не чистым повтором с `cc9c2c0`.

Итог: `day/1` выглядит как MVP, где `app` одновременно shared KMP module и Android application. `day/1-v2` выглядит как более production-like KMP scaffold: отдельный Android host, настоящий iOS host, shared framework, явный Android KMP library plugin, чище разложены доменные/data типы. Основная бизнес-архитектура и dependency graph сохранены; различия в основном от платформенной упаковки, совместимости KMP/Compose/AGP и приведения файлов к локальным правилам.
