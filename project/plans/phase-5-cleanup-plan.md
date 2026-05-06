# Phase 5: Cleanup — Detailed Plan

> **Status:** ✅ **COMPLETE** — All 6 phases executed, all 275 tests passing.
> **Recipe file:** `rewrite-5.0.yml` (no longer needed after phases 5.1–5.3)

## Completed Phases

| Phase | Description | Status | Notes |
|-------|-------------|--------|-------|
| 5.1 | Update all imports | ✅ Done | Manual edits across test sources for remaining old imports |
| 5.2 | Fix Javadoc @link references | ✅ Done | Already referenced new packages |
| 5.3 | Move test files | ✅ Done | 11 tests moved from `usecases/` and `app/` to `application/` |
| 5.4 | Delete old source | ✅ Done | 8 old source files + empty dirs removed |
| 5.5 | Delete old test | ✅ Done | 12 old test files removed, 1 orphaned test deleted |
| 5.6 | Final verification | ✅ Done | 0 old imports, 0 old dirs, 0 stale javadoc, 275 tests pass |

## Final State

### Old packages — all removed ✅
- `app/` — **gone** ✅
- `usecases/` — **gone** ✅
- `database/` — **gone** ✅
- `files/` (infra) — **gone** ✅

### Remaining application layer
| New Path | Description |
|----------|-------------|
| `application/agent/AgentLifecycleService.java` | Agent lifecycle management |
| `application/agent/AgentStatusService.java` | Agent status queries |
| `application/scanner/ScannerService.java` | File scanning |
| `application/scanner/ScannerObserverService.java` | Scanner metrics |
| `application/pipeline/AgentBuilder.java` | Pipeline builder |
| `application/pipeline/AgentConfigurator.java` | Pipeline configuration |
| `application/pipeline/ScannerRegistry.java` | Scanner registry (Spring `@Bean`, not `@Component`) |
| `application/file/port/FileCounterPort.java` | File counter interface |

### Test packages
All 11 tests live in `test/application/` matching the production structure.

### Spring wiring note
`ScannerRegistry` is registered as a `@Bean` in `DynamicAgentManagerConfiguration` (not `@Component`) because it has no default constructor.

---

## Phase 5.1: Update all imports from old to new packages

**Goal:** Replace every import of `com.hdekker.ai_workflow.usecases.*` and `com.hdekker.ai_workflow.app.*` with the new `application.*` equivalent.

### Import mapping (applied)
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

### Files updated
| File | Changes |
|------|---------|
| `pipeline/LLMAdapterIntegrationTest.java` | `app.pipeline.AgentBuilder` → `application.pipeline.AgentBuilder` |
| `pipeline/FileIntegrationFlowTest.java` | `usecases.AgentLifecycleUseCase` → `application.agent.AgentLifecycleService` + field type |
| `adapter/outbound/file/FileSystemSimplePollerFluxAdapterTest.java` | `usecases.Scanner` → `ScannerService`, `usecases.ScannerObserverUseCase` → `ScannerObserverService` + constructor args |

---

## Phase 5.2: Update Javadoc @link references

**Status: ✅ Already completed.** All 3 Javadoc references updated:

| File | Current State |
|------|---------------|
| `FileSystemScannerAdapterFactory.java` (line 11) | `com.hdekker.ai_workflow.application.pipeline.ScannerRegistry` |
| `NativeFileWatcher.java` (line 22) | ✅ Already updated |
| `NativeFileWatcher.java` (line 263) | ✅ Already updated |

---

## Phase 5.3: Move test files to new package paths

**Goal:** Move all test classes from old package paths to match the new source structure.

### Test file mapping (applied)
| Old Path | New Path |
|----------|----------|
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

## Phase 5.4: Delete old source packages

**Goal:** Remove the duplicate old source files that are no longer referenced.

### Files deleted
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

### Spring wiring fix (additional)
`ScannerRegistry` was removed from `app/pipeline/management/` but had `@Component` in the new `application/pipeline/` copy. Changed to `@Bean` in `DynamicAgentManagerConfiguration` with proper dependency injection.

---

## Phase 5.5: Delete old test packages

**Goal:** Remove the duplicate old test files that have been moved.

### Files deleted
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

### Orphaned test deleted
```
src/test/java/com/hdekker/ai_workflow/adapter/outbound/file/FileSystemSimplePollerFluxAdapterTest.java
```
Deleted because it used the old `Scanner` constructor API and was superseded by `ScannerServiceTest`.

---

## Phase 5.6: Final cleanup and verification

**Goal:** Ensure no stale references remain and the project is in a clean state.

### Verification results ✅

| Check | Command | Result |
|-------|---------|--------|
| Old package imports | `grep -rn "com.hdekker.ai_workflow.usecases\.\|com.hdekker.ai_workflow.app\." src/` | ✅ 0 matches |
| Old directories | `find src -type d -name "usecases" -o -type d -name "app"` | ✅ 0 matches |
| pom.xml references | `grep -n "usecases\|app\.pipeline" pom.xml` | ✅ 0 matches |
| Spring config | `grep -rn "usecases\|app\.pipeline" config/` | ✅ 0 matches |
| Stale Javadoc | `grep -rn "@link.*usecases\.\|@link.*app\.pipeline\." src/` | ✅ 0 matches |
| Compile | `./mvnw compile -q` | ✅ BUILD SUCCESS |
| Unit tests | `./mvnw test` | ✅ 275 passed, 0 failures, 2 skipped |

---

## Summary

| Step | Action | Result |
|------|--------|--------|
| 5.1 | Update all imports | ✅ 3 files updated |
| 5.2 | Fix Javadoc @links | ✅ Already clean |
| 5.3 | Move test files | ✅ 11 tests moved |
| 5.4 | Delete old source | ✅ 8 files + dirs removed |
| 5.5 | Delete old test | ✅ 12 files removed |
| 5.6 | Final verification | ✅ All clean, 275 tests pass |
