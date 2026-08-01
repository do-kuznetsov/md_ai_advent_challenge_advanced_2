# День 9. Итоги monolithic и multi-stage inference

Дата прогона: 2026-08-01. Provider: прямой DeepSeek API. Dataset: `ai_training/dataset/eval.jsonl`; сценарии `clean`, `boundary`, `noisy`; всего 37 кейсов. Для обоих Day 9 режимов использованы `deepseek-v4-flash`, non-thinking JSON output, $0.14/1M input cache-miss и $0.28/1M output.

Полные JSON-отчёты внешних прогонов лежат локально в ignored `ai_training/day9/results/`.

## Итог

| Метрика | Monolithic | Multi-stage | Day 8 routing |
|---|---:|---:|---:|
| Кейсы | 37 | 37 | 37 |
| API calls | 37 | 111 | 50 |
| Accepted | 24 / 37 (64.9%) | 37 / 37 (100%) | 32 / 37 (86.5%) |
| Final risk совпал с эталоном | 24 / 37 | 37 / 37 | 33 / 37 |
| Additive codes совпали с эталоном | 32 / 37 | 37 / 37 | 35 / 37 |
| Total latency | 148.545 s | 302.717 s | 250.975 s |
| Latency / case | 4.015 s | 8.182 s | 6.783 s |
| Total tokens | 41,190 | 85,444 | 60,094 |
| Cost | $0.008300 | $0.015950 | $0.020074 |
| Cost / case | $0.000224 | $0.000431 | $0.000543 |

Multi-stage добавил 103.8% latency и 92.2% cost против monolithic. Day 8 routing дороже multi-stage на 25.9%, потому что 13 кейсов эскалировались с Flash на Pro.

## Multi-stage по стадиям

| Стадия | Calls | Contract valid | Latency | Tokens | Cost |
|---|---:|---:|---:|---:|---:|
| Нормализация | 37 | 37 / 37 | 83.877 s | 29,235 | $0.005310 |
| Решение по риску | 37 | 37 / 37 | 76.684 s | 17,188 | $0.002585 |
| Формирование результата | 37 | 37 / 37 | 142.156 s | 39,021 | $0.008055 |

Каждый кейс дошёл до третьей стадии. Финальные `risk_level` и additive codes совпали с эталонной Day 6 разметкой во всех 37 кейсах.

## Сценарии

| Сценарий | Monolithic | Multi-stage | Day 8 routing |
|---|---:|---:|---:|
| Clean, 16 кейсов | 11 accepted | 16 accepted | 14 accepted |
| Boundary, 5 кейсов | 2 accepted | 5 accepted | 4 accepted |
| Noisy, 16 кейсов | 11 accepted | 16 accepted | 14 accepted |

Monolithic чаще всего нарушал контракт на boundary: восемь раз вернул `warnings` при пустом `matched_additives`. В пяти кейсах добавил отсутствующие в `reference_additives` сущности; в трёх — нарушил aggregate risk. Multi-stage устранил эти нарушения за счёт детерминированной проверки каждого переходного контракта.

## Сравнение с Day 8

Day 8 — другой production-gate: Flash возвращает `AssessmentEnvelope` с scoring, constraints выполняются до принятия, а 13 сомнительных кейсов получают один fallback на `deepseek-v4-pro`. Поэтому сравнение с Day 9 операционное, не парный prompt benchmark.

- Multi-stage лучше routing по итоговой точности и acceptance: 37/37 против 32/37.
- Multi-stage дешевле routing: $0.015950 против $0.020074.
- Routing быстрее multi-stage: 6.783 s/case против 8.182 s/case.
- Для задачи, где нельзя принимать ответ с лишними или пропущенными добавками, multi-stage — лучший результат этого прогона. Для менее критичного потока monolithic остаётся самым дешёвым, но его 13 contract rejects требуют последующей обработки.
