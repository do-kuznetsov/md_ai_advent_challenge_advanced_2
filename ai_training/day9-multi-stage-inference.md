# День 9. Декомпозиция инференса

`ai:quality-cli` запускает два независимых режима для product-safety dataset Дня 6. Оба используют один и тот же `eval.jsonl`, набор сценариев, модель и цены. Это изолирует влияние multi-stage от model routing.

## Режимы

- `monolithic`: один запрос с `ProductInput`, один `ProductSafetyAssessment`.
- `multi-stage`: три коротких JSON-запроса: нормализация `reference_additives`, решение по риску, формирование `ProductSafetyAssessment`.

В multi-stage невалидный или не прошедший contract stage завершает кейс; последующие запросы не выполняются. Retry, self-check и model fallback не используются.

## Запуск

Ключ DeepSeek положить в ignored `.keys.txt` как `deepseek_api_key=...` или передать через `DEEPSEEK_API_KEY`. Перед внешним прогоном сверить актуальные тарифы и передать их флагами.

```bash
./gradlew :ai:quality-cli:run --args='\
  --mode monolithic \
  --dataset ai_training/dataset/eval.jsonl \
  --scenarios clean,boundary,noisy \
  --model deepseek-v4-flash \
  --input-price-per-million 0.14 \
  --output-price-per-million 0.28 \
  --output ai_training/day9/results/monolithic-report.json'
```

```bash
./gradlew :ai:quality-cli:run --args='\
  --mode multi-stage \
  --dataset ai_training/dataset/eval.jsonl \
  --scenarios clean,boundary,noisy \
  --model deepseek-v4-flash \
  --input-price-per-million 0.14 \
  --output-price-per-million 0.28 \
  --output ai_training/day9/results/multi-stage-report.json'
```

Не использовать `--checks`, `--max-attempts`, `--confidence-threshold`, `--small-*` или `--large-*`: это Day 7/8 механизмы, они запрещены для Day 9.

## Отчёты и сравнение

Оба отчёта имеют одну схему: config, total и scenario metrics, `byStage`, а также кейсы с parsed stage outputs, contract failures, latency, tokens, cost, risk-match и additive-code-match.

Сравнивать нужно отчёты с одинаковыми `dataset`, `scenarios`, `model`, input/output prices и limit. Для итоговой метрики использовать Day 6 `eval`; `train` не включать.
