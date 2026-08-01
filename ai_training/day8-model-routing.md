# Day 8. Routing Flash to Pro

`ai:quality-cli` сохраняет Day 7 QA-режим по умолчанию. Режим `routing` запускает `deepseek-v4-flash`, проверяет JSON, constraints и scoring, затем при сомнительном результате один раз передаёт исходный запрос `deepseek-v4-pro`.

## Запуск

Ключ хранить в ignored `.keys.txt`:

```text
deepseek_api_key=...
```

```bash
./gradlew :ai:quality-cli:run --args='\
  --mode routing \
  --dataset ai_training/dataset/eval.jsonl \
  --checks constraints,scoring \
  --scenarios clean,boundary,noisy \
  --small-model deepseek-v4-flash \
  --large-model deepseek-v4-pro \
  --confidence-threshold 0.75 \
  --limit 16 \
  --small-input-price-per-million 0.14 \
  --small-output-price-per-million 0.28 \
  --large-input-price-per-million 0.435 \
  --large-output-price-per-million 0.87 \
  --output ai_training/day8/results/routing-report.json'
```

Команда отправляет dataset во внешний DeepSeek API и расходует баланс. Перед production-прогоном сверить текущие тарифы DeepSeek.

## Routing rule

Flash принимается, только когда JSON валиден, constraints пройдены, `status=OK` и `confidence_score >= 0.75`.

`UNSURE`, score ниже порога, malformed JSON или ошибка constraints эскалируют кейс на Pro. `FAIL` Flash сразу отклоняет кейс. Pro проходит те же проверки; его невалидный ответ — финальный reject. Больше двух model calls на один кейс routing не делает.

## Console and report

CLI пишет безопасные progress events: `flash.start`, `flash.accept`, `escalate`, `pro.start`, `pro.accept`, `reject` и итоговый `summary`. Events содержат только id кейса, этап и reason; API key, composition и полный ответ модели не выводятся.

JSON-отчёт содержит attempts с model id, ролью `small|large`, decision и reason codes. Summary фиксирует `acceptedOnSmall`, `escalatedToLarge`, `acceptedOnLarge`, `routingRejected`, tokens, latency и cost.

Reason codes: `primary_request_failed`, `invalid_envelope`, `status_fail`, `constraints_failed`, `status_unsure`, `confidence_below_threshold`.

Интерпретация серии: `acceptedOnSmall` — дешёвые простые кейсы; `acceptedOnLarge` — успешно эскалированные; `routingRejected` — ответы, не прошедшие final gate.
