# Day 5: Local Model Execution Loop Attempt

Цель проверки: понять, можно ли заставить локальную модель `qwen2.5-coder:14b` в VSCode + Continue не просто отвечать в chat/edit-режиме, а реально начать агентный прогон по `ai/execution-loop/README.md` и backlog из `ai/execution-loop/day5-task-pool.md`.

## Setup

Использован тот же локальный стек, что в Day 4:

- IDE: VSCode;
- extension: Continue;
- runtime: Ollama;
- autocomplete model: `qwen2.5-coder:1.5b`;
- agent/chat model: `qwen2.5-coder:14b`;
- режим Continue: `Agent`;
- repository: `/Users/do_kuznetsov/repos/SibGear/md_ai_advent_challenge_advanced_2`.

Для Agent-режима конфигурация была уточнена:

- исправлен YAML indentation в `~/.continue/config.yaml`;
- для `qwen2.5-coder:14b` добавлено `capabilities: [tool_use]`;
- старый запрет из Day 4 `Do not call or imitate tools ... shell` убран из agent-сценария;
- включена настройка Continue `Only use system message tools`;
- из `baseAgentSystemMessage` убрана фраза `If tools are unavailable, say "Agent tools unavailable" and stop`, потому что модель начала использовать ее как легальный fallback вместо попытки вызвать tools.

## Что получилось

### 1. Agent mode заработал только после system message tools

Первый smoke prompt:

```text
Run terminal command `pwd`, then stop. Do not print JSON tool calls as text.
```

До включения `Only use system message tools` модель печатала сырой JSON:

```json
{
  "name": "run_terminal_command",
  "arguments": {
    "command": "pwd"
  }
}
```

После включения `Only use system message tools` Continue начал показывать реальную terminal tool card, команда `pwd` выполнилась и вернула путь репозитория.

### 2. Terminal tools стали пригодны для простых проверок

Модель смогла выполнить через Continue Agent:

```text
git rev-parse --short HEAD
git status --short
rg --files | rg 'CurrentLocationProvider|OpenMeteoApi|...'
sed -n '1,260p' <file>
```

Это значит, что локальную модель можно использовать для простых evidence-запросов, если заставлять ее работать через terminal-only protocol.

### 3. File edit smoke test прошел

Модель смогла создать файл `ai/execution-loop/.continue-agent-smoke.md`, прочитать его обратно и подтвердить совпадение содержимого.

После этого файл был удален через Continue Agent, `git status --short` снова стал чистым.

### 4. Pre-run gate можно проверить, но только механическим prompt

При первой проверке backlog модель нашла 20 задач, но ошибочно сказала, что gate не готов, потому что "contains only 20 tasks", хотя диапазон был `15-20`.

После жесткого механического prompt:

```text
task count N satisfies 15 <= N <= 20
```

модель правильно ответила:

```text
TASK_COUNT=20
COUNT_READY=yes
MISSING_FIELDS=none
NON_TODO=none
EXTERNAL_BLOCKERS=none
PRE_RUN_GATE_READY=yes
```

Вывод: простая арифметика и checklist должны быть явно разложены на машинные условия. Иначе модель делает неверный вывод даже при правильно найденных данных.

## Что не получилось

### 1. Полный Codex Execution Loop не стартует надежно

Оригинальный Day 5 prompt требует:

- проверить pre-run gate;
- зафиксировать baseline;
- брать todo-задачи по очереди;
- выбирать режим Bug Fix / Research / task-executor;
- автономно менять код;
- делать отдельные коммиты;
- обновлять task pool и run log;
- останавливаться только по failure condition.

Для Continue + локальной `qwen2.5-coder:14b` это слишком длинный и слишком агентный контракт. Модель не владеет Codex-профилями, subagents, безопасным Git scope и loop-state так же, как облачный Codex.

### 2. Research dry-run дал слишком общий результат

На D5-001 модель смогла ответить в chat, но результат был слабым:

- использовала placeholders вроде `[current timestamp]` и `[HEAD commit hash]`;
- давала укороченные или неточные пути;
- смешивала verified facts и общие предположения;
- заявляла platform-specific детали без чтения platform files.

После уточнения "strict evidence" в новом session модель вернула `not verified` почти для всего и завершила ответ фразой `Agent tools unavailable`, хотя отдельный `pwd` tool call в том же session работал.

### 3. Модель плохо держит порядок "сначала tools, потом answer"

Даже когда prompt требовал выполнить `sed` и только потом отвечать, модель сначала написала анализ общими словами, а затем уже выполнила terminal commands.

Рабочий обходной путь: двухфазный протокол.

1. Prompt 1: только собрать evidence через terminal tools и сказать `EVIDENCE_DONE`.
2. Prompt 2: суммаризировать только по terminal outputs выше.

Для автономного execution loop такой ручной двухфазный режим противоречит цели "без вмешательства пользователя".

### 4. Контекст быстро забивается

Continue показал `99% of context filled` уже во время диагностических попыток. Для длинного Day 5 loop это критично: модель начинает забывать ранние правила, теряет ordering constraints и чаще подставляет placeholders.

## Можно ли рассчитывать на выполнение задач из списка

Да, но только на очень ограниченный класс задач и не в режиме полного автономного loop.

Локальная модель может быть полезна для:

- autocomplete в Kotlin-файлах;
- коротких research-запросов по заранее найденным файлам;
- terminal-only evidence сборки;
- простых Markdown-only задач;
- маленьких однофайловых правок после человеческого review;
- черновиков тестов или mapper-кода, если человек затем проверяет diff и запускает Gradle.

Из Day 5 backlog локально реалистичны только задачи уровня:

- D5-001, если разбить на "собери evidence" и "суммаризируй evidence";
- D5-002 / D5-011 / D5-017, если это Markdown-only и результат проверяет человек;
- небольшие mapper/test задачи вроде D5-006, но не автономно и не с коммитом без review.

Нельзя надежно рассчитывать на:

- полный прогон 15-20 задач подряд;
- безопасное обновление task pool и run log после каждой задачи;
- самостоятельный выбор Bug Fix / Research / task-executor;
- multi-file production changes;
- Gradle retry loop;
- отдельные коммиты на каждую изменяющую задачу;
- строгую защиту staged/unstaged scope;
- сложный хвост задач D5-012-D5-020 без постоянного человеческого контроля.

## Итог

VSCode + Continue + Ollama + `qwen2.5-coder:14b` можно довести до состояния "инструменты вызываются", если включить `Only use system message tools` и убрать конфликтные anti-tool rules. Это делает локальную модель пригодной как быстрый локальный assistant для маленьких задач.

Но как автономный Codex Execution Loop runner она пока ненадежна. Главные причины:

- нестабильный tool protocol без system-message fallback;
- слабая дисциплина evidence-first;
- плохое удержание многошагового loop-контракта;
- контекст быстро забивается;
- модель путает verified facts, placeholders и предположения;
- Continue Agent не равен Codex profiles/subagents.

Практическая рекомендация: использовать локальную модель как вспомогательный слой для autocomplete, поиска черновых идей и маленьких terminal-only исследований. Для Day 5 execution loop, production-правок, коммитов и Gradle verification оставлять облачный Codex основным исполнителем.
