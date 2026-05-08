---
name: plan-orchestrator
description: "Execute plan phases sequentially using pi RPC sub-agents. Reads a plan file, spawns a pi sub-agent per phase with full context, monitors for stalls, verifies compile/tests, and auto-commits. USE FOR: execute plan phases, run multi-phase plan, orchestrator, sequential plan execution, phase-by-phase plan, plan runner, sub-agent orchestration, automated plan execution. DO NOT USE FOR: writing plan content, creating ADRs, creating DPRs, manual code review."
license: MIT
metadata:
  author: AI Workflow Team
  version: "2.0.0"
  compatibility: "Python 3.10+, pi installed via npm, git initialized"
---

# Plan Orchestrator Skill

Execute multi-phase plans automatically by spawning pi sub-agents via RPC. Each phase gets its own isolated pi process with full plan context, and the orchestrator verifies compile/tests and commits before advancing.

---

## Core Workflow

```
Plan File
    ↓
Parse phases from markdown
    ↓
For each phase:
    1. Spawn pi --mode rpc --no-session
    2. Send prompt with full plan context + phase checklist
    3. Stream events (text, tool calls, bash output)
    4. Detect stalls (kill if no LLM activity for 2× timeout)
    5. Compile project
    6. Run tests
    7. Auto-commit changes
    8. Advance to next phase
```

## Prerequisites

Before using this skill:

1. **Pi must be installed**: `npm install -g @earendil-works/pi-coding-agent`
2. **Python 3.10+** must be available
3. **Git must be initialized** in the project root
4. **Project must have a build system** (Maven, Gradle, etc.)
5. **Tests must be runnable** via command line

## Setup

The orchestrator is located at `project/skills/plan-orchestrator/orchestrator.py`

Verify it exists:
```bash
python project/skills/plan-orchestrator/orchestrator.py --help
```

## Usage

### Basic Execution

Run all phases from a plan file:

```bash
python project/skills/plan-orchestrator/orchestrator.py project/plans/plan-name.md
```

### Resuming Mid-Plan

If the orchestrator was interrupted (timeout, manual kill, etc.):

```bash
python project/skills/plan-orchestrator/orchestrator.py project/plans/plan-name.md --from 3
```

This skips phases 0-2 and starts at Phase 3. The sub-agent sees the current working tree state, so it can pick up where it left off.

### Dry Run

Preview what would be executed without running anything:

```bash
python project/skills/plan-orchestrator/orchestrator.py project/plans/plan-name.md --dry-run
```

### Model Selection

Use `--provider` and `--model` to select the LLM for sub-agents. Your configured models:

| Provider | Endpoint | Model | Context | Max Output | Use Case |
|----------|----------|-------|---------|------------|----------|
| `llama-workhorse` | `192.168.2.108:8080` | `qwen3-27b` | 150K | 32.8K | Fast iteration, simple refactors |
| `llama-workhorse` | `192.168.2.108:8080` | `qwen3-35b` | 140K | 32.8K | Balanced quality/speed |
| `llama-workbeast` | `192.168.2.103:8080` | `qwen3.6-35B-q8` | 200K | 32.8K | Complex refactors, deep reasoning |

```bash
# Fast iteration — 27B model (good for simple changes)
python project/skills/plan-orchestrator/orchestrator.py plan.md --provider llama-workhorse --model qwen3-27b

# Balanced — 35B model (default recommendation)
python project/skills/plan-orchestrator/orchestrator.py plan.md --provider llama-workhorse --model qwen3-35b

# Heavy lifting — 35B q8 with 200K context (complex multi-file refactors)
python project/skills/plan-orchestrator/orchestrator.py plan.md --provider llama-workbeast --model qwen3.6-35B-q8
```

> **Tip**: Start with `qwen3-35b` for most plans. Use `qwen3.6-35B-q8` when phases have many files, large codebases, or deep cross-cutting changes. Use `qwen3-27b` for quick, localized changes.

### Custom Configuration

```bash
# Longer stall timeout (useful for slow builds)
python project/skills/plan-orchestrator/orchestrator.py plan.md --stall-minutes 30

# Skip test verification (use if tests are flaky or slow)
python project/skills/plan-orchestrator/orchestrator.py plan.md --skip-test

# Skip auto-commit (review changes manually between phases)
python project/skills/plan-orchestrator/orchestrator.py plan.md --skip-commit

# Custom build command (e.g., Gradle)
python project/skills/plan-orchestrator/orchestrator.py plan.md --build-cmd "./gradlew build"

# Custom test command
python project/skills/plan-orchestrator/orchestrator.py plan.md --test-cmd "./gradlew test"

# Custom test pattern (Maven-style)
python project/skills/plan-orchestrator/orchestrator.py plan.md --test-glob "*Service*"

# Combine options
python project/skills/plan-orchestrator/orchestrator.py plan.md --from 2 --stall-minutes 20 --test-glob "*Test*"
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCH_STALL_MINUTES` | `15` | Kill sub-agent if no LLM activity for this many minutes |
| `ORCH_BRANCH_PREFIX` | `refactor` | Branch name prefix (e.g., `refactor/my-plan`) |

```bash
ORCH_STALL_MINUTES=30 python project/skills/plan-orchestrator/orchestrator.py plan.md
```

## Plan File Format

The orchestrator expects a markdown plan with phases structured as:

```markdown
# Plan: My Refactoring

## Problem
Description of the problem...

## Target
Description of the target state...

## Phases

### Phase 0: Create domain enum
- [ ] Task A
- [ ] Task B

### Phase 1: Refactor service
- [ ] Task C
- [ ] Task D
```

**Phase headers must match the pattern**: `### Phase N: <title>`

The orchestrator extracts:
- **Context**: Everything before Phase 0 (problem, target, architecture)
- **Each phase**: Title, checklist items, and any inline notes

### Phase Structure Best Practices

Each phase should:
1. Have a **clear, single responsibility** (one concept, one concern)
2. Include **checklist items** (bullet points with `[ ]` or `-`)
3. Specify **which files to create/modify**
4. End with **compile + test verification**

Example:
```markdown
### Phase 2: Create `ScannerEventBus`
- [ ] Create `ScannerEventBus` implementing `ScannerEventPort`
- [ ] Implement `publish()` with callback iteration
- [ ] Add `registerCallback()` / `unregisterCallback()` with `CopyOnWriteArrayList`
- [ ] Handle individual callback failures (log, don't break others)
- [ ] Compile and verify no errors
```

## What Happens Per Phase

1. **Spawn sub-agent**: `node .../cli.js --mode rpc --no-session`
2. **Send prompt**: Full plan context + phase-specific checklist + instructions
3. **Stream events**:
   - Text output streamed live to terminal
   - Tool calls logged with file paths and command previews
   - Bash output truncated to first 5 lines
4. **Stall detection**:
   - Resets timer on LLM activity (text_delta, thinking_delta, toolcall_delta)
   - Does NOT reset on tool_execution_update (prevents masking during long Maven runs)
   - Kills process if idle for 2× stall timeout (30m default)
5. **Compile check**: Runs `--build-cmd` (default: `./mvnw compile -q`)
6. **Test check**: Runs `--test-cmd` (default: `./mvnw test -Dtest="*" -q`)
7. **Auto-commit**: `git add -A && git commit -m "feat: <phase title>"`
8. **Advance**: Moves to next phase or exits with summary

## Output Format

```
======================================================================
Plan: scanner-status-observability-refactor.md
Phases: 10  |  Starting from: Phase 0
Stall timeout: 15m
======================================================================

  [-] Phase 0: Create `ScannerFileResult` domain enum
  [>] Phase 1: Split ports — metrics vs events
  ...

======================================================================
Phase 0: Phase 0: Create `ScannerFileResult` domain enum
======================================================================

  Spawning pi sub-agent (stall timeout: 15m)...
I'll start by exploring the existing codebase...

  [tool 1] bash: find src/main/java -type f -name "*.java" | grep
  [tool 2] read: src/main/java/.../ScannerEventType.java
  [tool 3] edit: src/main/java/.../ScannerFileResult.java
    [INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.094 s
  Agent finished (16 tool calls).

  Compiling...
  [OK] Compile OK

  Running tests...
  [OK] Tests OK

  Staging and committing...
  [OK] Committed: feat: Phase 0: Create `ScannerFileResult` domain enum

  [OK] Phase 0 COMPLETE
```

## Summary at End

```
======================================================================
SUMMARY
======================================================================
  [OK] Phase 0: OK (16 tool calls)
  [OK] Phase 1: OK (28 tool calls)
  [OK] Phase 2: OK (32 tool calls)
  [FAIL] Phase 3: FAIL-COMPILE (20 tool calls)

Total: 4 phases executed, 3 passed, 1 failed.
```

## Troubleshooting

### Sub-agent stalls or hangs

If a sub-agent appears stuck (not making progress):

```bash
# Check what's running
ps aux | grep pi

# Kill orphaned pi processes
pkill -f "pi --mode rpc"

# Resume with shorter timeout
python project/skills/plan-orchestrator/orchestrator.py plan.md --from 2 --stall-minutes 10
```

### Compile or test failures

When a phase fails compile or tests:

1. **Read the error output** from the orchestrator
2. **Fix the issue manually** in the codebase
3. **Resume from the failed phase**: `python project/skills/plan-orchestrator/orchestrator.py plan.md --from <N>`

The sub-agent will see the current working tree state and adapt.

### "No LLM activity" stall detection false positive

If the sub-agent is legitimately idle (waiting for user input, etc.):

- Increase stall timeout: `--stall-minutes 30`
- Or skip verification: `--skip-test` (if tests are expected to be reworked)

### Working tree not clean

The orchestrator warns about uncommitted changes but continues. To start fresh:

```bash
git stash
python project/skills/plan-orchestrator/orchestrator.py plan.md
git stash pop  # if needed
```

### Branch Contract

The orchestrator handles **branch creation** but does **not** handle merging or deletion. Those are the responsibility of the **project-management** skill.

| Action | Who | When |
|--------|-----|------|
| Create branch `{prefix}/{plan-slug}` | **Orchestrator** | When starting execution from `main` |
| Commit per phase | **Orchestrator** | After each phase passes compile + tests |
| Merge to `main` | **Project-management skill** | When plan completes all phases |
| Delete feature branch | **Project-management skill** | After merge to `main` |
| Update master plan index | **Project-management skill** | After merge |

**Branch creation rules:**
- Creates a branch named `{prefix}/{plan-slug}` when on `main`
  - `prefix` from `ORCH_BRANCH_PREFIX` env var (default: `refactor`)
  - `plan-slug` is the plan filename without `.md` extension
- Example: `refactor/scanner-observability-refactor`
- If already on a feature branch, uses the current branch (no new branch created)

To use a custom branch:
```bash
git checkout -b my-custom-branch
python project/skills/plan-orchestrator/orchestrator.py plan.md  # uses current branch
```

### Post-Execution Handoff

After all phases complete successfully, the orchestrator prints a summary and exits. The next step is to **complete the plan** through the **project-management** skill:

1. Extract knowledge to ADRs/DPRs/design documents
2. Merge branch to `main`: `git checkout main && git merge {prefix}/{plan-slug} --no-edit`
3. Delete branch: `git branch -d {prefix}/{plan-slug}`
4. Update `master-plan.md`: set status to `✅ Complete`, add completion date
5. Check for auto-purge candidates (completed > 4 weeks ago)
6. Commit: `docs: complete plan <slug>, merge to main`

## Advanced Patterns

### Model Selection Best Practices

**Per-phase model switching** is not supported — the `--provider` and `--model` flags apply to all phases. Plan accordingly:

```bash
# Use the strongest model for a complex refactor across all phases
python project/skills/plan-orchestrator/orchestrator.py complex-plan.md --provider llama-workbeast --model qwen3.6-35B-q8

# Use a faster model for simple, localized changes
python project/skills/plan-orchestrator/orchestrator.py small-fix.md --provider llama-workhorse --model qwen3-27b
```

**When in doubt**, use `qwen3-35b` — it gives the best quality/speed tradeoff for most refactoring tasks.

### Phased Rollout

Run phases in batches with manual review between:

```bash
# Run phases 0-2, then review
python project/skills/plan-orchestrator/orchestrator.py plan.md --from 0 --skip-commit
# ... manual review ...
git add -A && git commit -m "feat: phases 0-2"

# Run phases 3-5
python project/skills/plan-orchestrator/orchestrator.py plan.md --from 3 --skip-commit
# ... manual review ...
git add -A && git commit -m "feat: phases 3-5"
```

### Parallel Plan Execution

For independent plans, run them in separate terminal windows:

```bash
# Terminal 1
python project/skills/plan-orchestrator/orchestrator.py project/plans/plan-a.md

# Terminal 2
python project/skills/plan-orchestrator/orchestrator.py project/plans/plan-b.md
```

Each runs in its own git branch and doesn't interfere.

### CI/CD Integration

Use in CI to validate plan execution:

```yaml
# GitHub Actions example
- name: Execute Plan
  run: |
    python project/skills/plan-orchestrator/orchestrator.py plan.md --skip-commit
    git diff --exit-code
  env:
    ORCH_STALL_MINUTES: 20
```

## Skill Architecture

```
project/skills/plan-orchestrator/
├── SKILL.md              # This file — instructions for the agent
└── orchestrator.py       # The orchestrator script
```

The orchestrator script is:
- **Self-contained**: No external Python dependencies (only stdlib)
- **Cross-platform**: Works on Windows, macOS, Linux
- **Non-destructive**: Creates branches, commits per phase, never rewrites history
- **Interruptible**: Safe to kill mid-phase; resume with `--from <N>`

## Comparison with Alternatives

| Approach | Pros | Cons |
|----------|------|------|
| **Manual phases** | Full control | Slow, context loss between phases |
| **tmux sessions** | Visual, parallel | Complex setup, manual coordination |
| **This skill** | Automated, incremental verification, resumable | Sequential only, requires pi + Python |

## When Not to Use This Skill

- **Single-phase plans**: The orchestrator overhead isn't worth it for one-phase work
- **Plans requiring parallel execution**: The orchestrator runs phases sequentially
- **Plans with inter-phase dependencies that require manual intervention**: Each phase commits automatically; if a phase needs human review mid-stream, use `--skip-commit`
- **Plans where tests are unreliable**: The orchestrator aborts on test failures; if tests are flaky, use `--skip-test` but review carefully

## Quick Reference

```bash
# Run plan (default provider/model)
python project/skills/plan-orchestrator/orchestrator.py <plan-path>

# Run with specific model
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --provider llama-workhorse --model qwen3-35b
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --provider llama-workbeast --model qwen3.6-35B-q8

# Resume from phase N
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --from N

# Dry run (preview)
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --dry-run

# Longer timeout
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --stall-minutes 30

# Skip verification steps
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --skip-test --skip-commit

# Custom build/test
python project/skills/plan-orchestrator/orchestrator.py <plan-path> --build-cmd "./gradlew build" --test-cmd "./gradlew test"

# Check help
python project/skills/plan-orchestrator/orchestrator.py --help
```
