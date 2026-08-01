# Day 7. Confidence QA CLI

`ai:quality-cli` проверяет ответы модели DeepSeek для product-safety dataset Дня 6. CLI не использует fine-tuning и не выполняет запросы без явного запуска команды.

## Запуск

Ключ положить в ignored `.keys.txt`:

```text
deepseek_api_key=...
```

Или передать через `DEEPSEEK_API_KEY`.

```bash
./gradlew :ai:quality-cli:run --args='\
  --dataset ai_training/dataset/eval.jsonl \
  --checks self-check,constraints,scoring \
  --scenarios clean,boundary,noisy \
  --model deepseek-v4-flash \
  --confidence-threshold 0.75 \
  --max-attempts 2 \
  --limit 16 \
  --input-price-per-million 0.14 \
  --output-price-per-million 0.28 \
  --output ai_training/day7/results/quality-report.json'
```

## Проверки

- `self-check`: второй запрос сверяет кандидат с `reference_additives` и возвращает `OK`, `UNSURE` или `FAIL` с краткими нарушениями.
- `constraints`: CLI проверяет JSON-envelope, допустимые значения, полное соответствие reference-добавкам, отсутствие дублей, aggregate risk и warnings.
- `scoring`: первичный ответ содержит `confidence_score` от `0` до `1` и `status`. Кандидат с низким score или `UNSURE` повторяется до лимита.

`FAIL` прекращает обработку кейса. Ошибочный JSON, constraints failure, низкий score и `UNSURE` требуют повторного primary inference.

## Сценарии и отчёт

- `clean`: все строки выбранного JSONL.
- `boundary`: строки без reference-добавок или с эталонным `unknown` risk.
- `noisy`: in-memory OCR-подобное изменение `composition`; `reference_additives` и эталон остаются прежними.

`--limit` ограничивает число исходных строк до разворачивания сценариев. Использовать для preflight и контроля расхода.

Отчёт содержит каждую попытку, verdict, причины reject, совпадение с эталоном, latency, input/output tokens, расчёт cost, accepted/rejected/retried и дополнительную цену/latency относительно первого primary-вызова. Результаты по умолчанию ignored, потому что могут содержать данные внешнего инференса.

Тарифы задаются флагами и записываются в отчёт. Значения по умолчанию относятся к `deepseek-v4-flash` и cache-miss input; перед реальным замером сверить их с текущим тарифом DeepSeek.
