#!/usr/bin/env python3
"""Extract candidate product-composition examples from a phpMyAdmin SQL dump."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from statistics import median


TARGET_TABLES = {
    "e_additive_danger",
    "e_additive_name",
    "products_composition",
    "products_name",
}

NON_FOOD_NAME_KEYWORDS = (
    "бумага",
    "влажные салфетки",
    "гель для",
    "губк",
    "дезодорант",
    "зубная",
    "крем для",
    "мыло",
    "пакеты",
    "паста зубная",
    "пеленки",
    "порошок",
    "прокладки",
    "салфетки",
    "средство для",
    "туалетная",
    "хозяйствен",
    "шампунь",
)


def iter_sql_statements(path: Path):
    buffer: list[str] = []
    in_string = False
    escaped = False

    with path.open("r", encoding="utf-8", errors="replace") as source:
        for line in source:
            for char in line:
                buffer.append(char)

                if in_string:
                    if escaped:
                        escaped = False
                    elif char == "\\":
                        escaped = True
                    elif char == "'":
                        in_string = False
                    continue

                if char == "'":
                    in_string = True
                elif char == ";":
                    yield "".join(buffer)
                    buffer = []

    if buffer:
        yield "".join(buffer)


def split_tuple_values(statement: str) -> list[list[str | None]]:
    values_index = statement.find(" VALUES")
    if values_index == -1:
        return []

    payload = statement[values_index + len(" VALUES") :]
    rows: list[list[str | None]] = []
    row: list[str | None] = []
    value: list[str] = []
    depth = 0
    in_string = False
    escaped = False
    quoted = False

    for char in payload:
        if in_string:
            if escaped:
                value.append({"n": "\n", "r": "\r", "t": "\t"}.get(char, char))
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "'":
                in_string = False
            else:
                value.append(char)
            continue

        if char == "'":
            in_string = True
            quoted = True
        elif char == "(":
            if depth == 0:
                row = []
                value = []
                quoted = False
            else:
                value.append(char)
            depth += 1
        elif char == ")" and depth > 0:
            depth -= 1
            if depth == 0:
                row.append(parsed_value(value, quoted))
                rows.append(row)
                value = []
                quoted = False
            else:
                value.append(char)
        elif char == "," and depth == 1:
            row.append(parsed_value(value, quoted))
            value = []
            quoted = False
        elif depth >= 1:
            value.append(char)

    return rows


def parsed_value(value: list[str], quoted: bool) -> str | None:
    raw = "".join(value).strip()
    if not quoted and raw.upper() == "NULL":
        return None
    return raw


def parse_insert(statement: str) -> tuple[str, list[dict[str, str | None]]] | None:
    match = re.search(r"INSERT INTO `([^`]+)` \((.*?)\) VALUES", statement, re.S)
    if not match:
        return None

    table = match.group(1)
    if table not in TARGET_TABLES:
        return None

    columns = [column.strip().strip("`") for column in match.group(2).split(",")]
    rows = []
    for values in split_tuple_values(statement):
        if len(values) == len(columns):
            rows.append(dict(zip(columns, values)))
    return table, rows


def load_tables(path: Path) -> dict[str, list[dict[str, str | None]]]:
    tables = {table: [] for table in TARGET_TABLES}
    for statement in iter_sql_statements(path):
        parsed = parse_insert(statement)
        if parsed is None:
            continue
        table, rows = parsed
        tables[table].extend(rows)
    return tables


def normalize_text(value: str | None) -> str:
    return " ".join((value or "").split())


def is_likely_food_product(name: str | None) -> bool:
    normalized = normalize_text(name).lower().replace("ё", "е")
    return not any(keyword in normalized for keyword in NON_FOOD_NAME_KEYWORDS)


def normalize_code(value: str | None) -> str:
    return (value or "").strip().upper().replace("Е", "E")


def risk_level(danger: int | None) -> str:
    if danger is None:
        return "unknown"
    if danger >= 4:
        return "high"
    if danger >= 2:
        return "medium"
    return "low"


def build_additives(tables: dict[str, list[dict[str, str | None]]]) -> dict[str, dict[str, object]]:
    danger_by_code = {
        normalize_code(row.get("e_additive")): int(row["danger"])
        for row in tables["e_additive_danger"]
        if row.get("e_additive") and str(row.get("danger", "")).isdigit()
    }
    additives = {}
    for row in tables["e_additive_name"]:
        code = normalize_code(row.get("e_additive"))
        if not code:
            continue
        danger = danger_by_code.get(code)
        additives[code] = {
            "code": code,
            "name": normalize_text(row.get("name")),
            "danger": danger,
            "risk_level": risk_level(danger),
        }
    return additives


def find_matches(composition: str, additives: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    normalized = composition.lower().replace("ё", "е")
    matches: dict[str, dict[str, object]] = {}

    for raw_match in re.finditer(r"(?iu)[eе]\s*-?\s*(\d{3,4})", composition):
        code = f"E{raw_match.group(1)}"
        additive = additives.get(code)
        if additive is not None:
            matches[code] = {
                "matched_text": raw_match.group(0),
                **additive,
            }

    for code, additive in additives.items():
        name = str(additive.get("name") or "").lower().replace("ё", "е")
        if len(name) < 5:
            continue
        if name in normalized and code not in matches:
            matches[code] = {
                "matched_text": additive["name"],
                **additive,
            }

    return sorted(matches.values(), key=lambda item: str(item["code"]))


def select_candidates(
    tables: dict[str, list[dict[str, str | None]]],
    limit: int,
    min_without_matches: int,
    min_composition_length: int,
    max_composition_length: int,
) -> list[dict[str, object]]:
    additives = build_additives(tables)
    names_by_barcode: dict[str, str] = {}
    for row in tables["products_name"]:
        barcode = normalize_text(row.get("barcode"))
        name = normalize_text(row.get("name"))
        if barcode and name and barcode not in names_by_barcode:
            names_by_barcode[barcode] = name

    candidates = []
    seen_barcodes = set()
    seen_compositions = set()
    for row in tables["products_composition"]:
        barcode = normalize_text(row.get("barcode"))
        composition = normalize_text(row.get("composition"))
        product_name = names_by_barcode.get(barcode)
        if not barcode or not composition or barcode in seen_barcodes:
            continue
        composition_key = composition.lower().replace("ё", "е")
        if composition_key in seen_compositions:
            continue
        if not (min_composition_length <= len(composition) <= max_composition_length):
            continue
        if not is_likely_food_product(product_name):
            continue

        seen_barcodes.add(barcode)
        seen_compositions.add(composition_key)
        matches = find_matches(composition, additives)
        candidates.append(
            {
                "barcode": barcode,
                "product_name": product_name,
                "composition": composition,
                "composition_length": len(composition),
                "matched_additives": matches,
            }
        )

    with_matches = [item for item in candidates if item["matched_additives"]]
    without_matches = [item for item in candidates if not item["matched_additives"]]

    with_matches.sort(
        key=lambda item: (
            -len(item["matched_additives"]),
            item["product_name"] is None,
            item["composition_length"],
            item["barcode"],
        )
    )
    without_matches.sort(
        key=lambda item: (
            item["product_name"] is None,
            item["composition_length"],
            item["barcode"],
        )
    )

    selected_without_matches = without_matches[: min(min_without_matches, limit)]
    selected_with_matches = with_matches[: limit - len(selected_without_matches)]
    return selected_with_matches + selected_without_matches


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as target:
        for row in rows:
            target.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            target.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int, default=80)
    parser.add_argument("--min-without-matches", type=int, default=16)
    parser.add_argument("--min-composition-length", type=int, default=8)
    parser.add_argument("--max-composition-length", type=int, default=1500)
    args = parser.parse_args()

    tables = load_tables(args.source)
    candidates = select_candidates(
        tables,
        args.limit,
        args.min_without_matches,
        args.min_composition_length,
        args.max_composition_length,
    )
    lengths = [int(row["composition_length"]) for row in candidates]
    write_jsonl(args.output, candidates)

    summary = {
        "source": str(args.source),
        "output": str(args.output),
        "tables": {table: len(rows) for table, rows in sorted(tables.items())},
        "candidates": len(candidates),
        "candidates_with_matched_additives": sum(1 for row in candidates if row["matched_additives"]),
        "composition_length_median": int(median(lengths)) if lengths else 0,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
