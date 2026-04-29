# Plan: Replace Micrometer Scanner Metrics with ScannerObserverUseCase

**Status**: **Complete**  
**Created**: 2026-04-29  
**Author**: AI Workflow Team  
**Last Updated**: 2026-04-29  

---

## 1. Objective

Remove the Micrometer dependency from scanner infrastructure and consolidate all scanner metrics concerns into an explicit `ScannerObserverUseCase`. This follows the project's hexagonal architecture pattern: metrics instrumentation becomes an internal use case rather than leaking a third-party framework into core adapters.

**Current problem**: `FileSystemScannerAdapter`, `NativeFileWatcher`, and `ScannerMetricsService` all depend on `MeterRegistry`, `Counter`, and `Gauge` from Micrometer. The UI layer (`ScannerListView`) depends on `ScannerMetricsService` to query Micrometer. This couples infrastructure code to an external observability framework and scatters metrics concerns across three classes.

**Target state**: A single `ScannerObserverUseCase` owns all scanner metrics (file count, discovered, unchanged). Core adapters pass no Micrometer types. The UI consumes metrics directly from the use case via a simple query method.

---

## 2. Current Architecture (To Be Replaced)

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI Layer                                                           │
│  ┌─────────────────┐    ┌──────────────────────┐                   │
│  │ ScannerListView │───▶│ ScannerMetricsService│                  │
│  │                 │    │ (reads MeterRegistry) │                  │
│  └─────────────────┘    └──────────┬───────────┘                  │
│                                    │ @EventListener                │
│  ┌──────────────────────────────┐  │                              │
│  │ ScannerMetricsPushService   │◀─┘                              │
│  └──────────────────────────────┘                                 │
└─────────────────────────────────────────────────────────────────────┘
                                ▲
                                │
┌─────────────────────────────────────────────────────────────────────┐
│  Scanner Infrastructure (couples to Micrometer)                     │
│  ┌──────────────────┐    ┌────────────────────┐                    │
│  │ ScannerRegistry  │───▶│ FileSystemScanner  │                    │
│  │ (passes          │    │ Adapter            │                    │
│  │  MeterRegistry)  │    │  • creates Counter │                    │
│  └──────────────────┘    │  • creates Gauge   │                    │
│                          │  • passes Counter  │                    │
│                          └────────┬───────────┘                    │
│                                   │                                 │
│                          ┌────────▼───────────┐                    │
│                          │  NativeFileWatcher  │                    │
│                          │  • increments Counter│                   │
│                          │  • updates AtomicLong│                   │
│                          └─────────────────────┘                    │
└─────────────────────────────────────────────────────────────────────┘
                                │
                        ┌───────▼───────┐
                        │  MeterRegistry │
                        │  (Micrometer)  │
                        └────────────────┘
```

### Files Involved (Current)

| File | Role | Micrometer Dependency |
|------|------|----------------------|
| `FileSystemScannerAdapter.java` | Creates counters, gauge, passes to watcher | `MeterRegistry`, `Counter`, `Gauge` |
| `NativeFileWatcher.java` | Increments counters, updates gauge | `Counter`, `AtomicLong` |
| `ScannerRegistry.java` | Passes `MeterRegistry` to adapters | `MeterRegistry` |
| `ScannerMetricsService.java` | Reads counters/gauges from registry | `MeterRegistry`, `Counter`, `Gauge` |
| `ScannerMetricsPushService.java` | Listens to events, pushes to UI | None (but depends on `ScannerMetricsService`) |
| `ScannerMetricsChangedEvent.java` | Event for metric changes | None |
| `ScannerMetricsSnapshot.java` | DTO for metrics snapshot | None |
| `ScannerListView.java` | UI view consuming metrics | `ScannerMetricsService` |

---

## 3. Target Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI Layer                                                           │
│  ┌─────────────────┐    ┌──────────────────────┐                   │
│  │ ScannerListView │───▶│ ScannerObserver      │                   │
│  │                 │    │ UseCase              │                   │
│  │                 │    │  • getMetrics(agentId)│                  │
│  │                 │    │  • getAllMetrics()     │                  │
│  └─────────────────┘    └──────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────┘
                                ▲
                                │ query / register
┌─────────────────────────────────────────────────────────────────────┐
│  Scanner Infrastructure (no Micrometer)                             │
│  ┌──────────────────┐    ┌────────────────────┐                    │
│  │ ScannerRegistry  │───▶│ FileSystemScanner  │                    │
│  │                  │    │ Adapter            │                    │
│  └──────────────────┘    │  • calls observer  │                    │
│                          │    on scan events  │                    │
│                          └────────┬───────────┘                    │
│                                   │                                 │
│                          ┌────────▼───────────┐                    │
│                          │  NativeFileWatcher  │                    │
│                          │  • calls observer   │                    │
│                          │    on events        │                    │
│                          └─────────────────────┘                    │
└─────────────────────────────────────────────────────────────────────┘
                                ▲
                                │ notify
┌─────────────────────────────────────────────────────────────────────┐
│  ScannerObserverUseCase (new)                                        │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  ScannerObserverUseCase                              │           │
│  │  • Record discovery, unchanged, file count           │           │
│  │  • Thread-safe concurrent map of per-agent metrics   │           │
│  │  • Expose query methods for UI                       │           │
│  │  • Optional: register callback for real-time push    │           │
│  └──────────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Single source of truth**: `ScannerObserverUseCase` is the only class that owns scanner metrics state. No more scattered `MeterRegistry` queries.
2. **In-memory metrics store**: Use `ConcurrentHashMap<String, AgentMetrics>` for thread-safe per-agent metric tracking. Simple, no external dependency.
3. **Observer pattern**: `FileSystemScannerAdapter` and `NativeFileWatcher` call `observer.recordDiscovery()`, `observer.recordUnchanged()`, `observer.updateFileCount()` instead of incrementing Micrometer counters.
4. **UI callback support**: `ScannerObserverUseCase` supports registering a `Consumer<ScannerMetricsChangedEvent>` callback (same event type) for real-time push to UI.
5. **MetricsSnapshot as return type**: `getMetrics(agentId)` returns `ScannerMetricsSnapshot` directly — no need for `ScannerMetricsService` to query `MeterRegistry`.

---

## 4. Implementation Phases

### Phase 1: Create ScannerObserverUseCase

**Goal**: Build the new use case class with metrics storage and query API.

**New file**: `src/main/java/com/hdekker/ai_workflow/usecases/ScannerObserverUseCase.java`

```java
package com.hdekker.ai_workflow.usecases;

// Package-private record for per-agent metrics
record AgentMetrics(
    long fileCount,
    long totalDiscovered,
    long unchanged
) {
    AgentMetrics withFileCount(long newCount) { return new AgentMetrics(newCount, totalDiscovered, unchanged); }
    AgentMetrics withDiscovered() { return new AgentMetrics(fileCount, totalDiscovered + 1, unchanged); }
    AgentMetrics withUnchanged() { return new AgentMetrics(fileCount, totalDiscovered, unchanged + 1); }
}

@Service
public class ScannerObserverUseCase {
    // ConcurrentHashMap<String, AgentMetrics> keyed by agentId
    // recordDiscovery(agentId), recordUnchanged(agentId), updateFileCount(agentId, count)
    // getMetrics(agentId) → ScannerMetricsSnapshot
    // getAllMetrics() → List<ScannerMetricsSnapshot>
    // registerRefreshCallback(Consumer<ScannerMetricsChangedEvent>)
    // pushToUI(ScannerMetricsChangedEvent)
}
```

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 1.1 | Create `ScannerObserverUseCase` with `AgentMetrics` record | `usecases/ScannerObserverUseCase.java` | ✅ |
| 1.2 | Implement `recordDiscovery()`, `recordUnchanged()`, `updateFileCount()` | Same file | ✅ |
| 1.3 | Implement `getMetrics(String agentId)` returning `ScannerMetricsSnapshot` | Same file | ✅ |
| 1.4 | Implement `getAllMetrics()` returning `List<ScannerMetricsSnapshot>` | Same file | ✅ |
| 1.5 | Implement callback registration and push (migrate from `ScannerMetricsService`) | Same file | ✅ |
| 1.6 | Add unit tests for `ScannerObserverUseCase` | `test/usecases/ScannerObserverUseCaseTest.java` | ✅ |

---

### Phase 2: Decouple FileSystemScannerAdapter and NativeFileWatcher

**Goal**: Remove Micrometer from `FileSystemScannerAdapter` and `NativeFileWatcher`. Replace counter/gauge operations with observer calls.

**Modified files**:
- `FileSystemScannerAdapter.java`
- `NativeFileWatcher.java`

**Changes to `FileSystemScannerAdapter`**:

| Change | Before | After |
|--------|--------|-------|
| Constructor params | `MeterRegistry meterRegistry, Consumer<ScannerMetricsChangedEvent>` | `ScannerObserverUseCase observer` |
| Counter creation | `meterRegistry.counter(...)` | N/A — observer handles it |
| Gauge creation | `Gauge.builder(...).register(meterRegistry)` | N/A — use `AtomicLong` passed to watcher |
| File discovery | `filesDiscoveredCounter.increment()` | `observer.recordDiscovery(agentId)` |
| File unchanged | `filesUnchangedCounter.increment()` | `observer.recordUnchanged(agentId)` |
| File count update | `fileCount.set(count)` | `observer.updateFileCount(agentId, count)` |
| Callback invocation | `metricsEventPublisher.accept(...)` | `observer.notifyUI(...)` or event publish |

**Changes to `NativeFileWatcher`**:

| Change | Before | After |
|--------|--------|-------|
| Constructor params | `Counter filesDiscoveredCounter, Counter filesUnchangedCounter, AtomicLong fileCount, Consumer<FileHistory> emitCallback` | `Consumer<String> onDiscovery, Consumer<String> onUnchanged, Consumer<String> onFileCount` |
| File discovery | `filesDiscoveredCounter.increment()` | `onDiscovery.accept(agentId)` |
| File unchanged | `filesUnchangedCounter.increment()` | `onUnchanged.accept(agentId)` |
| File count update | `fileCount.set(countFiles())` | `onFileCount.accept(agentId)` |

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 2.1 | Update `NativeFileWatcher` constructor to accept functional callbacks instead of Micrometer types | `files/NativeFileWatcher.java` | ✅ |
| 2.2 | Update `NativeFileWatcher` to call callbacks instead of incrementing counters | Same file | ✅ |
| 2.3 | Update `FileSystemScannerAdapter` constructor to accept `ScannerObserverUseCase` instead of `MeterRegistry` | `files/FileSystemScannerAdapter.java` | ✅ |
| 2.4 | Remove all Micrometer imports and counter/gauge field declarations | Same file | ✅ |
| 2.5 | Replace counter/gauge operations with observer method calls | Same file | ✅ |
| 2.6 | Update `ScannerRegistry` to inject `ScannerObserverUseCase` instead of `MeterRegistry` | `app/pipeline/management/ScannerRegistry.java` | ✅ |
| 2.7 | Pass observer to adapter in `ScannerRegistry.createForAgent()` | Same file | ✅ |

---

### Phase 3: Consolidate UI Metrics Layer

**Goal**: Replace `ScannerMetricsService` with `ScannerObserverUseCase`. Simplify `ScannerMetricsPushService`.

**Files to modify**:
- `ui/service/ScannerMetricsService.java` — **Replace** with `ScannerObserverUseCase` (already done in Phase 1)
- `ui/service/ScannerMetricsPushService.java` — **Update** to delegate to `ScannerObserverUseCase`
- `ui/views/ScannerListView.java` — **Update** dependency from `ScannerMetricsService` to `ScannerObserverUseCase`

**Changes to `ScannerMetricsPushService`**:

| Change | Before | After |
|--------|--------|-------|
| Dependency | `ScannerMetricsService` | `ScannerObserverUseCase` |
| `onScannerMetricsChanged()` | Calls `metricsService.pushToUI(event)` | Calls `observer.pushToUI(event)` |

**Changes to `ScannerListView`**:

| Change | Before | After |
|--------|--------|-------|
| Dependency injection | `ScannerMetricsService metricsService` | `ScannerObserverUseCase observer` |
| Grid "Files" column | `metricsService.getMetrics(info.agentId())` | `observer.getMetrics(info.agentId())` |
| Callback registration | `metricsService.registerRefreshCallback(...)` | `observer.registerRefreshCallback(...)` |
| Callback push | `metricsService.pushToUI(event)` (internal) | Already handled by observer |

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 3.1 | Update `ScannerMetricsPushService` to use `ScannerObserverUseCase` | `ui/service/ScannerMetricsPushService.java` | ✅ |
| 3.2 | Update `ScannerListView` to inject and use `ScannerObserverUseCase` | `ui/views/ScannerListView.java` | ✅ |
| 3.3 | Delete `ScannerMetricsService.java` (functionality merged into use case) | File removed | ✅ |
| 3.4 | Update unit tests for `ScannerListView` | `test/ui/views/ScannerListViewTest.java` | ✅ |

---

### Phase 4: Remove Micrometer Dependency

**Goal**: Remove Micrometer from `pom.xml` and clean up any remaining references.

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 4.1 | Remove `spring-boot-starter-actuator` from `pom.xml` | `pom.xml` | ✅ |
| 4.2 | Search for remaining `io.micrometer` imports | `grep -r` across codebase | ✅ |
| 4.3 | Remove any remaining Micrometer references | Source files | ✅ |
| 4.4 | Verify `MeterRegistry` is no longer injected anywhere | Build verification | ✅ |

---

### Phase 5: Update and Run Tests

**Goal**: Update all existing tests to work without Micrometer and add new tests.

**Test files to update**:

| Test File | Changes Required |
|-----------|-----------------|
| `FileSystemScannerAdapterMetricsTest.java` | **Rewrite** — test `ScannerObserverUseCase` directly instead of `MeterRegistry` |
| `FileSystemScannerAdapterTest.java` | Update constructor calls to pass `ScannerObserverUseCase` mock |
| `NativeFileWatcherMetricsTest.java` | **Rewrite** — test functional callbacks instead of counters |
| `ScannerRegistryTest.java` | Update constructor to use `ScannerObserverUseCase` mock |
| `ScannerRegistryIntegrationTest.java` | Update to use `ScannerObserverUseCase` |
| `ScannerMetricsServiceTest.java` | **Delete** — replaced by `ScannerObserverUseCaseTest.java` |
| `AgentPipelineTest.java` | Update if it references `MeterRegistry` |

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 5.1 | Rewrite `FileSystemScannerAdapterMetricsTest` to test use case | `test/files/FileSystemScannerAdapterMetricsTest.java` | ✅ |
| 5.2 | Rewrite `NativeFileWatcherMetricsTest` to test callbacks | `test/files/NativeFileWatcherMetricsTest.java` | ✅ |
| 5.3 | Update `FileSystemScannerAdapterTest` constructor calls | `test/files/FileSystemScannerAdapterTest.java` | ✅ |
| 5.4 | Update `ScannerRegistryTest` constructor calls | `test/app/pipeline/management/ScannerRegistryTest.java` | ✅ |
| 5.5 | Update `ScannerRegistryIntegrationTest` | `test/app/pipeline/management/ScannerRegistryIntegrationTest.java` | ✅ |
| 5.6 | Delete `ScannerMetricsServiceTest.java` | File removed | ✅ |
| 5.7 | Create `ScannerObserverUseCaseTest.java` with comprehensive tests | `test/usecases/ScannerObserverUseCaseTest.java` | ✅ |
| 5.8 | Run `./mvnw test` — verify all pass | Console output | ✅ (287 tests, 0 failures, 2 skipped) |
| 5.9 | Run `./mvnw verify` — full build with integration tests | Console output | ✅ |

---

## 5. Files Summary

### New Files (1)

| File | Purpose |
|------|---------|
| `src/main/java/com/hdekker/ai_workflow/usecases/ScannerObserverUseCase.java` | Replaces `ScannerMetricsService`; owns all scanner metrics state and query API |
| `src/test/java/com/hdekker/ai_workflow/usecases/ScannerObserverUseCaseTest.java` | Unit tests for the use case |

### Modified Files (8)

| File | Change |
|------|--------|
| `src/main/java/.../files/FileSystemScannerAdapter.java` | Remove Micrometer, accept `ScannerObserverUseCase` |
| `src/main/java/.../files/NativeFileWatcher.java` | Remove `Counter`, accept functional callbacks |
| `src/main/java/.../app/pipeline/management/ScannerRegistry.java` | Remove `MeterRegistry`, inject `ScannerObserverUseCase` |
| `src/main/java/.../ui/service/ScannerMetricsPushService.java` | Delegate to `ScannerObserverUseCase` |
| `src/main/java/.../ui/views/ScannerListView.java` | Inject `ScannerObserverUseCase` |
| `pom.xml` | Remove `spring-boot-starter-actuator` |
| `src/test/java/.../files/FileSystemScannerAdapterMetricsTest.java` | Rewrite to test use case |
| `src/test/java/.../files/NativeFileWatcherMetricsTest.java` | Rewrite to test callbacks |

### Deleted Files (2)

| File | Reason |
|------|--------|
| `src/main/java/.../ui/service/ScannerMetricsService.java` | Functionality merged into `ScannerObserverUseCase` |
| `src/test/java/.../ui/service/ScannerMetricsServiceTest.java` | Replaced by `ScannerObserverUseCaseTest.java` |

### Unchanged Files (keep as-is)

| File | Reason |
|------|--------|
| `ScannerMetricsChangedEvent.java` | Event contract — still valid for UI push |
| `ScannerMetricsSnapshot.java` | DTO — still valid, returned by use case |
| `ScannerInfo.java` | Scanner info DTO — unrelated |

---

## 6. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| `ScannerMetricsService` has internal `pushToUI()` used by `ScannerMetricsPushService` | Medium | Migrate callback registration to `ScannerObserverUseCase` in Phase 3 |
| `NativeFileWatcher` has complex emit callback that re-reads file | Low | Preserve emit callback signature; observer calls are separate |
| `FileSystemScannerAdapter` has multiple constructor callers (tests + production) | Medium | Update all call sites in one phase |
| `spring-boot-starter-actuator` may be pulled by other dependencies (Spring AI, Vaadin) | Low | Check dependency tree with `./mvnw dependency:tree`; only remove if not transitively needed |
| Integration tests may rely on actuator endpoints for health checks | Low | Verify integration test requirements before removing actuator |
| `AgentPipelineTest` may create `MeterRegistry` beans for test context | Low | Update test configuration |

---

## 7. Validation Criteria

- [x] `io.micrometer` imports removed from all production source files
- [x] `MeterRegistry` no longer injected in any Spring bean
- [x] `ScannerObserverUseCase` is the sole class creating and querying scanner metrics
- [x] `FileSystemScannerAdapter` constructor has no `MeterRegistry` parameter
- [x] `NativeFileWatcher` has no `Counter` type in its constructor
- [x] `ScannerMetricsService.java` is deleted
- [x] All unit tests pass (`./mvnw test`)
- [x] All integration tests pass (`./mvnw verify`)
- [x] UI loads without errors and displays scanner file counts correctly
- [x] Real-time push updates still work (files discovered triggers UI refresh)

---

## 8. Execution Order

```
Phase 1: Create ScannerObserverUseCase (with tests)
    ↓
Phase 2: Decouple FileSystemScannerAdapter & NativeFileWatcher from Micrometer
    ↓
Phase 3: Consolidate UI Metrics Layer (replace ScannerMetricsService)
    ↓
Phase 4: Remove Micrometer dependency (pom.xml + cleanup)
    ↓
Phase 5: Update & Run All Tests
```

---

## 9. Dependencies Between Phases

| Phase | Depends On |
|-------|-----------|
| Phase 1 | — (standalone) |
| Phase 2 | Phase 1 (use case must exist) |
| Phase 3 | Phase 2 (adapter must pass observer) |
| Phase 4 | Phase 3 (all Micrometer refs removed from code) |
| Phase 5 | Phase 4 (final test sweep) |

---

## 10. Notes

- The `ScannerMetricsChangedEvent` type is **retained** — it serves as the contract between scanner infrastructure and the UI push mechanism. The use case publishes it, the push service listens, and the view registers a callback.
- If `spring-boot-starter-actuator` is needed for other actuator features (health endpoints, info, etc.), keep it but remove the `MeterRegistry` usage specifically. Run `./mvnw dependency:tree` to verify.
- The `ScannerMetricsSnapshot` DTO is **retained** — it's the return type of `ScannerObserverUseCase.getMetrics()`. No changes needed.
- This refactor aligns with the project's hexagonal architecture: metrics become an internal use case concern rather than a framework leak.
