#!/usr/bin/env python3
"""Convert product-safety annotations into OpenAI chat fine-tuning JSONL."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


SYSTEM_PROMPT = (
    "Ты анализируешь состав продукта только по переданному справочнику добавок. "
    "Верни только валидный JSON без Markdown. Не выдумывай риски и добавки, которых нет во входе."
)


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


def additive_context(assistant: dict[str, object]) -> list[dict[str, object]]:
    context = []
    for additive in assistant["matched_additives"]:
        context.append(
            {
                "matched_text": additive["matched_text"],
                "canonical_name": additive["canonical_name"],
                "code": additive["code"],
                "risk_level": additive["risk_level"],
            }
        )
    return context


def user_content(annotation: dict[str, object]) -> str:
    assistant = annotation["assistant"]
    payload = {
        "product_name": annotation["product_name"],
        "composition": annotation["composition"],
        "reference_additives": additive_context(assistant),
    }
    return json.dumps(payload, ensure_ascii=False, sort_keys=True)


def message_row(annotation: dict[str, object]) -> dict[str, list[dict[str, str]]]:
    assistant_content = json.dumps(annotation["assistant"], ensure_ascii=False, sort_keys=True)
    return {
        "messages": [
            {
                "role": "system",
                "content": SYSTEM_PROMPT,
            },
            {
                "role": "user",
                "content": user_content(annotation),
            },
            {
                "role": "assistant",
                "content": assistant_content,
            },
        ]
    }


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as target:
        for row in rows:
            target.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            target.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--annotations", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    annotations = load_jsonl(args.annotations)
    rows = [message_row(annotation) for annotation in annotations]
    write_jsonl(args.output, rows)

    summary = {
        "annotations": str(args.annotations),
        "output": str(args.output),
        "rows": len(rows),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
