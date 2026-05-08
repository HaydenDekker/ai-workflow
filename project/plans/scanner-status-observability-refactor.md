# Plan: Scanner Status & Observability Refactor

## Problem

The scanner status system is tangled across concerns. `ScannerObserverService` mixes metrics storage with UI push messaging. The `ScannerStatus` enum conflates file event results (Emitted/Filtered) with scanner lifecycle states (EMITTING_INITIAL/IDLE). The push chain is correct but the *data it carries* is not — status values are computed in the application layer but represent display concerns that belong in the UI.

There is no explicit use case for scanner observability. `ScannerService` calls the metrics port and status push directly — the observability concern is implicit, buried in `processRawEvent()`. There's no single orchestrator that coordinates "record metrics + publish event" as a unit.

**Symptoms:**
- `ScannerService.processRawEvent()` must know about `ScannerStatus` values that exist only for UI display
- `notifyStatusChange()` and `observer.recordEvent()` fire duplicate status updates for the same event
- The 2s FILTERED reset and 30s IDLE timeout live in the application layer but govern UI display timing
- `ScannerMetricsPort` declares both metrics queries (`getMetrics()`) and push messaging (`pushToUI()`) — two different concerns in one port
- `ScannerMetricsEvent` carries `ScannerStatus` (display concern) alongside `ScannerEventType` (domain concept)
- No explicit use case — observability is an implicit side-effect of scanner processing

**Root cause**: Status was designed as a scanner-internal state machine. It evolved to also carry UI display state. The two uses collided. The observability concern was never extracted into its own use case.

## Target

Split the concerns cleanly:

1. **`ScannerFileResult`** (new domain enum) — what happened to a file: `EMITTED`, `FILTERED`, `ERROR`. Replaces the status values that were tied to individual file events.
2. **`ScannerObservabilityUseCase`** (new orchestrator) — coordinates metrics recording + event publishing. Single entry point for all observability operations. Replaces the implicit observability in `ScannerService`.
3. **`ScannerMetricsPort`** — pure metrics store. Tracks file counts, discovered counts, emission timestamps. No push, no callbacks.
4. **`ScannerEventPort`** (new port) — push-only interface for event publishing. Separates the driven adapter (metrics) from the driving adapter mechanism (push).
5. **`ScannerMetricsService`** — implements `ScannerMetricsPort`. Renamed from `ScannerObserverService`. Pure metrics, no callbacks.
6. **`ScannerEventBus`** — implements `ScannerEventPort`. Receives events, pushes to UI callbacks.
7. **`ScannerService`** — simplified. Calls `ScannerObservabilityUseCase` instead of metrics + push directly. Keeps only scanner-internal lifecycle (`EMITTING_INITIAL`, `ERROR`).
8. **`ScannerListView`** — owns display state. Maps `ScannerFileResult` to visual states (`Active` for 10s, `Filtered` for 2s, `Error` until cleared, `Idle` otherwise). The idle timer becomes a UI concern.

### Hexagonal Flow

```
ScannerService (application)
  └─ observability.recordFileEvent(agentId, eventType, fileCount, folderPath)
       └─ ScannerObservabilityUseCase
            ├─ metrics.recordEvent(agentId, eventType, fileCount)     ← ScannerMetricsPort
            └─ eventBus.publish(agentId, result, folderPath)          ← ScannerEventPort
                 └─ callbacks.forEach(cb → cb.accept(event))
                      └─ ScannerListView.refreshCallback
                           └─ ui.access(() → refreshScanners())

ScannerController / ScannerService (UI)
  └─ metrics.getMetrics(agentId)                                     ← ScannerMetricsPort
  └─ scanner.toInfo()                                                ← ScannerInfo (lifecycle status)
```

### Why This Works

| Concern | Current Owner | New Owner | Rationale |
|---------|---------------|-----------|----------|
| File event result (Emitted/Filtered/Error) | `ScannerStatus` (enum) | `ScannerFileResult` (new enum) | Domain concept, not a scanner lifecycle state |
| Observability orchestration | Implicit in `ScannerService` | `ScannerObservabilityUseCase` | Explicit use case, single entry point |
| Metrics storage (count, timestamps) | `ScannerObserverService` | `ScannerMetricsService` via `ScannerMetricsPort` | Pure data, driven adapter |
| UI push notifications | `ScannerObserverService` (callbacks) | `ScannerEventBus` via `ScannerEventPort` | Driving adapter mechanism, separated from metrics |
| Display state (Active/Idle/Filtered) | Application layer (`ScannerStatus`) | UI layer (`ScannerListView`) | Presentation concern — timer durations are UI choices |
| Scanner lifecycle (initial scan, error) | `ScannerService` | `ScannerService` | Scanner-internal state the application layer needs |
| Idle timeout | `ScannerService` (30s) | UI layer (10s for Emitted, 2s for Filtered) | Display timing, not scanner behavior |

### Why This Works

| Concern | Current Owner | New Owner | Rationale |
|---------|---------------|-----------|-----------|
| File event result (Emitted/Filtered/Error) | `ScannerStatus` (enum) | `ScannerFileResult` (new enum) | Domain concept, not a scanner lifecycle state |
| Metrics storage (count, timestamps) | `ScannerObserverService` | `ScannerMetricsPort` / `ScannerMetricsService` | Pure data, no push |
| UI push notifications | `ScannerObserverService` (callbacks) | `ScannerEventBus` | Driving adapter mechanism, not metrics |
| Display state (Active/Idle/Filtered) | Application layer (`ScannerStatus`) | UI layer (`ScannerListView`) | Presentation concern — timer durations are UI choices |
| Scanner lifecycle (initial scan, error) | `ScannerService` | `ScannerService` | Scanner-internal state the application layer needs |
| Idle timeout | `ScannerService` (30s) | UI layer (10s for Emitted, 2s for Filtered) | Display timing, not scanner behavior |

## Hexagonal Structure

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │                        DOMAIN LAYER                                  │
  │  ScannerFileResult  │  ScannerMetrics  │  ScannerEventType          │
  └─────────────────────────────────────────────────────────────────────┘
                                 ▲
  ┌──────────────────────────────┴──────────────────────────────────────┐
  │                     APPLICATION LAYER                                │
  │                                                                      │
  │  ┌──────────────────────────────────────────────────────┐           │
  │  │          ScannerObservabilityUseCase                  │           │
  │  │  recordFileEvent() → metrics + eventBus               │           │
  │  │  recordEmission()    → metrics + eventBus             │           │
  │  │  getMetrics()        → metrics                        │           │
  │  │  transitionToError() → metrics + eventBus             │           │
  │  └──────────────┬──────────────────────┬─────────────────┘           │
  │                 │                      │                             │
  │  ┌──────────────▼──────┐  ┌───────────▼──────────┐                  │
  │  │  ScannerMetricsPort  │  │  ScannerEventPort    │  ← PORTS        │
  │  │  (query + store)     │  │  (push only)         │                  │
  │  └──────────────────────┘  └──────────────────────┘                  │
  │                                                                      │
  │  ┌────────────────────────────┐  ┌─────────────────────────────┐    │
  │  │  ScannerMetricsService     │  │  ScannerEventBus            │    │
  │  │  (implements MetricsPort)  │  │  (implements EventPort)     │    │
  │  └────────────────────────────┘  └─────────────────────────────┘    │
  │                                                                      │
  │  ┌──────────────────────────────────────────────────────┐           │
  │  │                   ScannerService                     │           │
  │  │  (calls ObservabilityUseCase, not ports directly)    │           │
  │  └──────────────────────────────────────────────────────┘           │
  └──────────────────────────────┬──────────────────────────────────────┘
                                 │
  ┌──────────────────────────────┴──────────────────────────────────────┐
  │                        ADAPTER LAYER                                 │
  │  Inbound: ScannerListView │ ScannerController │ UI ScannerService  │
  │  Outbound: FileSystemFileCounter (FileCounterPort)                  │
  └─────────────────────────────────────────────────────────────────────┘
```

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `ScannerFileResult` | Value object: EMITTED, FILTERED, ERROR — what happened to a file |
| **Domain** | `ScannerMetrics`, `ScannerEventType` | Value objects for metrics and event typing |
| **Application (use case)** | `ScannerObservabilityUseCase` | Orchestrates metrics + event publishing. Single entry point for observability. |
| **Application (port)** | `ScannerMetricsPort` | Driven adapter port — pure metrics queries (no push, no callbacks) |
| **Application (port)** | `ScannerEventPort` | Driving adapter port — push-only interface for event publishing |
| **Application (service)** | `ScannerMetricsService` | Implements `ScannerMetricsPort` — stores file counts, discovered counts, emission timestamps |
| **Application (service)** | `ScannerEventBus` | Implements `ScannerEventPort` — receives events, pushes to UI callbacks |
| **Application (use case)** | `ScannerService` | Per-scanner orchestrator: calls `ScannerObservabilityUseCase`, owns scanner lifecycle |
| **Inbound adapter** | `ScannerListView` | Vaadin view — maps file result to display state with UI-owned timers |
| **Inbound adapter** | `ScannerService` (UI) | Thin wrapper around `ScannerRegistry` for the view |
| **Outbound adapter** | `FileSystemFileCounter` | Walks real filesystem via `Files.walk()` |

## Existing Tests

| Test Class | Layer | What it covers | Status |
|------------|-------|---------------|--------|
| `ScannerObserverServiceTest` | Application | Metrics tracking, callback registration, concurrency, fileCount | ✅ Green — 33 tests |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, full scan, raw event processing, status transitions | ✅ Green — 13 tests |
| `ScannerServiceTest` (UI) | Inbound adapter | `getAllScannerInfos()` conversion, error handling | ✅ Green — 6 tests |
| `ScannerListViewTest` | Inbound adapter | Route and page title annotations | ⚠️ Thin — 3 tests, no rendering/data tests |
| `ScannerRegistryTest` | Application | Registry CRUD operations | ✅ Green — 16 tests |
| `ScannerRegistryIntegrationTest` | Application | Full scanner lifecycle with registry | ✅ Green — 9 tests |
| `AgentPipelineTest` | Application | Pipeline wiring | ✅ Green — 3 tests |

## Test Gaps

- **No test for `ScannerFileResult` enum** — new domain concept needs its own tests
- **No test for `ScannerObservabilityUseCase`** — new orchestrator, needs integration tests (metrics + event bus)
- **No test for `ScannerEventPort` / `ScannerEventBus`** — new port and service, needs push/callback tests
- **No test for `ScannerMetricsService`** — renamed from `ScannerObserverService`, needs metrics-only tests (no callbacks)
- **No test for UI display state mapping** — `ScannerListView` doesn't test the Active/Filtered/Error/Idle mapping
- **Status transition tests in `ScannerServiceTest`** will need updating for new event model

## Phases

### Phase 0: Create `ScannerFileResult` domain enum
- [ ] Create `ScannerFileResult` enum in `domain/scanner/` with values: `EMITTED`, `FILTERED`, `ERROR`
- [ ] Add `from(ScannerEventType)` factory method: CREATION/MODIFICATION → EMITTED, UNCHANGED → FILTERED, null/DELETE → EMITTED
- [ ] Add Javadoc explaining this is a file-level result, not a scanner lifecycle state
- [ ] Compile and run full test suite — no existing code references this yet, should be clean

**Files:** `src/main/java/.../domain/scanner/ScannerFileResult.java`

### Phase 1: Split ports — metrics vs events
- [ ] Remove `pushToUI()` and `recordEmission()` from `ScannerMetricsPort` interface
- [ ] Keep `recordEvent()` (simplified — no status parameter, just eventType + fileCount) and query methods
- [ ] Create `ScannerEventPort` interface in `application/scanner/port/`
  - `void publish(String agentId, ScannerFileResult result, String folderPath, String errorMessage)`
  - `void registerCallback(Consumer<ScannerFileEvent> callback)`
  - `void unregisterCallback(Consumer<ScannerFileEvent> callback)`
- [ ] Define `ScannerFileEvent` record: `agentId`, `ScannerFileResult result`, `folderPath`, `errorMessage`
- [ ] Compile and verify compile errors point to the right places (push calls now unresolved)

**Files:** `src/main/java/.../application/scanner/port/ScannerMetricsPort.java`, `src/main/java/.../application/scanner/port/ScannerEventPort.java`, `src/main/java/.../domain/scanner/ScannerFileEvent.java`

### Phase 2: Implement port adapters — metrics + event bus
- [ ] Rename `ScannerObserverService` → `ScannerMetricsService`
  - Remove callback registration/unregistration methods
  - Remove `pushToUI()` calls from `recordEvent()`
  - Remove `ScannerStatus` parameter from `recordEvent()` — no longer needed for push
  - Verify `ScannerMetricsPort` is implemented cleanly
- [ ] Create `ScannerEventBus` as a `@Service` implementing `ScannerEventPort`
  - `publish()` — iterates callbacks with `Consumer<ScannerFileEvent>`
  - `registerCallback()` / `unregisterCallback()` with `CopyOnWriteArrayList`
  - Individual callback failures caught and logged (don't break other callbacks)
- [ ] Compile and verify

**Files:** `src/main/java/.../application/scanner/ScannerMetricsService.java` (renamed), `src/main/java/.../application/scanner/ScannerEventBus.java`

### Phase 3: Create `ScannerObservabilityUseCase`
- [ ] Create `ScannerObservabilityUseCase` as a `@Service` in `application/scanner/`
- [ ] Inject both `ScannerMetricsPort` and `ScannerEventPort`
- [ ] Implement methods:
  - `recordFileEvent(agentId, eventType, fileCount, folderPath)` → metrics + eventBus
  - `recordEmission(agentId)` → metrics + eventBus
  - `getMetrics(agentId)` → metrics only
  - `transitionToError(agentId, message)` → metrics + eventBus
- [ ] Add `ScannerFileResult.from(eventType)` mapping inside `recordFileEvent()`
- [ ] Compile and verify
- [ ] **Tests**: `ScannerObservabilityUseCaseTest` — verify both ports called with correct data for each method

**Files:** `src/main/java/.../application/scanner/ScannerObservabilityUseCase.java`, `src/test/java/.../application/scanner/ScannerObservabilityUseCaseTest.java`

### Phase 4: Refactor `ScannerService` — call use case instead of ports
- [ ] Replace `ScannerMetricsPort observer` dependency with `ScannerObservabilityUseCase observability`
- [ ] Replace `observer.recordEvent(agentId, eventType, status, path, msg, count)` with:
  - `observability.recordFileEvent(agentId, eventType, fileCount, folderPath)`
- [ ] Replace `observer.recordEmission(agentId)` with:
  - `observability.recordEmission(agentId)`
- [ ] Replace `observer.recordEvent(agentId, null, ERROR, null, msg)` with:
  - `observability.transitionToError(agentId, msg)`
- [ ] Remove `notifyStatusChange()` calls for EMITTING_UPDATES, FILTERED in `processRawEvent()`
- [ ] Remove `filteredResetScheduler` and `cancelAndScheduleFilteredReset()` — timer moves to UI
- [ ] Keep `notifyStatusChange()` for EMITTING_INITIAL (initSource) and ERROR (scanner-internal lifecycle)
- [ ] Remove `idleChecker` idle-to-IDLE transition — idle is now a UI concern
- [ ] Update `toInfo()` — return `ScannerFileResult`-based status for the DTO, or return null/IDLE (UI derives display state from events)
- [ ] Compile and verify

**Files:** `src/main/java/.../application/scanner/ScannerService.java`

### Phase 5: Update `ScannerRegistry` wiring
- [ ] Inject `ScannerObservabilityUseCase` instead of `ScannerMetricsPort`
- [ ] Pass use case to `ScannerService` constructor
- [ ] Update `listAll()` to return `ScannerInfo` with file result (not scanner status)
- [ ] Update `DynamicAgentManagerConfiguration` — wire use case instead of observer
- [ ] Compile and verify

**Files:** `src/main/java/.../application/pipeline/ScannerRegistry.java`, `src/main/java/.../config/DynamicAgentManagerConfiguration.java`

### Phase 6: Update inbound adapters
- [ ] Update `ScannerController.listScanners()` — reads from use case `getMetrics()` for fileCount
- [ ] Update UI `ScannerService.getAllScannerInfos()` — same
- [ ] Update `ScannerInfoDTO` — replace `status` (String) with `fileResult` (String) if needed, or keep status for backward compatibility and derive from fileResult
- [ ] Compile and verify

**Files:** `src/main/java/.../adapter/inbound/rest/controller/ScannerController.java`, `src/main/java/.../adapter/inbound/ui/service/ScannerService.java`, `src/main/java/.../adapter/inbound/rest/dto/ScannerInfoDTO.java`

### Phase 7: Rewrite `ScannerListView` — UI owns display state
- [ ] Replace callback registration from `observer.registerRefreshCallback()` to `eventPort.registerCallback()`
- [ ] Add per-agent timer map: `Map<String, ScheduledFuture<?>> displayTimers`
- [ ] On `EMITTED` event: show "Active" (blue), schedule timer for 10s → "Idle" (green)
- [ ] On `FILTERED` event: show "Filtered" (orange), schedule timer for 2s → "Idle" (green)
- [ ] On `ERROR` event: show "Error" (red), no auto-reset (persists until cleared)
- [ ] Remove `renderStatusComponent()` status switch — replace with display state from timer map
- [ ] Keep `grid.setItems()` refresh for data; use `grid.getDataProvider().refreshItem()` or `grid.getDataCommunicator().reset()` for status-only updates to avoid full re-render
- [ ] Add `UI.access()` for timer callbacks
- [ ] Compile and verify

**Files:** `src/main/java/.../adapter/inbound/ui/view/ScannerListView.java`

### Phase 8: Rewrite tests
- [ ] Rename `ScannerObserverServiceTest` → `ScannerMetricsServiceTest`
- [ ] Remove callback tests (moved to `ScannerEventBusTest`)
- [ ] Add `ScannerEventBusTest` — publish, callback registration, callback failure isolation, concurrency
- [ ] Add `ScannerObservabilityUseCaseTest` — both ports called correctly for each method
- [ ] Add `ScannerFileResultTest` — from() factory, enum values
- [ ] Update `ScannerServiceTest` — verify use case called with correct args (not ports directly)
- [ ] Update `ScannerRegistryTest` / `ScannerRegistryIntegrationTest` — new constructor params
- [ ] Update UI `ScannerServiceTest` — new DTO fields
- [ ] Run full scanner test suite — all green

**Files:** `src/test/java/.../application/scanner/ScannerMetricsServiceTest.java`, `src/test/java/.../application/scanner/ScannerEventBusTest.java`, `src/test/java/.../application/scanner/ScannerObservabilityUseCaseTest.java`, `src/test/java/.../application/scanner/ScannerFileResultTest.java`, `src/test/java/.../application/scanner/ScannerServiceTest.java`, `src/test/java/.../adapter/inbound/ui/service/ScannerServiceTest.java`, `src/test/java/.../application/pipeline/ScannerRegistryTest.java`, `src/test/java/.../application/pipeline/ScannerRegistryIntegrationTest.java`

### Phase 9: Update documentation
- [ ] Update `dpr-scanner-observability.md` — new architecture, use case, ports, UI-owned timers
- [ ] Update `dpr-scanner-concept.md` — `ScannerFileResult` replaces status for file events
- [ ] Update `dpr-agent-scanner-relationship.md` if needed
- [ ] Update `design-principles.md` master index if new DPRs created

**Files:** `project/docs/dpr-scanner-observability.md`, `project/docs/dpr-scanner-concept.md`

## Design Decisions

### ScannerObservabilityUseCase as orchestrator

`ScannerService` should not call ports directly for observability. The observability concern — "record what happened to this file" — is a distinct use case that coordinates two independent services (metrics store + event publisher). The use case:

1. Is the **single entry point** for all observability operations
2. Decides **what** to record and **what** to publish (scanner doesn't decide)
3. Can be **mocked** in `ScannerService` tests (scanner tests don't need to know about ports)
4. Can be **tested** with mock ports (use case tests verify orchestration without real services)
5. Follows the hexagonal convention: **use case → ports ← adapters**

```
// ScannerService depends on ONE use case, not two ports:
private final ScannerObservabilityUseCase observability;

// Inside processRawEvent():
observability.recordFileEvent(agentId, eventType, fileCount, folderPath);

// The use case orchestrates both concerns:
public void recordFileEvent(...) {
    metrics.recordEvent(agentId, eventType, fileCount);       // ScannerMetricsPort
    eventBus.publish(agentId, ScannerFileResult.from(type), ...); // ScannerEventPort
}
```

### ScannerFileResult vs ScannerStatus

`ScannerStatus` was a scanner-level state machine (IDLE → EMITTING_INITIAL → EMITTING_UPDATES → FILTERED → ERROR). It conflated:
- **What happened to a file** (emitted, filtered, error) — this is `ScannerFileResult`
- **What phase the scanner is in** (initial scan, steady state) — this is scanner-internal lifecycle in `ScannerService`
- **What the UI should display** (Active, Idle, Filtered) — this is presentation logic in `ScannerListView`

`ScannerFileResult` captures only the file-level outcome. The scanner lifecycle stays in `ScannerService` (EMITTING_INITIAL, ERROR are scanner concerns). The display state is pure UI.

### Why not keep ScannerStatus?

`ScannerStatus` can remain for the scanner-internal lifecycle (EMITTING_INITIAL, ERROR). The file-event statuses (EMITTING_UPDATES, FILTERED, IDLE) are replaced by `ScannerFileResult` + UI timers. The enum shrinks from 5 values to 2 (or is removed entirely if the scanner no longer needs it — `toInfo()` can return the display string directly).

### Timer durations

| Event | Display State | Duration | Color |
|-------|--------------|----------|-------|
| EMITTED | "Active" | 10 seconds | Blue (`#4a90d9`) |
| FILTERED | "Filtered" | 2 seconds | Orange (`#e67e22`) |
| ERROR | "Error" | Until cleared | Red (`#e74c3c`) |
| (no event / timer expired) | "Idle" | Indefinite | Green (`#27ae60`) |

These are UI presentation choices, not application-layer constants.

### Two ports, not one

`ScannerMetricsPort` was doing two things: storing data (`getMetrics()`) and pushing events (`pushToUI()`). Ports should model a single concern:

| Port | Direction | Concern |
|------|-----------|---------|
| `ScannerMetricsPort` | Driven (app → port → service) | Store and query metrics |
| `ScannerEventPort` | Driving (app → port → adapters) | Push events to interested adapters |

The use case orchestrates both. Each port is independently swappable.

### Backward compatibility

`ScannerInfoDTO.status` can remain as a String field. `toInfo()` derives it from `ScannerFileResult` (e.g. EMITTED → "EMITTING_UPDATES", FILTERED → "FILTERED", null → "IDLE"). This keeps REST contracts stable while the internal model simplifies.

## Risks

| Risk | Mitigation |
|------|-----------|
| UI timers may not fire reliably (Vaadin thread safety) | All timer callbacks wrapped in `UI.access()`; tested with integration tests |
| ScannerRegistry wiring changes break configuration | Phase-by-phase TDD — compile + tests green after each phase |
| `ScannerStatus` removal breaks REST API | Keep `ScannerInfoDTO.status` as String — derive from `ScannerFileResult` |
| Error recovery path lost without `ScannerStatus.ERROR` | `ScannerService` keeps `transitionToError()` / `recover()` as internal lifecycle; ERROR event emitted to event bus |

## Implementation Summary (filled after completion)

| Phase | Status | Changes |
|-------|--------|---------|
| 0 | ⬜ | `ScannerFileResult` domain enum |
| 1 | ⬜ | Split ports — `ScannerMetricsPort` (metrics) + `ScannerEventPort` (push) |
| 2 | ⬜ | Implement port adapters — `ScannerMetricsService` + `ScannerEventBus` |
| 3 | ⬜ | Create `ScannerObservabilityUseCase` orchestrator |
| 4 | ⬜ | Refactor `ScannerService` — call use case instead of ports |
| 5 | ⬜ | Update `ScannerRegistry` wiring |
| 6 | ⬜ | Update inbound adapters |
| 7 | ⬜ | Rewrite `ScannerListView` with UI timers |
| 8 | ⬜ | Rewrite tests |
| 9 | ⬜ | Update documentation |

**Total new files**: ~6 (`ScannerFileResult.java`, `ScannerFileEvent.java`, `ScannerEventPort.java`, `ScannerEventBus.java`, `ScannerObservabilityUseCase.java`, `ScannerMetricsService.java`)
**Total renamed files**: ~1 (`ScannerObserverService.java` → `ScannerMetricsService.java`)
**Total modified files**: ~18 (ScannerService, ScannerRegistry, controllers, views, configuration, tests, docs)

## Notes

- **TDD order**: Each phase ends with green tests. Phase 0 adds a new domain enum (no existing code broken). Phases 1-2 split existing code. Phase 3 adds the use case orchestrator. Phases 4-7 rewire. Phase 8 rewrites tests to match new model.
- **`ScannerStatus` enum**: Can be kept for scanner-internal use (EMITTING_INITIAL, ERROR) or removed entirely. Decision made during Phase 4.
- **`ScannerObservabilityUseCase`**: Follows the `<Action>UseCase` naming convention. It orchestrates two ports — the naming reflects the concern (observability) not a single action. This is consistent with `AgentStatusService` in the observability domain.
- **Idle checker removal**: The 30s idle timeout in `ScannerService` becomes a UI concern. If other consumers need idle detection (e.g., REST API), keep a lightweight version in `ScannerMetricsService`.
- **`ScannerMetricsEvent`**: Replaced by `ScannerFileEvent` in the event bus. `ScannerMetrics` record remains for pure metrics snapshots.
- **Previous plans**: `scanner-status-rework.md` and `scanner-event-refactor.md` are Draft state. This plan supersedes them with a concrete, testable approach.
