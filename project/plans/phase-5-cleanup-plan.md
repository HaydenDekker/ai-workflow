# Phase 5: Cleanup — Detailed Plan

> **Status:** Recipe file created (`rewrite-5.0.yml`). Ready for execution.
> **Recipe file:** `rewrite-5.0.yml` contains all 4 OpenRewrite recipes for phases 5.1–5.5.

## Completed Phases

| Phase | Description | Status | Notes |
|-------|-------------|--------|-------|
| 5.2 | Fix Javadoc @link references | ✅ Done | Both `FileSystemScannerAdapterFactory.java` and `NativeFileWatcher.java` already reference new packages |

## Current State Summary

### Old packages still present (duplicates of new application layer)

| Old Path | New Path | Status |
|----------|----------|--------|
| `app/pipeline/AgentBuilder.java` | `application/pipeline/AgentBuilder.java` | Duplicate (old version, formatting only) |
| `app/pipeline/AgentConfigurator.java` | `application/pipeline/AgentConfigurator.java` | Duplicate (old version, no port support) |
| `app/pipeline/management/ScannerRegistry.java` | `application/pipeline/ScannerRegistry.java` | Duplicate (old version, depends on concrete adapters) |
| `usecases/AgentLifecycleUseCase.java` | `application/agent/AgentLifecycleService.java` | Duplicate (old version, depends on concrete adapters) |
| `usecases/AgentStatusUsecase.java` | `application/agent/AgentStatusService.java` | Duplicate (old version, depends on concrete adapters) |
| `usecases/FileCounter.java` | `application/file/port/FileCounterPort.java` | Duplicate (interface, same contract) |
| `usecases/Scanner.java` | `application/scanner/ScannerService.java` | Duplicate (old version, depends on concrete adapters) |
| `usecases/ScannerObserverUseCase.java` | `application/scanner/ScannerObserverService.java` | Duplicate (old version, depends on concrete adapters) |

### Files importing old packages (must be updated before deletion)

**Main source (4 files, 8 imports):**
| File | Import(s) |
|------|-----------|
| `config/AgentConfiguration.java` | `usecases.AgentLifecycleUseCase` |
| `config/AgentRestoreOnStartup.java` | `usecases.AgentLifecycleUseCase` |
| `adapter/inbound/ui/component/AdapterStatusComponent.java` | `usecases.AgentStatusUsecase` |
| `adapter/inbound/ui/view/ObservabilityView.java` | `usecases.AgentStatusUsecase` |
| `adapter/inbound/ui/view/ScannerListView.java` | `usecases.ScannerObserverUseCase` |

**Test source (4 files, 5 imports):**
| File | Import(s) |
|------|-----------|
| `adapter/outbound/file/FileSystemSimplePollerFluxAdapterTest.java` | `usecases.Scanner`, `usecases.ScannerObserverUseCase` |
| `pipeline/FileIntegrationFlowTest.java` | `usecases.AgentLifecycleUseCase` |
| `pipeline/LLMAdapterIntegrationTest.java` | `app.pipeline.AgentBuilder` |
| `app/pipeline/AgentPipelineTest.java` | `usecases.Scanner`, `usecases.ScannerObserverUseCase` |
| `app/pipeline/management/ScannerRegistryIntegrationTest.java` | `usecases.AgentLifecycleUseCase`, `usecases.ScannerObserverUseCase` |
| `app/pipeline/management/ScannerRegistryTest.java` | `usecases.ScannerObserverUseCase` |
| `usecases/*Test.java` (4 files) | `app.pipeline.management.ScannerRegistry` |

**Javadoc references (4 occurrences in 2 files):**
| File | Reference |
|------|-----------|
| `adapter/outbound/file/FileSystemScannerAdapterFactory.java` (line 11) | `com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry` |
| `adapter/outbound/file/NativeFileWatcher.java` (lines 22, 263) | `com.hdekker.ai_workflow.usecases.Scanner` |

### Packages already cleaned up
- `database/` — **gone** ✅
- `files/` (infra) — **gone** ✅
- `pipeline/` (top-level) — **gone** ✅ (only `app/pipeline/` and `application/pipeline/` remain)

### Test packages still in old locations
| Old Test Path | New Test Path (should be) |
|---------------|---------------------------|
| `test/usecases/AgentLifecycleUseCaseTest.java` | `test/application/agent/AgentLifecycleServiceTest.java` |
| `test/usecases/AgentLifecycleUseCasePersistenceTest.java` | `test/application/agent/AgentLifecycleServicePersistenceTest.java` |
| `test/usecases/AgentLifecycleUseCaseScannerRestoreTest.java` | `test/application/agent/AgentLifecycleServiceScannerRestoreTest.java` |
| `test/usecases/AgentStatusUsecaseTest.java` | `test/application/agent/AgentStatusServiceTest.java` |
| `test/usecases/ScannerTest.java` | `test/application/scanner/ScannerServiceTest.java` |
| `test/usecases/ScannerObserverUseCaseTest.java` | `test/application/scanner/ScannerObserverServiceTest.java` |
| `test/app/pipeline/AgentBuilderTest.java` | `test/application/pipeline/AgentBuilderTest.java` |
| `test/app/pipeline/AgentConfiguratorTest.java` | `test/application/pipeline/AgentConfiguratorTest.java` |
| `test/app/pipeline/AgentPipelineTest.java` | `test/application/pipeline/AgentPipelineTest.java` |
| `test/app/pipeline/management/ScannerRegistryTest.java` | `test/application/pipeline/ScannerRegistryTest.java` |
| `test/app/pipeline/management/ScannerRegistryIntegrationTest.java` | `test/application/pipeline/ScannerRegistryIntegrationTest.java` |

---

## Phase 5.1: Update all imports from old to new packages (OpenRewrite)

**Goal:** Replace every import of `com.hdekker.ai_workflow.usecases.*` and `com.hdekker.ai_workflow.app.*` with the new `application.*` equivalent across all source and test files.

### Recipe: `rewrite-5.0.yml`

The OpenRewrite recipe is defined in `rewrite-5.0.yml` under the name
`com.hdekker.ai-workflow.phase5.import-updates`. It uses `ChangeType` to update
all imports from old packages to new ones across the entire codebase (main + test).

**Import mapping:**
```
usecases.AgentLifecycleUseCase        → application.agent.AgentLifecycleService
usecases.AgentStatusUsecase           → application.agent.AgentStatusService
usecases.Scanner                      → application.scanner.ScannerService
usecases.ScannerObserverUseCase       → application.scanner.ScannerObserverService
usecases.FileCounter                  → application.file.port.FileCounterPort
app.pipeline.AgentBuilder             → application.pipeline.AgentBuilder
app.pipeline.AgentConfigurator        → application.pipeline.AgentConfigurator
app.pipeline.management.ScannerRegistry → application.pipeline.ScannerRegistry
```

**Current state of main source imports:** All 5 main source files already reference
the new `application.*` packages. No changes needed in main sources.

**Files that still need import updates (test sources):**
| File | Imports to update |
|------|-------------------|
| `adapter/outbound/file/FileSystemSimplePollerFluxAdapterTest.java` | `usecases.Scanner`, `usecases.ScannerObserverUseCase` |
| `pipeline/FileIntegrationFlowTest.java` | `usecases.AgentLifecycleUseCase` |
| `pipeline/LLMAdapterIntegrationTest.java` | `app.pipeline.AgentBuilder` |
| `app/pipeline/AgentPipelineTest.java` | `usecases.Scanner`, `usecases.ScannerObserverUseCase` |
| `app/pipeline/management/ScannerRegistryIntegrationTest.java` | `usecases.AgentLifecycleUseCase`, `usecases.ScannerObserverUseCase` |
| `app/pipeline/management/ScannerRegistryTest.java` | `usecases.ScannerObserverUseCase` |
| `usecases/*Test.java` (7 files) | Various old `app.pipeline` and `usecases` imports |

### Execution steps

1. **Dry-run:** `./mvnw rewrite:dryRun -Drewrite.activeRecipe=com.hdekker.ai-workflow.phase5.import-updates`
2. **Apply:** `./mvnw rewrite:run -Drewrite.activeRecipe=com.hdekker.ai-workflow.phase5.import-updates`
3. **Compile:** `./mvnw compile -q`
4. **Test:** Run relevant tests (see test matrix below)

### Test matrix for Phase 5.1

| Test Class | Scope | Command |
|------------|-------|---------|
| `AgentConfiguration` (if exists) | Config | `./mvnw test -Dtest=AgentConfiguration* -q` |
| `AgentRestoreOnStartup` (if exists) | Config | `./mvnw test -Dtest=AgentRestoreOnStartup* -q` |
| `AdapterStatusComponentTest` | UI adapter | `./mvnw test -Dtest=AdapterStatusComponent* -q` |
| `ObservabilityViewTest` | UI adapter | `./mvnw test -Dtest=ObservabilityView* -q` |
| `ScannerListViewTest` | UI adapter | `./mvnw test -Dtest=ScannerListView* -q` |
| `AgentLifecycleService*` | App layer | `./mvnw test -Dtest=AgentLifecycleService* -q` |
| `AgentStatusService*` | App layer | `./mvnw test -Dtest=AgentStatusService* -q` |
| `ScannerService*` | App layer | `./mvnw test -Dtest=ScannerService* -q` |
| `ScannerObserverService*` | App layer | `./mvnw test -Dtest=ScannerObserverService* -q` |

---

## Phase 5.2: Update Javadoc @link references

**Status: ✅ Already completed.** All 3 Javadoc references have been updated:

| File | Current State |
|------|---------------|
| `adapter/outbound/file/FileSystemScannerAdapterFactory.java` (line 11) | ✅ `com.hdekker.ai_workflow.application.pipeline.ScannerRegistry` |
| `adapter/outbound/file/NativeFileWatcher.java` (line 22) | ✅ Already updated |
| `adapter/outbound/file/NativeFileWatcher.java` (line 263) | ✅ Already updated |

**Verification:**
```bash
grep -rn "usecases\.Scanner\|app\.pipeline\.management\.ScannerRegistry" src/main/java/ --include="*.java"
# Should return nothing (only the old duplicate files themselves contain old refs)
```

---

## Phase 5.3: Move test files to new package paths (OpenRewrite)

**Goal:** Move all test classes from old package paths to match the new source structure.

### OpenRewrite recipe: `com.hdekker.ai-workflow.phase5.test-moves`

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.hdekker.ai-workflow.phase5.test-moves
displayName: Phase 5.3 — Move test files to new package paths
description: |
  Move test classes from old usecases/app package paths to match the new
  application layer structure. Each test class moves to the package of its
  corresponding production class.

  Test file mapping:
    test/usecases/AgentLifecycleUseCaseTest.java
      → test/application/agent/AgentLifecycleServiceTest.java
    test/usecases/AgentLifecycleUseCasePersistenceTest.java
      → test/application/agent/AgentLifecycleServicePersistenceTest.java
    test/usecases/AgentLifecycleUseCaseScannerRestoreTest.java
      → test/application/agent/AgentLifecycleServiceScannerRestoreTest.java
    test/usecases/AgentStatusUsecaseTest.java
      → test/application/agent/AgentStatusServiceTest.java
    test/usecases/ScannerTest.java
      → test/application/scanner/ScannerServiceTest.java
    test/usecases/ScannerObserverUseCaseTest.java
      → test/application/scanner/ScannerObserverServiceTest.java
    test/app/pipeline/AgentBuilderTest.java
      → test/application/pipeline/AgentBuilderTest.java
    test/app/pipeline/AgentConfiguratorTest.java
      → test/application/pipeline/AgentConfiguratorTest.java
    test/app/pipeline/AgentPipelineTest.java
      → test/application/pipeline/AgentPipelineTest.java
    test/app/pipeline/management/ScannerRegistryTest.java
      → test/application/pipeline/ScannerRegistryTest.java
    test/app/pipeline/management/ScannerRegistryIntegrationTest.java
      → test/application/pipeline/ScannerRegistryIntegrationTest.java

  Also updates:
    - package declarations in each test file
    - any internal imports referencing old test packages
```

### Execution steps

1. **Dry-run:** `./mvnw rewrite:dryRun -Drewrite.activeRecipe=com.hdekker.ai-workflow.phase5.test-moves`
2. **Apply:** `./mvnw rewrite:run -Drewrite.activeRecipe=com.hdekker.ai-workflow.phase5.test-moves`
3. **Compile:** `./mvnw compile test-compile -q`
4. **Test:** Run each moved test class individually:

| Test Class | Command |
|------------|---------|
| `AgentLifecycleServiceTest` | `./mvnw test -Dtest=AgentLifecycleServiceTest -q` |
| `AgentLifecycleServicePersistenceTest` | `./mvnw test -Dtest=AgentLifecycleServicePersistenceTest -q` |
| `AgentLifecycleServiceScannerRestoreTest` | `./mvnw test -Dtest=AgentLifecycleServiceScannerRestoreTest -q` |
| `AgentStatusServiceTest` | `./mvnw test -Dtest=AgentStatusServiceTest -q` |
| `ScannerServiceTest` | `./mvnw test -Dtest=ScannerServiceTest -q` |
| `ScannerObserverServiceTest` | `./mvnw test -Dtest=ScannerObserverServiceTest -q` |
| `AgentBuilderTest` | `./mvnw test -Dtest=AgentBuilderTest -q` |
| `AgentConfiguratorTest` | `./mvnw test -Dtest=AgentConfiguratorTest -q` |
| `AgentPipelineTest` | `./mvnw test -Dtest=AgentPipelineTest -q` |
| `ScannerRegistryTest` | `./mvnw test -Dtest=ScannerRegistryTest -q` |
| `ScannerRegistryIntegrationTest` | `./mvnw test -Dtest=ScannerRegistryIntegrationTest -q` |

---

## Phase 5.4: Delete old source packages

**Goal:** Remove the duplicate old source files that are no longer referenced.

### Files to delete

```
src/main/java/com/hdekker/ai_workflow/app/pipeline/AgentBuilder.java
src/main/java/com/hdekker/ai_workflow/app/pipeline/AgentConfigurator.java
src/main/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistry.java
src/main/java/com/hdekker/ai_workflow/usecases/AgentLifecycleUseCase.java
src/main/java/com/hdekker/ai_workflow/usecases/AgentStatusUsecase.java
src/main/java/com/hdekker/ai_workflow/usecases/FileCounter.java
src/main/java/com/hdekker/ai_workflow/usecases/Scanner.java
src/main/java/com/hdekker/ai_workflow/usecases/ScannerObserverUseCase.java
```

After deletion, the `app/` and `usecases/` directories will be empty and can be removed.

### Execution steps

1. **Verify no remaining references:** `grep -rn "com.hdekker.ai_workflow.usecases\.\|com.hdekker.ai_workflow.app\." src/main/java/ --include="*.java"` — should return nothing
2. **Delete files** (via `ChangeType` recipe or manual `rm`)
3. **Compile:** `./mvnw compile -q`
4. **Test:** Run the full application layer test suite:

| Test Scope | Command |
|------------|---------|
| All application layer | `./mvnw test -Dtest="application.**" -q` |
| All config | `./mvnw test -Dtest="config.**" -q` |
| All adapter | `./mvnw test -Dtest="adapter.**" -q` |

---

## Phase 5.5: Delete old test packages

**Goal:** Remove the duplicate old test files that have been moved.

### Files to delete

```
src/test/java/com/hdekker/ai_workflow/usecases/AgentLifecycleUseCaseTest.java
src/test/java/com/hdekker/ai_workflow/usecases/AgentLifecycleUseCasePersistenceTest.java
src/test/java/com/hdekker/ai_workflow/usecases/AgentLifecycleUseCaseScannerRestoreTest.java
src/test/java/com/hdekker/ai_workflow/usecases/AgentStatusUsecaseTest.java
src/test/java/com/hdekker/ai_workflow/usecases/ScannerTest.java
src/test/java/com/hdekker/ai_workflow/usecases/ScannerObserverUseCaseTest.java
src/test/java/com/hdekker/ai_workflow/app/pipeline/AgentBuilderTest.java
src/test/java/com/hdekker/ai_workflow/app/pipeline/AgentConfiguratorTest.java
src/test/java/com/hdekker/ai_workflow/app/pipeline/AgentPipelineTest.java
src/test/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistryTest.java
src/test/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistryIntegrationTest.java
```

### Execution steps

1. **Verify tests exist in new locations:** `find src/test -name "*ServiceTest.java" -o -name "*ServicePersistenceTest.java" -o -name "*ServiceScannerRestoreTest.java"`
2. **Delete files** (via `ChangeType` recipe or manual `rm`)
3. **Compile:** `./mvnw test-compile -q`
4. **Test:** Run all tests that were just moved (from their new locations)

---

## Phase 5.6: Final cleanup and verification

**Goal:** Ensure no stale references remain and the project is in a clean state.

### Verification checklist

1. **No old package imports:** `grep -rn "com.hdekker.ai_workflow.usecases\.\|com.hdekker.ai_workflow.app\." src/ --include="*.java"` → should be empty
2. **No old directories:** `find src -type d -name "usecases" -o -type d -name "app"` → should be empty (except `application/` which is correct)
3. **No old package in pom.xml:** Check for any `<exclude>` or `<include>` patterns referencing old packages
4. **No old package in Spring config:** Verify `@EntityScan` and component scanning don't reference old paths
5. **No stale Javadoc:** `grep -rn "usecases\.\|app\.pipeline\." src/ --include="*.java" | grep "@"` → should be empty

### Final test

1. **Compile:** `./mvnw compile -q`
2. **Unit tests:** `./mvnw test -q` (or run per-class as per AGENTS.md)
3. **Integration tests:** `./mvnw verify -DskipTests -q` (if external services available)

---

## Summary Table

| Step | Action | Method | Risk | Tests After |
|------|--------|--------|------|-------------|
| 5.1 | Update all imports | OpenRewrite `ChangePackage` | Low | Config + adapter + app layer tests |
| 5.2 | Fix Javadoc @links | Manual edit | None | No tests needed |
| 5.3 | Move test files | OpenRewrite `ChangePackage` | Medium | Each moved test class individually |
| 5.4 | Delete old source | `ChangeType` or rm | Low | Full app layer + adapter compile |
| 5.5 | Delete old test | `ChangeType` or rm | Low | Each moved test class individually |
| 5.6 | Final verification | grep + compile | None | Full test suite |
