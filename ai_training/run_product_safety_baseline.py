#!/usr/bin/env python3
"""Run baseline responses for product-safety eval examples."""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path


OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"


def load_jsonl(path: Path) -> list[dict[str, object]]:
    rows = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as error:
                raise ValueError(f"{path}:{line_number}: invalid JSONL line") from error
    return rows


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as target:
        for row in rows:
            target.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            target.write("\n")


def openai_chat_completion(api_key: str, model: str, messages: list[dict[str, str]], timeout: int) -> dict[str, object]:
    payload = {
        "model": model,
        "messages": messages,
        "temperature": 0,
        "response_format": {
            "type": "json_object",
        },
    }
    request = urllib.request.Request(
        OPENAI_CHAT_COMPLETIONS_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI API HTTP {error.code}: {body}") from error


def baseline_row(
    index: int,
    source_row: dict[str, object],
    model: str,
    response: dict[str, object],
) -> dict[str, object]:
    messages = source_row["messages"]
    expected = json.loads(messages[2]["content"])
    choices = response.get("choices") or []
    content = ""
    if choices:
        content = choices[0].get("message", {}).get("content", "")

    parsed_content = None
    parse_error = None
    try:
        parsed_content = json.loads(content)
    except json.JSONDecodeError as error:
        parse_error = str(error)

    return {
        "index": index,
        "model": model,
        "input": json.loads(messages[1]["content"]),
        "expected": expected,
        "baseline_content": content,
        "baseline_json": parsed_content,
        "baseline_json_error": parse_error,
        "response_id": response.get("id"),
        "usage": response.get("usage"),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--eval", type=Path, default=Path("ai_training/dataset/eval.jsonl"))
    parser.add_argument("--output", type=Path, default=Path("ai_training/baseline/baseline-responses.jsonl"))
    parser.add_argument("--model", default="gpt-4o-mini")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--sleep-seconds", type=float, default=0.5)
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is required to run baseline.")

    eval_rows = load_jsonl(args.eval)[: args.limit]
    baseline_rows = []
    for index, row in enumerate(eval_rows, start=1):
        messages = row["messages"][:2]
        response = openai_chat_completion(api_key, args.model, messages, args.timeout)
        baseline_rows.append(baseline_row(index, row, args.model, response))
        time.sleep(args.sleep_seconds)

    write_jsonl(args.output, baseline_rows)
    print(
        json.dumps(
            {
                "eval": str(args.eval),
                "output": str(args.output),
                "model": args.model,
                "rows": len(baseline_rows),
            },
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
