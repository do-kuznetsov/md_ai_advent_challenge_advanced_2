#!/usr/bin/env python3
"""Prepare and optionally run OpenAI fine-tuning for product-safety dataset."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


OPENAI_FILES_URL = "https://api.openai.com/v1/files"
OPENAI_FINE_TUNING_JOBS_URL = "https://api.openai.com/v1/fine_tuning/jobs"
OPENAI_KEY_NAMES = ("OPENAI_API_KEY", "openai_api_key")
TERMINAL_STATUSES = {"succeeded", "failed", "cancelled"}


def load_keys_file(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}

    result = {}
    with path.open("r", encoding="utf-8", errors="replace") as source:
        for line in source:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if stripped.startswith("export "):
                stripped = stripped[len("export ") :].strip()
            if "=" in stripped:
                key, value = stripped.split("=", 1)
                result[key.strip()] = value.strip().strip('"').strip("'")
            elif stripped.startswith("sk-"):
                result["OPENAI_API_KEY"] = stripped
    return result


def openai_api_key(keys_file: Path) -> str | None:
    for key_name in OPENAI_KEY_NAMES:
        value = os.environ.get(key_name)
        if value:
            return value

    keys = load_keys_file(keys_file)
    for key_name in OPENAI_KEY_NAMES:
        value = keys.get(key_name)
        if value:
            return value

    return None


def request_json(url: str, api_key: str, payload: dict[str, object] | None = None) -> dict[str, object]:
    data = None
    method = "GET"
    headers = {
        "Authorization": f"Bearer {api_key}",
    }
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        method = "POST"
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI API HTTP {error.code}: {body}") from error


def upload_file(path: Path, api_key: str) -> dict[str, object]:
    boundary = f"----product-safety-{uuid.uuid4().hex}"
    file_bytes = path.read_bytes()
    parts = [
        (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="purpose"\r\n\r\n'
            "fine-tune\r\n"
        ).encode("utf-8"),
        (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
            "Content-Type: application/jsonl\r\n\r\n"
        ).encode("utf-8"),
        file_bytes,
        f"\r\n--{boundary}--\r\n".encode("utf-8"),
    ]
    body = b"".join(parts)
    request = urllib.request.Request(
        OPENAI_FILES_URL,
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI file upload HTTP {error.code}: {body}") from error


def create_fine_tune_job(
    api_key: str,
    model: str,
    training_file_id: str,
    validation_file_id: str | None,
    suffix: str,
) -> dict[str, object]:
    payload: dict[str, object] = {
        "model": model,
        "training_file": training_file_id,
        "suffix": suffix,
    }
    if validation_file_id:
        payload["validation_file"] = validation_file_id
    return request_json(OPENAI_FINE_TUNING_JOBS_URL, api_key, payload)


def retrieve_fine_tune_job(api_key: str, job_id: str) -> dict[str, object]:
    return request_json(f"{OPENAI_FINE_TUNING_JOBS_URL}/{job_id}", api_key)


def validate_dataset(train: Path, eval_file: Path) -> None:
    subprocess.run(
        [
            "python3",
            "ai_training/validate_product_safety_dataset.py",
            str(train),
            str(eval_file),
        ],
        check=True,
    )


def write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def dry_run_summary(args: argparse.Namespace) -> dict[str, object]:
    return {
        "execute": False,
        "will_validate": [
            str(args.train),
            str(args.eval),
        ],
        "will_upload": [
            {
                "path": str(args.train),
                "purpose": "fine-tune",
            },
            {
                "path": str(args.eval),
                "purpose": "fine-tune",
            },
        ],
        "will_create_fine_tuning_job": {
            "model": args.model,
            "suffix": args.suffix,
            "training_file": "<uploaded train file id>",
            "validation_file": "<uploaded eval file id>",
        },
        "will_poll_status": True,
        "output": str(args.output),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", type=Path, default=Path("ai_training/dataset/train.jsonl"))
    parser.add_argument("--eval", type=Path, default=Path("ai_training/dataset/eval.jsonl"))
    parser.add_argument("--model", default="gpt-4o-mini")
    parser.add_argument("--suffix", default="product-safety-day6")
    parser.add_argument("--keys-file", type=Path, default=Path(".keys.txt"))
    parser.add_argument("--output", type=Path, default=Path("ai_training/fine_tune/fine-tune-job.json"))
    parser.add_argument("--poll-interval-seconds", type=int, default=30)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()

    validate_dataset(args.train, args.eval)

    if not args.execute:
        print(json.dumps(dry_run_summary(args), ensure_ascii=False, indent=2, sort_keys=True))
        return

    api_key = openai_api_key(args.keys_file)
    if not api_key:
        raise SystemExit("OPENAI_API_KEY or openai_api_key is required to start fine-tuning.")

    train_file = upload_file(args.train, api_key)
    eval_file = upload_file(args.eval, api_key)
    job = create_fine_tune_job(
        api_key=api_key,
        model=args.model,
        training_file_id=str(train_file["id"]),
        validation_file_id=str(eval_file["id"]),
        suffix=args.suffix,
    )

    while str(job.get("status")) not in TERMINAL_STATUSES:
        write_json(
            args.output,
            {
                "train_file": train_file,
                "eval_file": eval_file,
                "job": job,
            },
        )
        time.sleep(args.poll_interval_seconds)
        job = retrieve_fine_tune_job(api_key, str(job["id"]))

    write_json(
        args.output,
        {
            "train_file": train_file,
            "eval_file": eval_file,
            "job": job,
        },
    )
    print(json.dumps({"output": str(args.output), "job_id": job.get("id"), "status": job.get("status")}, indent=2))


if __name__ == "__main__":
    main()
