# Day 10. Итоги local embeddings before LLM

Дата прогона: 2026-08-01. Local micro-model: Ollama `nomic-embed-text`. Fallback: прямой
DeepSeek API, `deepseek-v4-pro`. Набор: 64 Day 6 train-примера для in-memory index и
leave-one-out calibration; 30 evaluation cases: 16 Day 6 eval + 14 supplemental.

Micro-result содержит nearest-neighbor `risk_level`, similarity и margin. `OK` требует
similarity не ниже калиброванного порога и совпадения label с deterministic aggregate
переданного справочника. Иначе один fallback в Pro. Полный product-safety JSON не
строится: сравнивается только `risk_level`.

Raw JSON report остаётся ignored:
`ai_training/day10/results/micro-routing-report.json`.

## Calibration

| Метрика | Значение |
|---|---:|
| Similarity threshold | 0.50 |
| Margin threshold | 0.00; diagnostic only |
| Leave-one-out accepted | 49 / 64 |
| Leave-one-out accuracy | 100% |
| Index build latency | 1.726 s |
| Index input tokens | 34,879 |

## Итог

| Метрика | Значение |
|---|---:|
| Кейсы | 30 |
| Приняты micro-model | 21 / 30 (70.0%) |
| Точность micro accepted | 21 / 21 (100%) |
| Fallback в Pro | 9 / 30 (30.0%) |
| Вызовы большой LLM | 9 |
| Final rejects | 0 |
| Финальная точность `risk_level` | 30 / 30 (100%) |
| Latency cases | 12.943 s total, 431.467 ms/case |
| Амортизированная latency с index build | 489.000 ms/case |
| DeepSeek tokens | 1,958 input + 54 output |
| DeepSeek cost | $0.000899 |

Все 9 fallback cases имели reason `micro_unsure`; ошибки Ollama, невалидного JSON или
final reject не было.

## Разрез сложности

| Сценарий | Кейсы | Micro accepted | Fallback | Final correct | Latency/case |
|---|---:|---:|---:|---:|---:|
| Simple | 21 | 17 | 4 | 21 | 295 ms |
| Boundary | 5 | 3 | 2 | 5 | 603 ms |
| Complex | 4 | 1 | 3 | 4 | 933 ms |

Micro-model уверенно отсекает простые inputs. Complex OCR/alias forms чаще
эскалируются в Pro: 3 из 4.

## Сравнение с Day 8

| Метрика | Day 8 | Day 10 | Изменение |
|---|---:|---:|---:|
| Кейсы | 37 | 30 | Наборы различаются |
| First-level accepted | 24 / 37 (64.9%) | 21 / 30 (70.0%) | +5.1 pp |
| Pro escalations | 13 / 37 (35.1%) | 9 / 30 (30.0%) | -5.1 pp |
| Cloud LLM calls | 50 | 9 Pro | -82.0% |
| Correct `risk_level` | 33 / 37 | 30 / 30 | Scope differs |
| Latency/case | 6.783 s | 0.431 s | -93.6% |
| Cloud cost | $0.020074 | $0.000899 | -95.5% |

Day 8 validates полный structured response: JSON envelope, matched additives, constraints,
confidence и final gate. Его `Flash` остаётся генеративной Cloud LLM и вызывается для каждого
case. Day 10 local-first: embeddings обрабатываются на машине, а Pro видит только 9
неуверенных inputs.

Поэтому `100%` Day 10 нельзя считать парной победой над Day 8: это точность одного
enum, дополнительно защищённого source-of-truth справочником. Он не измеряет
extraction additives, warnings, summary или schema compliance полного ответа.

## Вывод

Day 10 выполняет цель: большинство запросов (70%) завершилось без генеративной
LLM, при 100% точности принятых micro результатов. Для быстрой оценки `risk_level`
предпочтителен local embedding-first route. Для полного product-safety ответа остаётся
нужен Day 8 quality gate либо прямой Pro: Day 10 не заменяет его structured contract.
