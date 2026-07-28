# День 6. Итоговые артефакты

## Требуемый результат

Для Дня 6 нужны:

- датасет в формате JSONL: train + eval;
- скрипт валидации;
- 10 baseline-ответов;
- критерии оценки;
- клиент для загрузки fine-tune файлов, создания job и polling статуса.

## Файлы

### Датасет

- [dataset/train.jsonl](dataset/train.jsonl) - 64 train-примера.
- [dataset/eval.jsonl](dataset/eval.jsonl) - 16 eval-примеров.
- [dataset/product-safety-full.jsonl](dataset/product-safety-full.jsonl) - 80 примеров до split.

### Промежуточные данные

- [candidates/product-safety-candidates.jsonl](candidates/product-safety-candidates.jsonl) - извлеченные кандидаты продуктов.
- [annotations/product-safety-annotations.jsonl](annotations/product-safety-annotations.jsonl) - детерминированная черновая разметка.

### Валидация

- [validate_product_safety_dataset.py](validate_product_safety_dataset.py) - JSONL-валидатор.

Команда валидации:

```bash
python3 ai_training/validate_product_safety_dataset.py \
  ai_training/dataset/product-safety-full.jsonl \
  ai_training/dataset/train.jsonl \
  ai_training/dataset/eval.jsonl
```

Текущий результат:

- `product-safety-full.jsonl`: 80 строк.
- `train.jsonl`: 64 строки.
- `eval.jsonl`: 16 строк.
- статус: valid.

### Baseline

- [baseline/baseline-responses.jsonl](baseline/baseline-responses.jsonl) - 10 baseline-ответов.
- Провайдер: OpenRouter.
- Модель: `openai/gpt-4o-mini`.
- Запросы: 10 независимых chat completion запросов.

Наблюдаемый baseline:

- валидный JSON в ответе: 10/10.
- точное соответствие целевой schema: 0/10.
- всего токенов по данным OpenRouter: 9133.

### Критерии оценки

- [evaluation-criteria.md](evaluation-criteria.md)

Основные метрики:

- валидность формата;
- полнота найденных добавок;
- точность найденных добавок;
- точность уровня риска;
- качество предупреждений;
- качество итогового вывода;
- правило отсутствия галлюцинаций.

### Fine-Tune клиент

- [run_product_safety_fine_tune.py](run_product_safety_fine_tune.py)

Команда dry-run:

```bash
python3 ai_training/run_product_safety_fine_tune.py \
  --train ai_training/dataset/train.jsonl \
  --eval ai_training/dataset/eval.jsonl \
  --model gpt-4o-mini \
  --suffix product-safety-day6
```

Клиент подготовлен, но не запускает fine-tune job без флага `--execute`.
