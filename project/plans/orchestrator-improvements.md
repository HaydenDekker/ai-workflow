# Plan: Orchestrator Improvements

> **Created:** 2026-05-11

## Problem

Running plans 11 and 12 with the orchestrator exposed several process defects: false failures from partial working trees, wasted tool calls on path resolution, unparseable test failure output, no baseline checks, and no awareness of sibling merges. The orchestrator is the primary tool for multi-phase work — its reliability directly affects how much manual intervention is needed.

## Target

- **Baseline check**: Verify tests pass on clean tree before spawning sub-agent (prevents false failures from pre-existing issues)
- **Commit before verify**: Sub-agent changes committed (or snapshotted) before the orchestrator runs verification tests (prevents partial-tree failures)
- **Better failure output**: Test failure summary shows which test class/method failed, not just rc=1 and conditions report noise
- **Correct mvnw path**: Detect shell and inject `./mvnw` (not `mvnw.cmd`) into sub-agent context — saves ~6 tool calls per plan
- **Phase idempotency**: Detect uncommitted changes from a previous failed run and decide commit/discard/retry before spawning
- **Rebase detection**: Warn or auto-rebase if branch is behind `main` at startup (sibling merge scenario)

## Implementation Status: ⬜ Draft

## Existing Tests
| Test Class | What it covers | Status |
|------------|---------------|--------|
| *(orchestrator is Python, not Java — no existing test suite)* | — | ⚠️ Untested |

## Test Gaps

- No unit tests for the orchestrator Python script at all
- No test for phase detection, branch creation, compile/test commands
- No integration test for the full spawn-verify-commit cycle

## Phases

### Phase 0: Baseline test check before spawning
- [ ] Add `./mvnw test -q` on clean working tree before Phase 0 spawn
- [ ] If baseline fails, abort with clear message listing which test(s) failed
- [ ] If baseline passes, continue normally
- [ ] Make baseline check skippable via `--skip-baseline` flag
- [ ] Add a simple Python unit test: verify baseline check runs when tests exist

### Phase 1: Commit sub-agent changes before verification
- [ ] After sub-agent finishes (success or stall), run `git add -A && git diff --staged --quiet`
- [ ] If there are staged changes, commit them with message `chore: sub-agent phase <N> changes`
- [ ] Then run compile and test verification against the committed state
- [ ] If tests fail, the working tree is stable (committed) for manual inspection or `--from` resume
- [ ] Update the existing auto-commit step to be a no-op if already committed
- [ ] Add Python unit test: verify commit-before-verify ordering

### Phase 2: Improved test failure reporting
- [ ] Capture full test output and extract failure summary (class name, method name, failure reason)
- [ ] Print a concise failure block (top 3 failures) instead of raw Maven output
- [ ] Save full output to a timestamped file in `project/orchestrator-logs/` for deep inspection
- [ ] Show the failure summary in the abort message
- [ ] Add Python unit test: parse sample Maven failure output and extract test names

### Phase 3: Correct mvnw path injection
- [ ] Detect shell at startup (bash vs cmd vs pwsh)
- [ ] Set `BUILD_CMD` and `TEST_CMD` to use `./mvnw` prefix (works in bash/MSYS) or `.\mvnw.cmd` (works in cmd)
- [ ] Inject the resolved path into the sub-agent system prompt so it never tries `mvnw.cmd` in bash
- [ ] Add `--shell auto|bash|cmd|pwsh` flag for manual override
- [ ] Add Python unit test: verify shell detection and path construction

### Phase 4: Phase idempotency / dirty tree handling
- [ ] Before spawning, check `git status --porcelain`
- [ ] If dirty: show summary of uncommitted changes
- [ ] Options: `--on-dirty commit|discard|abort` (default: `abort`)
- [ ] If `commit`: auto-commit with `chore: recover phase <N> partial changes`
- [ ] If `discard`: `git checkout -- . && git clean -fd` before spawning
- [ ] Add Python unit test: verify dirty detection and each disposition path

### Phase 5: Rebase detection at startup
- [ ] At startup, compare current branch HEAD with `main` (or `origin/main`)
- [ ] If behind: warn with count of missing commits
- [ ] Options: `--rebase auto|ask|skip` (default: `ask`, which prompts — or `auto` for CI)
- [ ] If `auto`: run `git rebase main` and abort on conflict
- [ ] Add Python unit test: verify rebase detection logic

### Phase 6: Unit test harness for the orchestrator
- [ ] Add `project/skills/plan-orchestrator/tests/` with pytest-style tests
- [ ] Test fixtures: sample plan markdown, sample Maven output (success + failure)
- [ ] Test coverage: phase parsing, branch naming, build command resolution, failure extraction
- [ ] Wire `pytest` as a pre-flight check the orchestrator can run on itself (`--self-test`)

## Notes

- The orchestrator is a standalone Python script — no Maven build needed for its own tests
- pytest is the standard; the orchestrator currently uses stdlib only. Adding pytest as a test dependency is fine (it's not used at runtime)
- Each phase's Python unit test validates its own change, keeping the pattern self-referential
- After this plan, the orchestrator should have basic test coverage for the logic paths that failed during plans 11/12
