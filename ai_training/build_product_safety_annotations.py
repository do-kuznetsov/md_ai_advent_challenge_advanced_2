#!/usr/bin/env python3
"""Build draft assistant annotations from extracted product-safety candidates."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


RISK_ORDER = {
    "low": 0,
    "unknown": 1,
    "medium": 2,
    "high": 3,
}


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


def aggregate_risk(additives: list[dict[str, object]]) -> str:
    if not additives:
        return "low"
    return max((str(additive["risk_level"]) for additive in additives), key=lambda risk: RISK_ORDER[risk])


def confidence(additives: list[dict[str, object]]) -> str:
    if not additives:
        return "medium"
    if any(additive.get("danger") is None for additive in additives):
        return "medium"
    return "high"


def reason(additive: dict[str, object]) -> str:
    additive_risk = str(additive["risk_level"])
    danger = additive.get("danger")
    if danger is None or additive_risk == "unknown":
        return "Добавка найдена в составе, но в справочнике нет числовой оценки опасности."
    if additive_risk == "high":
        return f"Добавка имеет высокий уровень риска по справочнику: danger={danger}."
    if additive_risk == "medium":
        return f"Добавка имеет средний уровень риска по справочнику: danger={danger}."
    return f"Добавка есть в справочнике, но отмечена как низкий риск: danger={danger}."


def warnings(risk_level: str, additives: list[dict[str, object]]) -> list[str]:
    if not additives:
        return []

    high = [additive for additive in additives if additive["risk_level"] == "high"]
    medium = [additive for additive in additives if additive["risk_level"] == "medium"]
    unknown = [additive for additive in additives if additive["risk_level"] == "unknown"]

    result = []
    if high:
        names = ", ".join(str(additive["canonical_name"]) for additive in high[:4])
        result.append(f"Найдены добавки с высоким уровнем риска: {names}.")
    if medium:
        names = ", ".join(str(additive["canonical_name"]) for additive in medium[:4])
        result.append(f"Найдены добавки со средним уровнем риска: {names}.")
    if unknown:
        names = ", ".join(str(additive["canonical_name"]) for additive in unknown[:4])
        result.append(f"По части добавок нет числовой оценки опасности в справочнике: {names}.")
    if risk_level == "low":
        result.append("Найдены только добавки с низким уровнем риска по справочнику.")
    return result


def safe_summary(risk_level: str, additives: list[dict[str, object]]) -> str:
    if not additives:
        return "По переданному справочнику опасные или спорные добавки в составе не найдены."
    if risk_level == "high":
        return "В составе есть добавки с высоким уровнем риска; продукт не стоит считать повседневно безопасным."
    if risk_level == "medium":
        return "В составе есть спорные добавки; продукт лучше употреблять осознанно и не делать его повседневным выбором."
    if risk_level == "unknown":
        return "В составе есть добавки без полной оценки в справочнике; для уверенного вывода нужны дополнительные данные."
    return "В составе найдены только добавки с низким уровнем риска по переданному справочнику."


def build_assistant(candidate: dict[str, object]) -> dict[str, object]:
    source_additives = list(candidate["matched_additives"])
    matched_additives = [
        {
            "matched_text": additive["matched_text"],
            "canonical_name": additive["name"],
            "code": additive["code"],
            "risk_level": additive["risk_level"],
            "reason": reason(additive),
        }
        for additive in source_additives
    ]
    risk_level = aggregate_risk(matched_additives)
    return {
        "risk_level": risk_level,
        "matched_additives": matched_additives,
        "warnings": warnings(risk_level, matched_additives),
        "safe_summary": safe_summary(risk_level, matched_additives),
        "confidence": confidence(source_additives),
    }


def build_annotation(candidate: dict[str, object]) -> dict[str, object]:
    return {
        "barcode": candidate["barcode"],
        "product_name": candidate["product_name"],
        "composition": candidate["composition"],
        "assistant": build_assistant(candidate),
    }


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as target:
        for row in rows:
            target.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            target.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    candidates = load_jsonl(args.candidates)
    annotations = [build_annotation(candidate) for candidate in candidates]
    write_jsonl(args.output, annotations)

    risk_counts: dict[str, int] = {}
    for row in annotations:
        risk = str(row["assistant"]["risk_level"])
        risk_counts[risk] = risk_counts.get(risk, 0) + 1

    summary = {
        "candidates": str(args.candidates),
        "output": str(args.output),
        "annotations": len(annotations),
        "risk_counts": dict(sorted(risk_counts.items())),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
