# Day 8. Итоги routing Flash to Pro

Дата прогона: 2026-08-01. Provider: прямой DeepSeek API. Модели: `deepseek-v4-flash` и `deepseek-v4-pro`. Dataset: `ai_training/dataset/eval.jsonl`; `--limit 16`; сценарии `clean`, `boundary`, `noisy`; всего 37 кейсов. Routing rule: JSON + constraints + `status=OK` + `confidence_score >= 0.75`; иначе один переход на Pro. `FAIL` — reject.

Полный внешний JSON-отчёт лежит локально в ignored `ai_training/day8/results/routing-report.json`.

## Итог

| Метрика | Значение |
|---|---:|
| Кейсы | 37 |
| Приняты Flash | 24 |
| Эскалированы на Pro | 13 |
| Приняты Pro | 8 |
| Отклонены final gate | 5 |
| API calls | 50: 37 Flash + 13 Pro |
| Latency | 250.975 s total, 6.783 s/case |
| Дополнительная latency routing | 105.858 s |
| Cost | $0.020074 total |
| Дополнительный cost routing | $0.011816 |
| Final candidate: совпал risk | 33 / 37 |
| Final candidate: совпали additive codes | 35 / 37 |

32 из 37 кейсов (`86.5%`) прошли final gate. Pro восстановил 8 из 13 эскалаций (`61.5%`).

## Разрез сценариев

| Сценарий | Flash accepted | Escalated | Pro accepted | Rejected | Latency | Cost |
|---|---:|---:|---:|---:|---:|---:|
| Clean | 10 | 6 | 4 | 2 | 126.471 s | $0.010297 |
| Boundary | 3 | 2 | 1 | 1 | 17.783 s | $0.001308 |
| Noisy | 11 | 5 | 3 | 2 | 106.721 s | $0.008468 |

## Почему Flash эскалировался

| Кейс | Trigger | Почему Flash не прошёл gate | Pro outcome |
|---|---|---|---|
| `1-clean` | `constraints_failed` | Добавил `E420`, `E955`, `E170`; продублировал `E965`. | Исправил список, accepted. |
| `3-clean` | `constraints_failed` | Добавил отсутствующие в reference `E420`, `E950`. | Повторил extras, добавил `E170`, заменил `E472` на `E472a`; rejected. |
| `8-clean` | `constraints_failed` | Пропустил `E1400`; top-level risk `low` при matched `E470` с risk `unknown`. | Вернул `E1400`, но сохранил top-level `low`; rejected. |
| `9-clean` | `invalid_envelope` | Ответ не распарсился как требуемый JSON-envelope. | Валидный JSON и constraints, accepted. |
| `11-clean` | `confidence_below_threshold` | Список additives был корректен, но score `0.70 < 0.75`; это policy escalation, не доказанная semantic error. | Score `0.90`, accepted. |
| `12-clean` | `constraints_failed` | Добавил не заданные reference сущности `Куркумин` и `Кармин`. | Точный список, accepted. |
| `8-boundary` | `constraints_failed` | Тот же reference-case: пропущен `E1400`, risk `low` вместо aggregate `unknown`. | Не исправил aggregate risk; rejected. |
| `16-boundary` | `constraints_failed` | При пустом `matched_additives` вернул non-empty warnings. | Empty warnings, risk `low`, score `1.0`; accepted. |
| `3-noisy` | `constraints_failed` | Добавил `E420`, `E950` вне reference. | Точный список, accepted. |
| `4-noisy` | `confidence_below_threshold` | Структура прошла constraints, но score `0.70`; policy escalation. | Score `0.95`, accepted. |
| `8-noisy` | `constraints_failed` | Пропустил `E1400`. | Тоже пропустил `E1400`; rejected. |
| `11-noisy` | `invalid_envelope` | Ответ Flash не распарсился как envelope. | Валидный JSON и constraints, accepted. |
| `16-noisy` | `constraints_failed` | При пустом списке добавил warnings. | Убрал warnings, но score остался `0.0`; rejected по confidence threshold. |

## Вывод

Routing выполняет задачу: 24 простых кейса остались на Flash, 13 сомнительных ушли на Pro, из них 8 восстановлены.

Самый важный результат: score не годится единственной эвристикой. Большинство ошибок Flash пришли с `0.90–0.95`, но constraints обнаружили лишние или пропущенные additives. Два кейса с корректной структурой получили routing только из-за score `0.70`; это цена выбранного conservative threshold.

Пять rejected кейсов полезны: router не принял формально уверенные ответы с неполной или неверной reference-мэппингом. Следующая итерация может добавить repair prompt для Pro или отдельный deterministic mapper для `E1400` и alias `E472`/`E472a`; текущий прогон намеренно не делал третьего model call.
