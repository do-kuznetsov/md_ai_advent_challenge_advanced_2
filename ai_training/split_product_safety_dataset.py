#!/usr/bin/env python3
"""Split product-safety chat JSONL into deterministic train/eval files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--train-output", type=Path, required=True)
    parser.add_argument("--eval-output", type=Path, required=True)
    parser.add_argument("--eval-every", type=int, default=5)
    args = parser.parse_args()

    rows = load_jsonl(args.input)
    train_rows = []
    eval_rows = []
    for index, row in enumerate(rows, start=1):
        if index % args.eval_every == 0:
            eval_rows.append(row)
        else:
            train_rows.append(row)

    write_jsonl(args.train_output, train_rows)
    write_jsonl(args.eval_output, eval_rows)

    summary = {
        "input": str(args.input),
        "train_output": str(args.train_output),
        "eval_output": str(args.eval_output),
        "total_rows": len(rows),
        "train_rows": len(train_rows),
        "eval_rows": len(eval_rows),
        "eval_every": args.eval_every,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
