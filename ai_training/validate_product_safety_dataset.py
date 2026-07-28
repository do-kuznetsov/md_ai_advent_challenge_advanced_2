#!/usr/bin/env python3
"""Validate product-safety OpenAI chat fine-tuning JSONL files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


EXPECTED_ROLES = ["system", "user", "assistant"]
RISK_LEVELS = {"low", "medium", "high", "unknown"}
CONFIDENCE_LEVELS = {"low", "medium", "high"}
ASSISTANT_KEYS = {
    "risk_level",
    "matched_additives",
    "warnings",
    "safe_summary",
    "confidence",
}
ADDITIVE_KEYS = {
    "matched_text",
    "canonical_name",
    "code",
    "risk_level",
    "reason",
}


class ValidationError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def non_empty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate_additive(additive: object, location: str) -> None:
    require(isinstance(additive, dict), f"{location}: additive must be object")
    require(set(additive) == ADDITIVE_KEYS, f"{location}: additive keys mismatch")
    require(non_empty_string(additive["matched_text"]), f"{location}: empty matched_text")
    require(non_empty_string(additive["canonical_name"]), f"{location}: empty canonical_name")
    require(additive["code"] is None or non_empty_string(additive["code"]), f"{location}: invalid code")
    require(additive["risk_level"] in RISK_LEVELS, f"{location}: invalid risk_level")
    require(non_empty_string(additive["reason"]), f"{location}: empty reason")


def validate_assistant_content(content: str, location: str) -> dict[str, object]:
    try:
        payload = json.loads(content)
    except json.JSONDecodeError as error:
        raise ValidationError(f"{location}: assistant content is not valid JSON") from error

    require(isinstance(payload, dict), f"{location}: assistant content must be object")
    require(set(payload) == ASSISTANT_KEYS, f"{location}: assistant keys mismatch")
    require(payload["risk_level"] in RISK_LEVELS, f"{location}: invalid risk_level")
    require(payload["confidence"] in CONFIDENCE_LEVELS, f"{location}: invalid confidence")
    require(non_empty_string(payload["safe_summary"]), f"{location}: empty safe_summary")

    warnings = payload["warnings"]
    require(isinstance(warnings, list), f"{location}: warnings must be array")
    for index, warning in enumerate(warnings):
        require(non_empty_string(warning), f"{location}: empty warning at index {index}")

    additives = payload["matched_additives"]
    require(isinstance(additives, list), f"{location}: matched_additives must be array")
    for index, additive in enumerate(additives):
        validate_additive(additive, f"{location}: matched_additives[{index}]")

    if not additives:
        require(payload["risk_level"] in {"low", "unknown"}, f"{location}: empty additives with risky level")
        require(warnings == [], f"{location}: empty additives must have empty warnings")

    return payload


def validate_user_content(content: str, location: str) -> dict[str, object]:
    try:
        payload = json.loads(content)
    except json.JSONDecodeError as error:
        raise ValidationError(f"{location}: user content is not valid JSON") from error

    require(isinstance(payload, dict), f"{location}: user content must be object")
    require(non_empty_string(payload.get("composition")), f"{location}: empty composition")
    require("product_name" in payload, f"{location}: missing product_name")
    require(isinstance(payload.get("reference_additives"), list), f"{location}: reference_additives must be array")
    return payload


def validate_file(path: Path) -> dict[str, object]:
    seen_user_content = set()
    risk_counts: dict[str, int] = {}
    rows = 0

    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                raise ValidationError(f"{path}:{line_number}: empty line")
            try:
                row = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValidationError(f"{path}:{line_number}: invalid JSON") from error

            location = f"{path}:{line_number}"
            require(isinstance(row, dict), f"{location}: row must be object")
            require(set(row) == {"messages"}, f"{location}: row must contain only messages")
            messages = row["messages"]
            require(isinstance(messages, list), f"{location}: messages must be array")
            require(len(messages) == 3, f"{location}: messages length must be 3")
            roles = [message.get("role") if isinstance(message, dict) else None for message in messages]
            require(roles == EXPECTED_ROLES, f"{location}: roles must be {EXPECTED_ROLES}")

            for index, message in enumerate(messages):
                require(isinstance(message, dict), f"{location}: message {index} must be object")
                require(set(message) == {"role", "content"}, f"{location}: message {index} keys mismatch")
                require(non_empty_string(message["content"]), f"{location}: message {index} empty content")

            user_payload = validate_user_content(messages[1]["content"], location)
            assistant_payload = validate_assistant_content(messages[2]["content"], location)

            user_key = json.dumps(user_payload, ensure_ascii=False, sort_keys=True)
            require(user_key not in seen_user_content, f"{location}: duplicate user content")
            seen_user_content.add(user_key)

            risk = str(assistant_payload["risk_level"])
            risk_counts[risk] = risk_counts.get(risk, 0) + 1
            rows += 1

    return {
        "file": str(path),
        "rows": rows,
        "risk_counts": dict(sorted(risk_counts.items())),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("files", nargs="+", type=Path)
    args = parser.parse_args()

    summaries = [validate_file(path) for path in args.files]
    print(json.dumps({"valid": True, "files": summaries}, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
