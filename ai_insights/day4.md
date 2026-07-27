# Day 4: Local Boost

Цель дня: подключить локальную LLM как код-ассистент в IDE, дать ей правила проекта из Дня 1, проверить на тех же типах задач, что облачного ассистента, и честно зафиксировать границы применимости.

## Итоговая связка

Использована связка:

- IDE: VSCode;
- extension: Continue, `Continue - open-source AI code agent`;
- локальный runtime: Ollama;
- autocomplete model: `qwen2.5-coder:1.5b`;
- chat/edit model: `qwen2.5-coder:14b`;
- железо: MacBook Pro, Apple M2 Max, 12 CPU cores, 32 GB RAM.

VSCode выбран как основной вариант, потому что Continue лучше всего поддерживает сценарий chat + edit + autocomplete именно там. Android Studio можно проверить отдельно через JetBrains plugin, но для первого стабильного прохода VSCode проще. Xcode для этого задания хуже подходит: ожидаемая связка локального chat/autocomplete code-assistant там менее прямолинейная.

## Настройка Continue

Базовая настройка в `~/.continue/config.yaml`:

```yaml
name: Weather Local
version: 1.0.0
schema: v1

models:
  - name: Qwen2.5 Coder 1.5B Autocomplete
    provider: ollama
    model: qwen2.5-coder:1.5b
    roles:
      - autocomplete
    autocompleteOptions:
      maxPromptTokens: 2048
      debounceDelay: 250
      onlyMyCode: true
      useImports: true
      useRecentlyEdited: true
      useRecentlyOpened: true
    defaultCompletionOptions:
      temperature: 0.1
      topP: 0.9
      keepAlive: 1800

  - name: Qwen2.5 Coder 14B Chat
    provider: ollama
    model: qwen2.5-coder:14b
    roles:
      - chat
      - edit
      - apply
    defaultCompletionOptions:
      contextLength: 16384
      maxTokens: 2048
      temperature: 0.2
      topP: 0.9
      keepAlive: 1800

context:
  - provider: file
  - provider: code
  - provider: diff
  - provider: terminal
```

Правила проекта добавлены в `rules` как короткий системный контракт:

```yaml
rules:
  - |
    Follow project rules from /Users/do_kuznetsov/repos/SibGear/md_ai_advent_challenge_advanced_2/AGENTS.md.
    This is a Kotlin Multiplatform weather project.
    Use Kotlin Multiplatform, Compose Multiplatform, Android, iOS.
    Respect module dependency graph, UDF BaseViewModel pattern, manual DI, Navigation 3, visibility rules, naming rules, and verification checklist.
    Do not add storage, favorites, settings, maps, search, or new providers without explicit task.

    Important:
    - Answer as normal Markdown text.
    - Do not output JSON tool calls.
    - Do not call or imitate tools like read_file, read_skill, search, apply, edit, or shell.
    - If you need files, use only context already provided by Continue such as @Codebase, @File, @Git Diff.
    - For Research requests, investigate from provided context and answer with file paths and reasoning.
```

Дополнительный блок `Important` понадобился после первой неудачной попытки Research: модель начала печатать JSON с псевдо-вызовами `read_skill` и `read_file` вместо обычного ответа.

## Проверка режимов

Autocomplete:

- `qwen2.5-coder:1.5b` установлен через Ollama;
- Continue видит модель;
- inline autocomplete в Kotlin-файле появился;
- режим пригоден для коротких локальных дополнений кода.

Chat:

- `qwen2.5-coder:14b` установлен через Ollama;
- Continue видит модель;
- короткий тестовый prompt отработал;
- `@Codebase` смог найти `WeatherViewModel`;
- после уточнения rules Research-запрос стал отвечать обычным Markdown.

## Тест 1: задача на генерацию фичи

Prompt:

```text
@Codebase
Предложи минимальный план добавления новой user-visible фичи в этот Kotlin Multiplatform weather проект.
Соблюдай правила проекта из AGENTS.md.
Не меняй код.
Ответь: какие файлы создать/изменить, какие тесты добавить, какие Gradle task запустить.
```

Результат:

- с первого раза: да;
- модель дала адекватный план;
- не предложила запрещенные storage/settings/search/map;
- ориентировалась на KMP, feature modules, UDF/ViewModel, manual DI и тесты.

Вывод: для планирования небольшой фичи локальной модели достаточно, если дать `@Codebase` и короткие rules.

## Тест 2: Research / агентный сценарий

Prompt:

```text
@Codebase
Research: как в проекте устроена загрузка текущей погоды?
Не меняй код.
Ответь структурно:
1. Файлы
2. Поток данных
3. Где обработка ошибок
4. Какие тесты стоит проверить или добавить
```

Первая попытка:

- с первого раза: нет;
- модель вывела JSON с псевдо-вызовами `read_skill` и `read_file`;
- это не было настоящим выполнением инструментов, а текстовой имитацией tool calls.

Исправление:

- в rules добавлены запреты на JSON tool calls и имитацию инструментов;
- явно указано отвечать обычным Markdown;
- явно указано использовать только context от Continue: `@Codebase`, `@File`, `@Git Diff`.

Повторная попытка:

- после исправления: да;
- модель дала нормальный Markdown-ответ по Research-сценарию.

Вывод: локальная модель может работать в research-режиме, но ей нужен более жесткий prompt boundary, чем облачному агенту. Иначе она склонна имитировать инструменты, которых у нее нет.

## Сравнение

| Критерий | Облачный ассистент | Локальная модель |
|---|---|---|
| Качество кода | Выше для сложных изменений, лучше держит архитектуру и ограничения | Достаточно для маленьких правок, шаблонного Kotlin-кода и первичного плана |
| Скорость ответа | Стабильная, но зависит от сети и сервиса | Быстро для autocomplete на `1.5b`; chat на `14b` медленнее, но приемлемо на M2 Max |
| Понимание контекста | Лучше работает с длинным контекстом, tool calls, diff, logs, проверками | Работает, если явно дать `@Codebase` и узкий prompt; хуже переносит сложные правила |
| Агентность | Может реально читать файлы, запускать команды, править код, проверять результат | В Continue локально скорее assistant/edit mode; настоящую агентность легко спутать с имитацией |
| Работа без интернета | Нет, кроме кэшированных локальных частей | Да, если Ollama-модели уже скачаны |
| Стоимость | Платный сервис или лимиты | После скачивания моделей локально бесплатно, но тратит ресурсы Mac |
| Надежность правил | Лучше соблюдает многоступенчатые инструкции | Нужны короткие, жесткие, повторяемые правила и запреты |

## Для чего локальной модели достаточно

Локальная модель подходит для:

- autocomplete в текущем файле;
- небольших Kotlin-функций и мапперов;
- первичного плана фичи;
- объяснения конкретного файла;
- простого refactor suggestion;
- офлайн-работы без доступа к облаку;
- черновиков тестов, если затем руками проверить и прогнать Gradle.

## Где облако пока незаменимо

Облачный ассистент лучше для:

- multi-file архитектурных изменений;
- bugfix с логами, Gradle, iOS/Android запуском и повторной проверкой;
- строгого соблюдения `AGENTS.md` во всех деталях;
- работы с dirty Git tree и staged/unstaged границами;
- настоящего agent flow с чтением файлов, запуском команд и безопасным применением diff;
- задач, где важен надежный first-pass результат.

## Рекомендация

Для этого проекта лучшая стартовая локальная связка:

- VSCode + Continue + Ollama;
- `qwen2.5-coder:1.5b` для autocomplete;
- `qwen2.5-coder:14b` для chat/edit;
- `contextLength: 16384`;
- `temperature: 0.1` для autocomplete;
- `temperature: 0.2` для chat/edit;
- правила проекта держать короткими и жесткими, с явным запретом на JSON tool calls.

Локальную модель стоит использовать как быстрый офлайн-помощник и autocomplete. Для production-правок в этом KMP-проекте лучше оставлять облачного агента финальным исполнителем и проверяющим.
