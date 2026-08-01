# Day 7. Итоги Confidence QA

Дата прогона: 2026-08-01. Модель: `deepseek-v4-flash`, thinking disabled. Датасет: `ai_training/dataset/eval.jsonl` (16 строк), сценарии: `clean=16`, `boundary=5`, `noisy=16`; всего 37 кейсов. Тариф расчёта: input `$0.14` и output `$0.28` за 1M tokens.

Каждый метод запускался независимо. JSON-отчёты лежат локально в ignored `ai_training/day7/results/`.

## Итоговая таблица

| Подход | Корректно обработано / всего | Отклонено | Повторы | API calls | Latency, total / case | Cost | Совпали risk / additive codes |
|---|---:|---:|---:|---:|---:|---:|---:|
| Constraint-based | 35 / 37 | 2 | 10 | 47 primary | 184.09 s / 4.98 s | $0.01062 | 34 / 35 |
| Scoring | 37 / 37 | 0 | 5 | 42 primary | 161.91 s / 4.38 s | $0.00964 | 34 / 32 |
| Self-check | 37 / 37 | 0 | 1 | 38 primary + 37 verifier | 196.67 s / 5.32 s | $0.01455 | 32 / 32 |

`Корректно обработано` означает accepted решением конкретного gate. Последние две колонки — отдельное сравнение accepted-кандидата с эталоном Дня 6; они показывают, что accepted не равен semantic correctness.

## Разрез сценариев

| Подход | Clean | Boundary | Noisy |
|---|---:|---:|---:|
| Constraint-based | 15 / 16 accepted | 4 / 5 accepted | 16 / 16 accepted |
| Scoring | 16 / 16 accepted | 5 / 5 accepted | 16 / 16 accepted |
| Self-check | 16 / 16 accepted | 5 / 5 accepted | 16 / 16 accepted |

## Ошибки и объяснение

| Где | Наблюдение | Чем объясняется |
|---|---|---|
| Constraint-based, `8-clean` и `8-boundary` | Отвергнуты: отсутствовал `E1400`, хотя остальные reference-добавки, включая `E470`, найдены. | Модель извлекала добавки из состава и пропустила нормализованный `E1400` (`Декстрин`), который задан reference context. Строгая проверка не дала превратить неполное извлечение в accepted ответ. |
| Scoring | 5 accepted ответов имели несовпадение risk или списка additive codes. | Высокий `confidence_score` — самооценка модели, не доказательство полноты extraction. Подход не сверяет ответ с reference-истиной. |
| Self-check | 5 accepted ответов имели несовпадение risk или списка additive codes. | Verifier использует ту же модель и похожий контекст. Он хорошо ловит форматные сомнения, но может согласиться с ошибкой primary ответа. |
| Все подходы | Повторные запросы: constraints 10, scoring 5, self-check 1. | Причины: отсутствующее поле `confidence`, неверная полнота `matched_additives`, несогласованный `risk_level`, warnings при пустом списке, низкий `confidence_score` или `UNSURE`. Repair prompt исправлял часть таких ответов. |

## Рекомендации

- **Constraint-based**: использовать для критичных решений, извлечения из известного справочника и API/DB workflows. Он единственный в эксперименте отсеял неполный список добавок. Цена: больше retry и немного выше latency, чем scoring.
- **Scoring**: использовать как дешёвый UX-signal — показать пользователю `UNSURE`, направить на ручную проверку, решить делать ли retry. Не использовать как единственный gate, когда ошибка недопустима: 37/37 accepted скрыли 5 semantic mismatches.
- **Self-check**: применять после constraints для неоднозначных текстов, свободных summaries и случаев без алгоритмической истины. Он дороже на 51% относительно scoring и не независим от primary модели, поэтому сам по себе не даёт гарантии.
- **Production default**: `constraints + scoring`, затем self-check только для `UNSURE`, спорного `risk_level` или запросов с высоким impact. Это оставляет deterministic reference checks обязательными и ограничивает cost verifier-вызовов.

## Ограничения

До валидных экспериментальных серий были исправлены integration проблемы CLI: рабочий каталог Gradle, nullable `product_name`, API response с top-level `id`, nested JSON schema и нормализация `matched_text`. Предыдущие невалидные запросы не включены в таблицы: они не распарсили модельный ответ и не измеряют выбранные QA approaches.
