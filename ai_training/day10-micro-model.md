# Day 10. Local embeddings before LLM

## Цель

Day 10 отличается от Day 8. Day 8 маршрутизирует Cloud LLM `Flash` в Cloud LLM `Pro`.
Здесь micro-model — локальный embedding classifier; большая генеративная модель вызывается
только для неуверенных случаев.

Классифицируем только `risk_level`: `low`, `medium`, `high` или `unknown`. Полный Day 6 JSON
не строится: extraction, warnings и summary намеренно вне scope этого эксперимента.

## Pipeline

1. `nomic-embed-text` в локальном Ollama получает batch из 64 `train.jsonl` входов.
   В embedding text входят название, состав, code/matched text/canonical name справочника, но не
   переданный `risk_level`: classifier не получает target label во входе.
2. Индекс хранит embedding и эталонный `risk_level` только в памяти процесса.
3. Для каждого запроса выбирается nearest neighbor по cosine similarity. Micro-result содержит
   label, similarity score, margin до ближайшего другого класса и статус `OK` / `UNSURE`.
4. Пороги similarity и margin выбираются leave-one-out калибровкой только по train; принимается
   самый широкий gate с точностью не ниже 95%.
5. `OK` требует также совпадения embedding label с deterministic aggregate `risk_level` из
   переданного справочника. Это constraint: справочник — source of truth Day 6, а не скрытый
   target label; несовпадение остаётся `UNSURE`.
6. `UNSURE` или ошибка embedding-запроса один раз передаются в `deepseek-v4-pro`.
   Fallback обязан вернуть только `{"risk_level":"..."}`. Невалидный fallback — final reject.

Fine-tuning и `ollama create` не используются: Day 6 train — label index, не набор для обучения
весов.

## Набор

- 64 train-примера Day 6: только index и leave-one-out calibration.
- 16 eval-примеров Day 6: неизменённая проверка.
- [day10/supplemental.jsonl](day10/supplemental.jsonl): 14 независимых cases; шесть имеют
  `high` risk, также есть simple, boundary и complex OCR/alias cases.

Итоговый прогон содержит ровно 30 запросов. Eval и supplemental cases не участвуют в выборе
порогов.

## Запуск

Проверить локальную модель:

```bash
ollama list
```

`nomic-embed-text` должен присутствовать. DeepSeek key хранится в ignored `.keys.txt` как
`deepseek_api_key=...` либо в `DEEPSEEK_API_KEY`; его не печатать и не добавлять в Git.

```bash
./gradlew :ai:quality-cli:run --args='\
  --mode micro-routing \
  --train-dataset ai_training/dataset/train.jsonl \
  --dataset ai_training/dataset/eval.jsonl \
  --supplemental-dataset ai_training/day10/supplemental.jsonl \
  --embedding-model nomic-embed-text \
  --ollama-base-url http://127.0.0.1:11434 \
  --large-model deepseek-v4-pro \
  --micro-accuracy-target 0.95 \
  --output ai_training/day10/results/micro-routing-report.json'
```

Команда передаёт в DeepSeek только cases с `UNSURE` или ошибкой Ollama, расходует API balance и
создаёт ignored локальный report.

## Метрики и успех

JSON report содержит:

- `micro_accepted`, `fallback_calls`, `large_model_calls`;
- отдельную latency построения index и среднюю end-to-end latency запросов;
- точность принятых micro-model ответов и финальную точность;
- thresholds, leave-one-out accuracy и reason каждого fallback.

Success criterion: micro-model принимает больше половины 30 cases и сохраняет не менее 95%
точности `risk_level` среди принятых ответов.
