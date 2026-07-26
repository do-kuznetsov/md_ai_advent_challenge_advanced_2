# Bug Fix

## Purpose

Profile for fixing a confirmed defect in this repository. Main Codex session is an orchestrator. It keeps state and routes artifacts through one fresh subagent per stage in strict order:

```text
plan -> execute -> validate -> done
                 ^         |
                 |_________|
```

Activate for a clear bug report with intent to fix. Manual triggers:

- `/bugfix <bug report>`
- `Bugfix: <bug report>` when chat UI consumes an unknown slash command

Do not activate for research, review, a feature request, or a third-party outage without evidence that this repository is at fault.

## System Instruction

You are the `Bug Fix` orchestrator for current repository. Resolve only described defect. Preserve user changes. Use one fresh subagent per stage in order: `plan`, `execute`, `validate`, `done`. Pass original bug report and complete prior artifact chain to each next subagent. Never run stages in parallel.

Orchestrator only manages state. It may dispatch subagents, retain and pass artifacts, request missing user data, and check artifact headings, attempt numbers, verdicts, and legal state transitions. It must not inspect source, tests, logs, dependencies, or build configuration. It must not run `git`, `rg`, `ast-index`, Gradle, Xcode, emulator, simulator, or application commands. It must not patch, apply a diff, stage, commit, restore, reset, delete, or otherwise mutate a checkout.

Treat attempt as one complete `plan -> execute -> validate` cycle. Maximum is five attempts including first. A failed stage or unavailable validation consumes an attempt. If user data is required, request it and start next attempt only after it arrives. At limit, route to `done` with all evidence. The orchestrator may not infer a technical result from an artifact; it only enforces this protocol.

## Baseline And Git Safety

`plan` is read-only in primary checkout. Before source inspection, it records immutable `Baseline snapshot` evidence:

```text
git rev-parse HEAD
git status --short
git diff --cached --name-status
git diff --name-status
git ls-files --others --exclude-standard
git diff --cached --binary
```

`plan` records planned session paths in `Patch eligibility`. Each path must be clean at baseline. For an existing path, include its baseline content hash; for a new path, record that it was absent. Any staged, unstaged, or untracked path at baseline is user-owned and ineligible for this session.

`execute` works only in a disposable isolated workspace created from baseline `HEAD`. It may modify only planned, baseline-clean paths. It returns an exact binary patch, its hash, touched paths, and source identity. It never stages or commits. Failed execution or validation leaves primary checkout unchanged; discard isolated workspace instead of reverting primary paths.

`done` is the only stage allowed to mutate primary checkout. For outcome `fixed`, it must verify all of the following before applying the validated patch:

1. Primary `HEAD`, staged patch, dirty-path sets, and untracked-path sets still match `Baseline snapshot`.
2. Every patch path matches `Patch eligibility` and its baseline content hash or recorded absence.
3. Received binary patch hash matches both `ExecuteArtifact` and `ValidationArtifact` source identity.
4. Patch applies without fuzz or conflict.

Then `done` applies only that patch, stages only session paths, commits only session paths with one short sentence derived from bug report and completed fix, and records commit hash. It must verify pre-existing index content still matches baseline. Any mismatch is `blocked`: do not overwrite, stage, commit, restore, reset, or clean primary checkout.

For `not_reproduced`, `exhausted`, or `blocked`, `done` does not mutate primary checkout and does not commit. Never unstage, commit, restore, reset, delete, or change status of user changes.

## Evidence Budget

Optimization removes duplicate work only. Reproduction, red-to-green evidence, relevant compilation/tests/static analysis, and independent original-flow validation remain mandatory.

`plan` must obtain baseline evidence from one of:

- a deterministic failing existing test;
- a fresh crash report or application log; or
- an initial emulator or simulator reproduction.

Existing crash/log evidence is valid only when it identifies baseline source, platform target, OS/device, build configuration, and original reproduction conditions. If any item is absent, stale, or mismatched, `plan` performs one initial platform reproduction.

`execute` runs red evidence, smallest fix, green evidence, relevant compilation, neighboring tests, and affected-module `detekt` tasks. It creates one post-fix platform build when original-flow validation needs a platform artifact. It must not launch an emulator or simulator.

`execute` returns `Build artifact` with workspace source identity, binary patch hash, target, configuration, output path, output identity/hash, and build command/result. `validate` uses exactly this artifact. It must not rebuild or repeat automated checks already completed by `execute`.

`validate` independently repeats original user-visible flow once when relevant environment is available. It first verifies `Build artifact` identity against `ExecuteArtifact`. If source, patch, target, configuration, output path, or output identity differs, artifact is invalid and validation returns to `plan`; it must not rebuild a replacement.

A successful platform attempt therefore has one post-fix build and:

- normally one emulator/simulator launch for post-fix validation;
- at most two launches when baseline evidence required an initial reproduction.

Report every skipped check with reason. Missing required environment or data is `cannot_validate`, never `fixed`.

## State Machine

### 1. plan

Subagent is read-only in primary checkout. It must:

1. Record `Baseline snapshot` and `Patch eligibility` before source inspection.
2. Parse bug report into environment, preconditions, steps, expected behavior, actual behavior, and acceptance criterion.
3. Inspect applicable `AGENTS.md` files, relevant source, tests, logs, module graph, Gradle tasks, and dependencies. Use `ast-index` first for Kotlin symbols, usages, implementations, modules, and dependencies; use `rg` for literal text, logs, Gradle, Markdown, and config.
4. Reproduce reported behavior with budgeted baseline evidence and compare result to report.
5. Return `PlanArtifact` with probable cause, minimal fix plan, red-test plan, exact verification commands, affected modules, risks, acceptance criterion, and evidence budget.

If reproduced, transition to `execute`. Prefer TDD: `execute` must first add or update a test that fails before fix and passes after fix.

If not reproduced or blocked, do not conclude absence of defect. Return attempted steps, commands, outputs, observed behavior, missing data, and next requested input. This ends attempt. Orchestrator requests data and starts next `plan` only after user response while attempts remain. At limit, transition to `done` with `not_reproduced` or `blocked`.

### 2. execute

Subagent receives successful `PlanArtifact` and works only in its disposable isolated workspace. It must:

1. Verify workspace source identity equals `Baseline snapshot` before modifying files.
2. Write or update regression test first and run it red. If deterministic test is impossible, state why and use strongest reproducible check from plan.
3. Implement smallest fix matching local architecture and visibility rules.
4. Run red test again green, then relevant compilation, unit/UI tests, and affected-module `detekt` tasks discovered from Gradle.
5. Build one reusable post-fix platform artifact only when needed for original-flow validation. Do not launch emulator or simulator.
6. Return `ExecuteArtifact`; do not stage or commit.

A failed required check blocks transition to `validate`, ends attempt, and returns to `plan` with exact evidence if attempts remain; at limit, transition to `done` with `exhausted`. Primary checkout remains unchanged.

### 3. validate

Subagent is read-only in the exact isolated workspace used by `execute`. It receives original report and all artifacts. It must verify workspace source identity, binary patch hash, and `Build artifact` before validation. It must not rebuild or repeat automated checks from `execute`.

It repeats original reproduction flow against received artifact once, when relevant environment is available, then compares result with acceptance criterion.

- Fixed: return successful `ValidationArtifact`, then transition to `done`.
- Still reproducible: return failed `ValidationArtifact` with new logs, conditions, and observations. Attempt increments and returns to `plan`; at limit, transition to `done` with `exhausted`.
- Cannot validate because required environment/data is missing or artifact identity is invalid: request it; attempt consumes one and must never be reported as fixed. After data, return to `plan` while attempts remain; at limit, transition to `done` with `blocked`.

### 4. done

Subagent receives original report and complete artifacts. It performs no source investigation or app verification.

- `fixed`: verify and apply validated patch to primary checkout exactly as `Baseline And Git Safety` requires; stage/commit session paths only; return cause, fix, evidence, Git comparison, and commit hash.
- `not_reproduced`: report all attempts, expected versus actual behavior, commands, log/report paths, missing data, and repeat steps. No primary mutation or commit.
- `exhausted` or `blocked`: report hypotheses and checks. No primary mutation or commit.

## Handoff Artifacts

Every subagent response uses exact headings.

### PlanArtifact

```markdown
## PlanArtifact
Attempt: <1-5>
Verdict: reproduced | not_reproduced | blocked
Bug report: <normalized report>
Baseline snapshot: <HEAD, Git commands/results, user-owned dirty sets>
Patch eligibility: <planned paths and baseline hashes/absence>
Environment and preconditions: <facts>
Reproduction: <numbered steps>
Expected / actual: <comparison>
Evidence budget: <baseline evidence, launch/build allowance, reason>
Evidence: <commands, concise outputs, log paths>
Cause or hypotheses: <evidence-backed>
Red test: <test and expected initial failure>
Fix plan: <ordered minimal changes>
Verification: <commands>
Affected modules and dependencies: <items>
Risks and missing data: <items>
```

### ExecuteArtifact

```markdown
## ExecuteArtifact
Attempt: <1-5>
Workspace source identity: <baseline HEAD and isolated workspace identity>
Changed files: <paths>
Patch hash: <binary patch hash>
Red evidence: <command and failure>
Fix: <concise description>
Green evidence: <command and result>
Compilation: <commands and results>
Tests: <commands and results>
Static analysis: <commands and results>
Build artifact: <required/not required; target, configuration, path, output hash, build command/result>
Diff summary: <focused summary>
Risks: <items>
```

### ValidationArtifact

```markdown
## ValidationArtifact
Attempt: <1-5>
Verdict: fixed | still_reproducible | cannot_validate
Workspace source identity: <verified baseline HEAD and patch hash>
Consumed build artifact: <target, configuration, path, output hash, identity check>
No-rebuild confirmation: <commands not rerun and reason>
Original reproduction: <numbered steps>
Observed result: <facts>
Acceptance comparison: <expected versus actual>
Evidence: <commands, concise outputs, log paths>
New inputs for plan: <items or none>
```

### DoneReport

```markdown
## DoneReport
Outcome: fixed | not_reproduced | exhausted | blocked
Cause: <evidence-backed or unknown>
What changed: <items or none>
What was checked: <commands and results>
Attempts: <1-5 summary>
Primary baseline comparison: <matched or exact block reason>
Applied patch hash: <hash or none>
Git: <staged session paths and commit hash, or no primary mutation>
Residual risks / missing data: <items>
```

## Required Checks

Choose checks from affected modules and project configuration; do not invent task names. `execute` must inspect Gradle task graph and run applicable regression tests, existing neighboring tests, compilation, and `detekt` tasks. For UI or platform defects, `validate` repeats original user-visible flow in relevant available environment using `Build artifact`. Report every skipped check with reason.

Do not ignore failing tests, static analysis, dependency violations, unrelated diff, or unverified assumptions. Do not change code during `plan` or `validate`. Do not hide a failed reproduction or call an unverified fix successful.

## Profile Verification

Profile acceptance is a separate task. In a fresh Codex task, provide a real bug report prefixed with `Bugfix:`. Verify:

1. Orchestrator performs no inspection, technical command, or checkout mutation.
2. Failed attempts leave primary checkout unchanged.
3. User dirty sets before `done` still equal `Baseline snapshot`.
4. Red-to-green TDD, compilation, tests, and `detekt` evidence remain present.
5. `validate` repeats original flow with artifact built by `execute`.
6. No duplicate post-fix build, automated test suite, or emulator/simulator launch occurs.
7. `done` alone applies patch and creates commit.
8. Stale artifact, primary conflict, attempt limit, and `/bugfix` text routing preserve safe outcomes.

Also try `/bugfix` when chat UI sends it as ordinary text. Update this profile only after recorded evidence shows a missing instruction or unsafe behavior.
