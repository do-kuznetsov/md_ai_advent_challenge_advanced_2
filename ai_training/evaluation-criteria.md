# Product Safety Evaluation Criteria

## Goal

Compare baseline and later fine-tuned model outputs on the same 10 eval examples.

The model is better when it follows the target schema more reliably, preserves additive extraction from the provided reference context, chooses the expected risk level, and avoids unsupported claims.

## Metrics

### 1. Format Validity

Expected:

- response is valid JSON;
- top-level keys exactly match `product-safety-response-schema.json`;
- `risk_level` is one of `low`, `medium`, `high`, `unknown`;
- `confidence` is one of `low`, `medium`, `high`;
- `matched_additives` and `warnings` are arrays;
- no Markdown or prose outside JSON.

Primary target after fine-tune: `10/10`.

### 2. Additive Recall

Expected:

- all additive codes from `expected.matched_additives` are present in model `matched_additives`;
- aliases and noisy spelling may differ, but canonical code must match.

Primary target after fine-tune: no missing medium/high additives.

### 3. Additive Precision

Expected:

- model does not add codes absent from `user.reference_additives`;
- no unsupported additives from model memory.

Primary target after fine-tune: no hallucinated medium/high additives.

### 4. Risk Accuracy

Expected:

- model top-level `risk_level` matches expected top-level `risk_level`;
- if the model disagrees, the reason must be traceable to provided reference context.

Primary target after fine-tune: `>= 8/10`.

### 5. Warning Quality

Expected:

- warnings mention high/medium risk additives when present;
- no warning when `matched_additives` is empty;
- warning text is short and user-facing.

Primary target after fine-tune: warnings are useful and proportional in `>= 8/10` examples.

### 6. Summary Quality

Expected:

- `safe_summary` is short;
- it does not overstate medical certainty;
- it does not claim product is absolutely safe;
- it clearly distinguishes no-match cases from risky cases.

Primary target after fine-tune: acceptable summary in `>= 8/10` examples.

### 7. No-Hallucination Rule

Expected:

- model uses only additives supplied in `user.reference_additives`;
- model does not invent extra health claims;
- model does not treat absent additives as present.

Primary target after fine-tune: `10/10`.

## Baseline Snapshot

Baseline file:

```text
ai_training/baseline/baseline-responses.jsonl
```

Model:

```text
openai/gpt-4o-mini
```

Provider:

```text
OpenRouter
```

Baseline run size: 10 eval examples.

Observed baseline result:

- valid JSON responses: `10/10`;
- exact target schema responses: `0/10`;
- top-level `risk_level` comparable to expected: `0/10`;
- total tokens reported by OpenRouter: `9133`.

Initial conclusion: baseline model returns JSON, but does not reliably follow the target Day 6 response contract. Main expected fine-tune improvement is schema discipline plus stable use of `risk_level`, `matched_additives`, `warnings`, `safe_summary`, and `confidence`.
