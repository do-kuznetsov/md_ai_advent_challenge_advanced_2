# Day 6. Product Safety Dataset

## Что сделано

Для задания Дня 6 выбрана задача: по сырому составу продукта подготовить оценку потенциальной безопасности на основе справочника пищевых добавок.

Тип задачи: `extraction + classification + structured generation`.

Подготовлены:

- постановка задачи: [day6-product-safety.md](day6-product-safety.md);
- JSON Schema целевого ответа: [product-safety-response-schema.json](product-safety-response-schema.json);
- описание исходных SQL dump-ов и выбранного источника: [data-sources.md](data-sources.md);
- скрипты извлечения, разметки, сборки JSONL, split-а и валидации;
- полный JSONL dataset на 80 примеров;
- train/eval split: 64/16.

Исходные SQL dump-ы лежат в `ai_training/origin/` и не должны попадать в Git. Для этого в `.gitignore` есть правило:

```gitignore
ai_training/origin/*.sql
```

## Источник данных

Для дальнейшей работы выбран production dump:

```text
ai_training/origin/u0492853_enot_db.sql
```

Причина:

- `products_composition`: 914 непустых составов;
- `products_name`: 4591 строк;
- `e_additive_name`: 218 строк;
- `e_additive_danger`: 218 строк;
- таблицы и колонки совместимы с test dump;
- test dump остается вторичным источником для проверки extraction logic.

## Pipeline

### 1. Извлечь кандидатов

```bash
python3 ai_training/extract_product_safety_candidates.py \
  --source ai_training/origin/u0492853_enot_db.sql \
  --output ai_training/candidates/product-safety-candidates.jsonl \
  --limit 80 \
  --min-without-matches 16 \
  --min-composition-length 8 \
  --max-composition-length 1500
```

Результат:

- 80 кандидатов;
- 64 состава с найденными добавками;
- 16 составов без совпадений со справочником;
- пустые, дубли, слишком короткие/длинные и явные non-food товары отфильтрованы.

### 2. Собрать черновую разметку

```bash
python3 ai_training/build_product_safety_annotations.py \
  --candidates ai_training/candidates/product-safety-candidates.jsonl \
  --output ai_training/annotations/product-safety-annotations.jsonl
```

Текущий risk split:

- `high`: 14;
- `medium`: 49;
- `low`: 16;
- `unknown`: 1.

Разметка детерминированная: risk считается из `danger` score справочника, summary и warning формируются шаблонами.

### 3. Собрать OpenAI chat JSONL

```bash
python3 ai_training/build_product_safety_messages.py \
  --annotations ai_training/annotations/product-safety-annotations.jsonl \
  --output ai_training/dataset/product-safety-full.jsonl
```

Каждая строка имеет формат:

```json
{"messages":[{"role":"system","content":"..."},{"role":"user","content":"..."},{"role":"assistant","content":"..."}]}
```

`assistant.content` — строка с JSON-объектом по schema из `product-safety-response-schema.json`.

### 4. Разделить train/eval

```bash
python3 ai_training/split_product_safety_dataset.py \
  --input ai_training/dataset/product-safety-full.jsonl \
  --train-output ai_training/dataset/train.jsonl \
  --eval-output ai_training/dataset/eval.jsonl \
  --eval-every 5
```

Результат:

- `ai_training/dataset/train.jsonl`: 64 строки;
- `ai_training/dataset/eval.jsonl`: 16 строк.

### 5. Провалидировать dataset

```bash
python3 ai_training/validate_product_safety_dataset.py \
  ai_training/dataset/product-safety-full.jsonl \
  ai_training/dataset/train.jsonl \
  ai_training/dataset/eval.jsonl
```

Валидатор проверяет:

- каждая строка — валидный JSON;
- есть только поле `messages`;
- роли ровно `system`, `user`, `assistant`;
- нет пустых `content`;
- `user.content` парсится как JSON;
- `assistant.content` парсится как JSON;
- обязательные поля ответа на месте;
- enum-значения `risk_level` и `confidence` корректны;
- нет дублей `user.content`.

Текущий результат: valid.

## Артефакты

Рабочие данные:

- [candidates/product-safety-candidates.jsonl](candidates/product-safety-candidates.jsonl);
- [annotations/product-safety-annotations.jsonl](annotations/product-safety-annotations.jsonl);
- [dataset/product-safety-full.jsonl](dataset/product-safety-full.jsonl);
- [dataset/train.jsonl](dataset/train.jsonl);
- [dataset/eval.jsonl](dataset/eval.jsonl).

Скрипты:

- [extract_product_safety_candidates.py](extract_product_safety_candidates.py);
- [build_product_safety_annotations.py](build_product_safety_annotations.py);
- [build_product_safety_messages.py](build_product_safety_messages.py);
- [split_product_safety_dataset.py](split_product_safety_dataset.py);
- [validate_product_safety_dataset.py](validate_product_safety_dataset.py).
- [run_product_safety_baseline.py](run_product_safety_baseline.py).
- [run_product_safety_fine_tune.py](run_product_safety_fine_tune.py).

Day 10:

- [day10-micro-model.md](day10-micro-model.md);
- [day10-run-report.md](day10-run-report.md);
- [day10/supplemental.jsonl](day10/supplemental.jsonl).

## Baseline

Нужно взять 10 примеров из `ai_training/dataset/eval.jsonl`, прогнать через базовую модель `openai/gpt-4o-mini` через OpenRouter без fine-tune и сохранить ответы как точку отсчета.

Ключ OpenRouter хранится локально в `.keys.txt`; файл игнорируется Git. Runner читает `OPENROUTER_API_KEY` или `openrouter_ai_key` из env или `.keys.txt`.

Команда:

```bash
python3 ai_training/run_product_safety_baseline.py \
  --eval ai_training/dataset/eval.jsonl \
  --output ai_training/baseline/baseline-responses.jsonl \
  --model openai/gpt-4o-mini \
  --limit 10
```

Результат:

- [baseline/baseline-responses.jsonl](baseline/baseline-responses.jsonl);
- 10 независимых запросов;
- valid JSON: `10/10`;
- exact target schema: `0/10`.

Критерии оценки зафиксированы в [evaluation-criteria.md](evaluation-criteria.md).

## Fine-Tune Client

Fine-tune client подготовлен для OpenAI API, но по умолчанию работает в dry-run режиме и не запускает job.

Dry-run:

```bash
python3 ai_training/run_product_safety_fine_tune.py \
  --train ai_training/dataset/train.jsonl \
  --eval ai_training/dataset/eval.jsonl \
  --model gpt-4o-mini \
  --suffix product-safety-day6
```

Что сделает реальный запуск с `--execute`:

1. Провалидирует `train.jsonl` и `eval.jsonl`.
2. Загрузит оба файла в OpenAI Files API с `purpose=fine-tune`.
3. Создаст fine-tuning job для `gpt-4o-mini`.
4. Будет polling-ом ждать terminal status.
5. Сохранит job state в `ai_training/fine_tune/fine-tune-job.json`.

Не запускай `--execute`, пока не принято отдельное решение о старте fine-tune job.

## Следующий шаг

Следующий пункт задания: собрать итоговый список артефактов и проверить, что все требуемые файлы есть.
