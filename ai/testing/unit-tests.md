# Unit Tests Playbook

## Purpose

Reusable Codex playbook for Day 3, Level 1: unit and integration tests for business logic.

This playbook tells an agent how to:

1. Inspect current unit-test coverage state.
2. Find uncovered business-logic gaps.
3. Add deterministic tests in a later implementation task.
4. Run relevant Gradle checks.
5. Measure code coverage only when a real coverage report exists.
6. Produce a concise report.

This file is documentation only. Do not add, edit, or delete production source or test source while creating or updating this playbook.

## Current Coverage Baseline

Status: measured with Kover.

Current measured line coverage: `53.6458%`.

Measurement command:

```bash
./gradlew koverXmlReport koverLog --continue
```

Report path: `build/reports/kover/report.xml`.

Report-level line counter:

```xml
<counter type="LINE" missed="89" covered="103"/>
```

Measured modules:

- `feature:reverse-geocoding:domain`
- `feature:weather:domain`
- `feature:weather:data`
- `feature:weather:ui`

This baseline covers Kover-instrumented JVM execution of selected business-logic KMP modules. The project declares JVM targets for these modules and their required KMP dependencies so `commonTest` can execute on JVM and produce Kover counters. Kover does not collect coverage from Native/iOS tests, so iOS simulator test execution is outside this percentage.

Do not update this numeric coverage percentage unless it comes from a new generated coverage report. A missing, empty, or non-generated report is `not measured`, not a numeric value.

Target for future measured unit coverage: `60%` for business logic.

## Codex Prompt

Use this prompt in a fresh Codex task when implementing Level 1 unit tests:

```text
Implement Day 3 / Level 1 unit tests for this repository.

Goal:
- Find current unit-test gaps in business logic.
- Add deterministic unit/integration tests in at least 3 test files and at least 3 modules.
- Run relevant Gradle tests and static checks.
- Measure unit coverage only from a real generated coverage report.
- Produce a report with commands, results, coverage status, and remaining gaps.

Constraints:
- Inspect applicable AGENTS.md first.
- Preserve staged, unstaged, and untracked user changes.
- Use ast-index first for Kotlin symbols, implementations, usages, module map, and project conventions.
- Use rg/find for Gradle, Markdown, literal strings, and filesystem inventory.
- Do not use real network, real geocoder, emulator, or simulator for Level 1 tests.
- Prefer commonTest for portable KMP business logic.
- Use deterministic fakes and Ktor MockEngine where HTTP behavior must be tested.
- Do not report a numeric coverage percentage unless a real coverage report was generated and parsed.
- If no coverage report exists, report coverage as not measured and explain why.

Required result:
- 3+ test files across 3+ modules.
- First full test run result recorded honestly.
- Unit coverage target: 60% for business logic.
- Final report with baseline, selected gaps, added/changed tests, commands, coverage before/after or not measured, failures, and next actions.
```

## Scope Rules

Include:

- `domain` interactors and repositories.
- `data` repositories, DTO-to-domain mappers, API request construction, error mapping, fallback behavior.
- ViewModel/UDF state transitions and one-shot effects.
- Manual DI only when it has real construction or wiring risk.

Exclude from Level 1:

- Real network calls.
- Real Android `Geocoder` or iOS `CLGeocoder`.
- Emulator, simulator, device, and UI smoke flows.
- Platform APIs without a fakeable boundary.
- Pure Compose rendering checks. Those belong to UI-level testing unless state logic is isolated.

## Discovery Protocol

Start every implementation task with current checkout evidence:

```bash
git status --short
git diff --cached --name-status
git diff --name-status
rg --files -g 'AGENTS.md'
```

Then inspect code and test inventory:

```bash
ast-index rebuild
ast-index search Test
ast-index search Repository
ast-index search Interactor
ast-index search ViewModel
find feature core app -path '*/src/*Test/*' -type f | sort
rg -n "kotlin\\(\"test\"\\)|kotlinx-coroutines-test|ktor-client-mock|kover|jacoco|coverage" -g '*.kts' -g 'libs.versions.toml' -g '*.md'
./gradlew tasks --all --console=plain
```

If `ast-index` and filesystem disagree, trust files on disk after `ast-index rebuild`. Do not treat stale `build/` reports or deleted source files as current tests.

## Gap Selection

Choose gaps by risk, not by easy line count:

- Business branch with no direct test.
- Repository behavior that combines location, reverse geocoding, API, and mapper.
- Error or fallback path used by UI state.
- Public interactor contract with pass-through success/failure behavior.
- Mapper or DTO boundary where API naming differs from domain naming.

For this project shape, likely candidates are:

- `feature:weather:domain`: `GetCurrentWeatherInteractor`.
- `feature:weather:data`: `CurrentWeatherRepositoryImpl`, `OpenMeteoApi`, `WeatherDataDomainMapper`.
- `feature:weather:ui`: `WeatherViewModel`, `WeatherUiMapper`.
- `feature:reverse-geocoding:domain`: `ResolveCityNameInteractor`.

Before writing tests, verify candidates against current source. Do not assume these gaps still exist.

## Test Implementation Rules

Use these rules in the later test-writing task:

- Add or update at least 3 test files across at least 3 modules.
- Prefer `src/commonTest/kotlin` for business logic.
- Add test dependencies only to affected modules and only through `gradle/libs.versions.toml` when a new external dependency is unavoidable.
- Prefer `kotlin("test")`, `kotlinx-coroutines-test`, local fakes, and Ktor `MockEngine`.
- Keep tests deterministic: no clocks unless injected, no random values, no real IO, no real network, no device services.
- Test observable contracts: returned `Result`, mapped model, ViewModel `state`, emitted `effect`, request URL/parameters.
- Name tests after behavior, not implementation details.
- Keep fakes inside test files unless reused by several tests in one module.
- Do not add production seams only for coverage unless the seam also improves architecture.

## Coverage Protocol

Coverage must be measured, not guessed.

Configured tool for KMP unit coverage: Kover. Kover collects coverage from JVM/Android unit execution; Native/iOS tests are not included in the measured percentage. Before changing Kover setup, verify compatible plugin syntax and tasks from project configuration and official tool documentation. Do not invent task names.

Implementation task should:

1. Search existing coverage setup:

   ```bash
   rg -n "kover|jacoco|coverage|codeCoverage" -g '*.kts' -g 'libs.versions.toml' -g '*.md'
   ./gradlew tasks --all --console=plain | rg -i "kover|jacoco|coverage"
   ```

2. If KMP unit coverage setup is missing or broken, propose the smallest Kover setup as part of the implementation plan, then add it only when that task scope allows Gradle changes.
3. Run tests before coverage so failures are easier to diagnose.
4. Generate coverage report with discovered Kover tasks.
5. Parse the real report and record:
   - measured line coverage percent;
   - modules included;
   - modules excluded and why;
   - path to report;
   - whether target `60%` is met.

If a report is absent, empty, or not generated, write:

```text
Coverage: not measured
Reason: <why no real report exists>
Target: 60% business-logic unit coverage
```

Never write a numeric percentage from file counts, test counts, intuition, or AGP task names alone.

## Verification Commands

Discover exact tasks from Gradle before running them. For current KMP modules, expected relevant shape is:

```bash
./gradlew :feature:weather:domain:allTests :feature:weather:data:allTests :feature:weather:ui:allTests :feature:reverse-geocoding:domain:allTests --continue
./gradlew :feature:weather:domain:detekt :feature:weather:data:detekt :feature:weather:ui:detekt :feature:reverse-geocoding:domain:detekt --continue
```

If affected modules differ, adjust task list to those modules. Do not call checks green when any selected task fails.

Do not run emulator, simulator, Playwright MCP, or Claude in Mobile for Level 1. Those belong to Level 2 smoke testing.

## Report Template

Use this final report shape:

```markdown
## Unit Test Report

Baseline:
- Git status: <clean / dirty, with scoped paths>
- Existing test files: <count and key paths>
- Coverage before: <measured percent from report, or not measured with reason>

Selected gaps:
- <module>: <business behavior>

Changes:
- <test file>: <covered behavior>

Commands:
- `<command>` -> <passed/failed/skipped>

First-run result:
- <passed/failed>
- If failed: <failure summary and later fix>

Coverage:
- Before: <measured percent or not measured>
- After: <measured percent or not measured>
- Target: 60%
- Report path: <path or none>
- Included modules: <list>
- Excluded modules: <list with reasons>

Failures / risks:
- <items or none>

Next actions:
- <items or none>
```

## Acceptance Criteria

For this playbook task:

- `ai/testing/unit-tests.md` exists.
- File states this task is documentation only.
- File does not instruct the agent to write tests during playbook creation.
- Coverage baseline is `not measured` unless a real report exists.
- Current coverage is recorded as `53.6458%` only because `build/reports/kover/report.xml` was generated and parsed.
- Target coverage is `60%` for business logic.

For the later implementation task:

- 3+ test files across 3+ modules.
- Relevant Gradle test tasks pass or failures are reported exactly.
- Relevant `detekt` tasks pass or failures are reported exactly.
- Coverage percent is recorded only from a real generated report.
