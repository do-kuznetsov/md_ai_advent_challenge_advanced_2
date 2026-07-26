# Day 2: оптимизация профиля Bug Fix

В этой ветке `ai/profile/bug-fix.md` был изменен не ради упрощения проверки дефектов, а ради устранения повторной работы между stage agents. Раньше profile допускал, что orchestrator сам исследует checkout и выполняет Git actions, а `execute` и `validate` могли повторно собирать и запускать один и тот же platform artifact. Это увеличивало время bugfix и число запусков simulator без добавления нового evidence.

## Новый ownership stages

Orchestrator теперь только хранит state machine, передает original bug report и artifacts, проверяет attempt/verdict/transition. Он не читает source или logs, не запускает `git`, Gradle, Xcode, emulator/simulator и не меняет checkout.

Техническая работа распределена так:

- `plan` read-only снимает immutable Git baseline, задает eligible paths и собирает baseline evidence;
- `execute` работает в disposable isolated workspace, выполняет red-to-green, compile/tests/detekt и создает patch плюс reusable build artifact;
- `validate` read-only использует тот же workspace и тот же artifact для независимого original-flow check;
- `done` единственный может применить validated patch к primary checkout, stage только session paths и создать commit.

До final `done` primary checkout не получает session changes. Перед apply `done` сверяет baseline `HEAD`, user dirty sets, path hashes и patch hash. Любое расхождение блокирует финализацию без overwrite user work.

## Evidence budget

Оптимизация формализирована через `Evidence budget` и `Build artifact`:

- baseline reproduction берется из deterministic failing test, свежего crash/log evidence или initial platform launch;
- existing log разрешен только при совпадении source, target, OS/device, configuration и reproduction conditions;
- если такого evidence нет, `plan` делает один initial launch;
- `execute` создает один post-fix platform build, но не запускает emulator/simulator;
- `validate` проверяет identity artifact и один раз повторяет original user flow без rebuild и повторного automated test suite.

Итог для platform defect: обычно один post-fix simulator launch; максимум два, если нужен свежий baseline reproduction. Post-fix build всегда один. Это сохраняет before/after evidence, но убирает duplicate post-fix checks.

## Почему качество не снижено

TDD, relevant compilation, neighboring tests, `detekt`, attempt limit и independent validation остались обязательными. Missing environment, stale artifact, conflict primary checkout или failed check не могут привести к `fixed`: flow возвращается в `plan`, запрашивает data или заканчивается `blocked`/`exhausted`. Artifact templates теперь содержат baseline snapshot, path eligibility, patch hash, build identity и no-rebuild confirmation, поэтому final Git action и validation можно проследить без повторного запуска тех же проверок.
