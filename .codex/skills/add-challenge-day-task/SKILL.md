---
name: add-challenge-day-task
description: Add or update one new challenge-day assignment in a repository's tasks.md Markdown tracker. Use when a user asks to add a day's task description, append a new challenge day, or maintain the День, Тема, Что сделать, Проверка / сравнение, Ожидаемый результат table without changing implementation code.
---

# Add Challenge Day Task

Maintain one complete, verifiable assignment row. Change only `tasks.md` unless user explicitly expands scope.

## Inspect

1. Read applicable `AGENTS.md` rules.
2. Inspect `git status --short`, staged diff, and unstaged diff. Preserve their status and unrelated work.
3. Locate tracker with `rg --files -g 'tasks.md'`; read its table header and existing rows.
4. Find duplicate day numbers or a row matching requested theme before editing. If found, ask whether to update it instead of adding a duplicate.

## Resolve Missing Input

Require enough information for all five table cells:

- `День`: requested number; infer next number only when user asks for next day.
- `Тема`: concise topic name.
- `Что сделать`: concrete scope and constraints.
- `Проверка / сравнение`: observable validation or comparison.
- `Ожидаемый результат`: deliverables and success condition.

Ask one concise clarification when material input is missing. Do not invent product requirements, metrics, tools, services, or implementation work.

## Edit

1. Preserve existing header, column order, language, separator row, and surrounding formatting.
2. Add one physical Markdown table row in chronological order. Use next displayed row position only after confirming no duplicate.
3. Fill every cell with task-specific text. Keep scope, validation, and expected result distinct.
4. Escape literal `|` as `\|`; avoid line breaks inside cells.
5. Do not alter any other file, stage files, commit, or run build/test tasks for a Markdown-only row.

## Verify

1. Inspect `git diff --check -- tasks.md`.
2. Inspect `git diff -- tasks.md` and confirm one intended row only.
3. Inspect `git status --short` and distinguish pre-existing changes from this row.
4. Report day number, tracker path, validation commands and results, plus any missing runtime verification. State that Markdown-only validation does not prove future implementation works.
