# Day 13 — Stateful LLM Gateway

Локальный audit/demo gateway: Ktor/JVM проксирует non-streaming chat в DeepSeek, SQLDelight/SQLite хранит обе версии каждого turn, Compose HTML показывает original и modified представления в разных окнах.

## Запуск

Ключ читается из `DEEPSEEK_API_KEY`, `deepseek_api_key` или `.keys.txt`.

```bash
./gradlew :ai:llm-gateway:runJvm
```

Gateway слушает только `127.0.0.1:18090`. Основное окно: `http://127.0.0.1:18090/`. SQLite: `ai_training/day13/runtime/llm-gateway.sqlite`.

```bash
curl -sS http://127.0.0.1:18090/health

curl -sS http://127.0.0.1:18090/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Привет","input_guard_mode":"redact"}'

curl -sS http://127.0.0.1:18090/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"chat_id":"<id>","prompt":"Продолжай"}'

curl -sS http://127.0.0.1:18090/v1/chats/<id>
curl -sS -X DELETE http://127.0.0.1:18090/v1/chats/<id>
```

`BLOCK` и `REDACT` выбираются первым prompt и дальше immutable. Blocked prompt сохраняется с masked modified view, входит в лимит 50 и возвращает `422`; provider не вызывается. Rate limit: 10 `POST /v1/chat` за 60 секунд на direct IP.

## Data flow

```text
browser -> rate limit -> input guard -> SQLite PENDING/BLOCKED
                                  BLOCK -> 422
                                  REDACT/clean -> modified history -> DeepSeek
DeepSeek -> output guard -> SQLite original+modified -> browser
```

Основное окно читает только original messages. Icon-only `Outlined.BugReport` открывает named popup `/debug?chat_id=<id>`; popup readonly, читает только modified history и polling-обновления. Повторный click фокусирует окно того же chat.

Input Guard: OpenAI/GitHub/AWS keys, email, phone, Luhn-valid card, Base64 depth 1, split secret, Unicode NFKC. Output Guard дополнительно ловит per-chat canary, suspicious URL, pipe-to-shell, destructive/reverse-shell/exfiltration commands. Stdout не содержит raw prompt/response: только IDs, decisions, rule IDs, usage, cost, latency.

## Проверка

```bash
./gradlew :ai:llm-gateway:detekt \
  :ai:llm-gateway:jvmTest \
  :ai:llm-gateway:jsBrowserTest \
  :ai:llm-gateway:jsBrowserDistribution \
  :ai:llm-gateway:jvmJar

./gradlew :ai:llm-gateway:runJvm --args='--live-smoke'
```

`cases.json` фиксирует 15 deterministic cases. `guard-report.json` фиксирует пойманные правила и intentional clean controls. Live smoke реально поднимает HTTP gateway, вызывает `deepseek-v4-flash`, доказывает modified-only отправку, проверяет BLOCK без второго provider call и создаёт `live-smoke-report.json`, `audit-export.json`, runtime SQLite DB.

`browser-smoke-report.json` фиксирует DOM smoke main/debug окон, polling, unknown-chat error и icon-only contract. In-app browser блокирует создание popup; named `window.open`/focus reuse проверяются UI contract tests, а readonly debug view — во втором top-level tab.

## Ограничения

- Streaming и TTL отсутствуют намеренно.
- Rate limiter in-memory; restart сбрасывает окно.
- Base64 декодируется один раз и с bounded token regex; обфускации вне правил возможны.
- URL/command detection эвристический: false positive/negative допустимы для demo.
- Original input/output хранится plaintext и отдаётся main UI. Это audit/demo gateway, не production confidentiality boundary.
- Для production нужны encrypted storage, auth/RBAC, distributed limiter, migrations/retention, structured observability и key-management service.
