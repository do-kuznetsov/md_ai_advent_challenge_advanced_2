# День 5. Backlog Для Execution Loop

Перед запуском прогона заполнить 15-20 задач. Каждая задача должна быть маленькой, независимой, проверяемой локально и иметь четкий критерий "сделано / не сделано".

Микс для этого прогона: 20 задач, последние 5 - сложный хвост от более легких к самым рискованным.

- 1 research-задача;
- 3 документационные задачи;
- 2 refactor-задачи;
- 2 задачи на тесты;
- 12 feature-задач.

## Правила заполнения

- Не добавлять задачи, требующие внешних аккаунтов, ручных UI-действий, секретов или недоступных сервисов.
- Не добавлять задачи, где критерий готовности зависит от субъективной оценки.
- Для каждой изменяющей задачи указать локальную проверку: Gradle test, compile, detekt или `Markdown-only`.
- Для багов описать текущее поведение, ожидаемое поведение и способ воспроизведения.
- Для Research-задач явно указать, что checkout менять нельзя.

## Backlog

| ID | Тип | Описание | Критерий готовности | Ожидаемые проверки | Статус | Коммит | Время | Результат |
|---|---|---|---|---|---|---|---|---|
| D5-001 | research | Исследовать текущий flow получения погоды: текущая локация, Open-Meteo, ViewModel, UI-state, DI. | Ответ содержит файлы, связи и точки расширения для города, координат и истории. Checkout не изменен. | Research only | done | - | 3m38s | Исследован flow WeatherApp -> WeatherScreen -> ViewModel -> interactor -> repository -> location/reverse geocoding/Open-Meteo -> UI state; точки расширения зафиксированы в run log. |
| D5-002 | docs | Описать целевую декомпозицию city search, city history, favorites, tabs и weather icons для этого проекта. | Документ фиксирует порядок внедрения, границы модулей и что нельзя смешивать в одной задаче. | Markdown-only | done | 4486bc5 | 2m50s | Добавлен `ai/execution-loop/day5-weather-feature-decomposition.md` с порядком внедрения, module boundaries, ownership и запретами на смешивание задач. |
| D5-003 | feature | Добавить domain value type для выбранной погодной локации: название, latitude, longitude. | Domain умеет представить выбранный город или координаты отдельно от текущей геолокации. | Domain tests or affected module compile | done | 5df2d4c | 3m45s | Добавлен `SelectedWeatherLocation` с вариантами `City` и `Coordinates`; добавлены domain tests. |
| D5-004 | refactor | Расширить погодный repository/usecase: сохранить текущую загрузку по геолокации и добавить загрузку по выбранной локации. | Старый сценарий текущей локации не сломан, новый метод покрыт тестом. | Weather domain/data tests | done | 044d36f | 5m53s | Добавлены `loadWeather(SelectedWeatherLocation)` и interactor overload; current-location path сохранен, selected-city path покрыт тестами. |
| D5-005 | feature | Добавить Open-Meteo Geocoding client для поиска города по введенному названию. | По строке поиска возвращаются кандидаты с названием, страной и координатами. | Ktor MockEngine tests | done | b6041de | 4m07s | Добавлен internal Open-Meteo geocoding client, DTOs и MockEngine tests на path/query params + JSON decoding. |
| D5-006 | tests | Покрыть маппинг geocoding DTO в domain-модель города. | Есть тесты на полный ответ, пустой список и отсутствующие optional-поля. | Weather data tests | done | 164f335 | 5m08s | Добавлен `WeatherCityCandidate`, internal geocoding mapper и tests на полный/пустой/missing optional response. |
| D5-007 | feature | Добавить UI-события и state для ввода города в поле поиска. | На экране есть поле ввода; пустой submit не запускает запрос. | Weather ui tests | done | e298a94 | 5m46s | Добавлены `cityQuery`, события ввода/search submit, поле ввода на экране и тест blank submit no-op. |
| D5-008 | feature | Загружать и показывать погоду для введенного города. | После ввода города экран показывает погоду выбранного города, а не текущей локации. | ViewModel tests and ui compile | done | c2968dc | 6m21s | Non-blank city submit searches candidates, takes first city and loads weather via selected location; app DI wired. |
| D5-009 | feature | Добавить табы приложения `Карта` / `Список`. | Есть два таба; текущий weather flow находится во вкладке `Список`; `Карта` показывает placeholder. | App/ui compile | done | 6d88321 | 3m30s | Добавлены app tabs `Карта` / `Список`; weather flow остался во вкладке `Список`, `Карта` показывает placeholder. |
| D5-010 | feature | Добавить погодные иконки из Compose Icons для солнца, облачности, ветра и осадков. | UI-модель содержит тип иконки; экран показывает соответствующий `Icon`. | Mapper/ui tests | done | 4a93f65 | 6m57s | `WeatherUiModel` получил icon types; screen показывает Compose `Icon`, mapper/tests покрывают sunny/cloud/wind/precipitation. |
| D5-011 | docs | Обновить документацию по Open-Meteo attribution для forecast и geocoding. | Документация говорит, где видна атрибуция и что API key не используется. | Markdown-only | done | f9645db | 1m50s | `ai/testing/ui-smoke.md` уточняет forecast/geocoding attribution и отсутствие API key. |
| D5-012 | feature | Подключить SQLDelight и создать storage-слой истории введенных городов. | Есть schema/table и KMP wiring; UI пока не обязан использовать storage. | Storage compile/tests | done | 10b7d62 | 7m44s | SQLDelight подключен; добавлены `city_history_entry`, domain repository/entry, data impl и Android/iOS driver factories. |
| D5-013 | feature | Сохранять успешно введенные города в историю SQLDelight. | Успешно загруженный город сохраняется; повторный ввод не создает дубль. | Repository/usecase tests | done | 3044fc1 | 8m45s | Успешный selected-city weather load сохраняется в SQLDelight history; duplicate city сохраняется как одна recent entry. |
| D5-014 | feature | Показать историю введенных городов во вкладке `Список`. | Пользователь видит историю и может нажать город, чтобы снова загрузить погоду. | ViewModel tests and ui compile | done | f0d0918 | 7m31s | История показывается в weather list; click по entry грузит selected-city weather без current location/geocoding search. |
| D5-015 | feature | Добавить любимые города: лайк на экране погоды и блок избранного в списке. | Город можно добавить и убрать из избранного; состояние сохраняется; избранное видно в списке. | Storage tests and ViewModel tests | done | 5523740 | 14m08s | first pass no: subagent compile retry plus main-added domain tests; final checks green |
| D5-016 | tests | Добавить regression tests для сценариев permission denied, permanently denied и retry текущей локации после расширения city flow. | Старые сценарии геолокации не сломаны после добавления ручного выбора города. | Weather ui tests | done | 107e32a | 6m45s | first pass yes; final checks green |
| D5-017 | docs | Описать user-flow и smoke-сценарии для текущей локации, ввода города, истории, избранного и карты. | Документ содержит пользовательские пути и локальные проверки для smoke-сценариев. | Markdown-only | done | fb1e25a | 2m44s | first pass yes; markdown-only inspection |
| D5-018 | refactor | Вынести feature-owned `NavKey` для weather/list/map route вместо временного private `WeatherRoute`. | Route keys принадлежат feature/app по правилам AGENTS; serializers module явно агрегирует route serializers. | App compile | done | 310f78a | 3m44s | first pass no: invalid runtime artifact and compile fix; final checks green |
| D5-019 | feature | Добавить карту без API key через MapLibre Compose или iOS-only fallback, если Android не собирается без ключа. | Вкладка `Карта` показывает интерактивную карту; если Android-вариант без ключа не подтвержден, реализация ограничена iOS и это отражено в коде или документации. | iOS compile; Android compile if enabled | done | 0188d45 | 7m11s | first pass no: common MapLibre hit missing iOS `MapLibre.framework`; final Android MapLibre plus iOS/JVM no-key fallback green |
| D5-020 | feature | Выбирать точку на карте и загружать погоду по координатам. | Tap на карте обновляет выбранные координаты, reverse geocoding дает имя, weather flow показывает погоду для точки. | ViewModel/domain tests and platform compile | done | c016814 | 5m05s | first pass no: missing app import; final checks green |
