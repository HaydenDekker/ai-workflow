# Plan: Target Directory Validation

## Problem

When an `AgentDefinition` has a `null` or blank `targetDirectory`, the codebase silently falls back to `"/tmp"` across 12 call sites. This causes two issues:

1. **Collision with the SQLite database** (`/tmp/ai-workflow.db`), which the file watcher picks up as a file event and tries to read as UTF-8 text, throwing `MalformedInputException`.
2. **Unreliable fallback** — `/tmp` may not exist or may not be the intended scan location for an agent.

The goal is to remove all silent defaults and enforce that every agent has a real, valid `targetDirectory`. If missing, the agent definition stays in the database but initialization is halted with a `WARN` log.

---

## Architecture

```
TargetDirectoryValidator (new)
    ├── validate(String path) → ValidationResult
    │     ├── rejects: null, blank, relative, non-existent, non-directory, unreadable
    │     └── returns: ValidationResult.valid() or ValidationResult.invalid(reason)
    │
    AgentLifecycleUseCase (5 call sites)
    ├── initializeFromYAML()  — if invalid: WARN + skip scanner/flux, persist to DB
    ├── restoreFromDatabase() — if invalid: WARN + skip scanner/flux, persist in DB
    ├── enableAgent()         — if invalid: WARN + skip re-init
    ├── updateAgent()         — if invalid: WARN + return null
    └── refreshAgent()        — if invalid: WARN + skip re-subscribe
    │
    AgentRestController
    └── createAgent()         — if invalid: return 400 Bad Request
    │
    AgentInfoService
    └── createAgent()         — if invalid: Mono.error()
    │
    UI dialogs (cosmetic only)
    ├── AgentCreationDialog   — remove "/tmp" default from TextField
    └── AgentDetailDialog     — remove "/tmp" default from TextField
```

---

## Changes

### 1. New class: `TargetDirectoryValidator`

**Location:** `src/main/java/com/hdekker/ai_workflow/files/TargetDirectoryValidator.java`

**Methods:**

| Method | Signature | Behavior |
|--------|-----------|----------|
| `validate` | `ValidationResult validate(String path)` | Returns `valid()` if path is non-null, non-blank, absolute, exists, is a directory, and is readable. Returns `invalid(reason)` otherwise. |

**Validation rules (applied in order):**

1. `null` or blank → `"targetDirectory is required"`
2. Not absolute → `"targetDirectory must be an absolute path"`
3. Does not exist → `"targetDirectory does not exist: {path}"`
4. Exists but not a directory → `"targetDirectory is not a directory: {path}"`
5. Not readable → `"targetDirectory is not readable: {path}"`

**ValidationResult record:**

```java
public record ValidationResult(boolean valid, String reason) {
    public static ValidationResult valid() { return new ValidationResult(true, null); }
    public static ValidationResult invalid(String reason) { return new ValidationResult(false, reason); }
}
```

**No Spring annotation** — it has no dependencies. It's a plain utility. Injected into callers that need it.

---

### 2. `AgentLifecycleUseCase` — 5 call sites

All 5 methods currently do:

```java
String targetDir = def.targetDirectory() != null ? def.targetDirectory() : "/tmp";
```

Each must be replaced with:

```java
String targetDir = agentDefinition.targetDirectory();
ValidationResult result = targetDirectoryValidator.validate(targetDir);
if (!result.valid()) {
    log.warn("Agent {} has no valid targetDirectory: {}. Initialization halted.", agentId, result.reason());
    // Skip scanner creation, skip flux, skip subscription.
    // Agent definition remains in the database.
}
```

#### Specific method changes:

**`initializeFromYAML(List<AgentDefinition>)` — line 98**
- If `targetDirectory` is null/blank or invalid: log WARN, **do not** call `scannerRegistry.createForAgent()`, **do not** call `buildFlux()`, **do not** create a subscription. Persist to DB via `persistenceService.save()`.

**`restoreFromDatabase()` — line 143**
- If `targetDirectory` is null/blank or invalid: log WARN, **do not** call `scannerRegistry.createForAgent()`, **do not** call `buildFlux()`, **do not** create a subscription. Agent entity stays in the database (already there).

**`enableAgent(String id)` — line 350**
- If `targetDirectory` is null/blank or invalid: log WARN, **do not** call `buildFlux()`, **do not** create a subscription. Agent stays disabled/in dormant state.

**`updateAgent(String id, AgentDefinition)` — line 403**
- If `targetDirectory` is null/blank or invalid: log WARN, **do not** call `addDynamicAgent()`. Return `null`.

**`refreshAgent(String agentId)` — line 441**
- If `targetDirectory` is null/blank or invalid: log WARN, **do not** call `buildFlux()`, **do not** create a subscription. Agent stays in its current state.

---

### 3. `AgentRestController` — 1 call site

**`createAgent()` — line 30**

Replace:

```java
AgentInfo agentInfo = dynamicAgentManager.addDynamicAgent(agentDefinition,
    agentDefinition.targetDirectory() != null ? agentDefinition.targetDirectory() : "/tmp");
```

With:

```java
String targetDir = agentDefinition.targetDirectory();
ValidationResult result = targetDirectoryValidator.validate(targetDir);
if (!result.valid()) {
    return ResponseEntity.badRequest()
        .body(new AgentInfo(null, agentDefinition, null, false, null, null));
    // Or return a dedicated error DTO. See note below.
}
return ResponseEntity.ok(dynamicAgentManager.addDynamicAgent(agentDefinition, targetDir));
```

**Note:** Returning a half-null `AgentInfo` is a temporary measure. The preferred approach is to return a generic error response body:

```java
return ResponseEntity.badRequest().body(Map.of("error", result.reason()));
```

This avoids polluting `AgentInfo` with error semantics.

---

### 4. `AgentInfoService` — 1 call site

**`createAgent()` — line 52**

Replace:

```java
String targetDir = agentDefinition.targetDirectory() != null
        ? agentDefinition.targetDirectory()
        : "/tmp";
AgentInfo info = dynamicAgentManager.addDynamicAgent(agentDefinition, targetDir);
```

With:

```java
String targetDir = agentDefinition.targetDirectory();
ValidationResult result = targetDirectoryValidator.validate(targetDir);
if (!result.valid()) {
    return Mono.error(new IllegalArgumentException(result.reason()));
}
AgentInfo info = dynamicAgentManager.addDynamicAgent(agentDefinition, targetDir);
return Mono.just(info);
```

---

### 5. UI Dialogs — 2 files, 5 locations

Remove `"/tmp"` as the default value for `targetDirectoryField` in all places:

| File | Line | Change |
|------|------|--------|
| `AgentCreationDialog.java` | 86 | `createTextField("Target Directory", "", 255)` — empty default |
| `AgentCreationDialog.java` | 161 | `existing.targetDirectory()` only — no fallback |
| `AgentCreationDialog.java` | 185 | `targetDirectoryField.clear()` or `setValue("")` |
| `AgentDetailDialog.java` | 100 | `createTextField("Target Directory", "", 255)` — empty default |
| `AgentDetailDialog.java` | 189 | `def.targetDirectory()` only — no fallback |

Additionally, add a form-level validation before submission that checks the field is non-empty. The actual validation happens on the backend; the frontend check is just a user-friendly pre-check.

---

### 6. Tests

#### `TargetDirectoryValidatorTest` (unit)

| Test | Input | Expected |
|------|-------|----------|
| `validatesAbsoluteExistingReadableDir` | real temp dir path | `valid()` |
| `rejectsNull` | `null` | `invalid("targetDirectory is required")` |
| `rejectsBlank` | `""` or `"  "` | `invalid("targetDirectory is required")` |
| `rejectsRelativePath` | `"./some/path"` | `invalid("targetDirectory must be an absolute path")` |
| `rejectsNonExistent` | `"/nonexistent/path"` | `invalid("targetDirectory does not exist: /nonexistent/path")` |
| `rejectsFileNotDirectory` | path to an existing file | `invalid("targetDirectory is not a directory: ...")` |
| `rejectsUnreadableDir` | directory with no read permission | `invalid("targetDirectory is not readable: ...")` |

#### `AgentLifecycleUseCase` (integration)

- **`restoreFromDatabase_nullTargetDirectory`** — Persist an agent with `targetDirectory = null`, call `restoreFromDatabase()`, verify: agent entity is in DB, no scanner was created, WARN log was emitted.
- **`initializeFromYAML_nullTargetDirectory`** — Pass an `AgentDefinition` with `targetDirectory = null`, call `initializeFromYAML()`, verify: agent is persisted, no scanner created, WARN log emitted.
- **`createAgent_invalidTargetDirectory_returns400`** — POST to `/api/agents` with `targetDirectory: null`, verify 400 response.

---

## Files to Modify (Summary)

| File | Lines to Change | Type |
|------|----------------|------|
| **New:** `TargetDirectoryValidator.java` | — | New class |
| **New:** `TargetDirectoryValidatorTest.java` | — | New test |
| `AgentLifecycleUseCase.java` | 98, 143, 350, 403, 441 | Remove 5 ternaries, add validator calls |
| `AgentRestController.java` | 30 | Remove ternary, add validator + 400 response |
| `AgentInfoService.java` | 52 | Remove ternary, add validator + `Mono.error()` |
| `AgentCreationDialog.java` | 86, 161, 185 | Remove `"/tmp"` defaults |
| `AgentDetailDialog.java` | 100, 189 | Remove `"/tmp"` defaults |
| **Tests to update:** `AgentLifecycleUseCasePersistenceTest.java`, `AgentLifecycleUseCaseScannerRestoreTest.java`, `AgentListViewDeleteTest.java` | Various | Replace `"/tmp"` in test data with real temp dirs or adjust expectations |

---

## Execution Order

1. **Create `TargetDirectoryValidator` + tests** — no dependency on existing changes.
2. **Update `AgentLifecycleUseCase`** — 5 call sites, each self-contained. Run `./mvnw verify` after each.
3. **Update `AgentRestController`** — adds 400 response path.
4. **Update `AgentInfoService`** — adds `Mono.error` path.
5. **Update UI dialogs** — cosmetic changes, no logic impact.
6. **Fix test data** — replace `"/tmp"` with real temp directories in test assertions.
7. **Run `./mvnw verify`** — full suite.
