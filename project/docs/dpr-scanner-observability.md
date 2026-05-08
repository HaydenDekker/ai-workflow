# DPR: Scanner Observability

> **Context**: This document describes the observability layer built on top of the scanner architecture — how metrics are tracked, how real-time UI updates are pushed, and the full data flow from file system events to the Vaadin dashboard. The architecture separates metrics storage from UI push notifications, and moves display-state timers (Active, Filtered, Idle) into the UI layer.

---

## Overview

Scanners are the bridge between the file system and the AI pipeline. They watch directories for file changes and emit `FileHistory` events through a reactive stream. Observability lets operators see what scanners are doing in real time: the actual file count per directory, the scanner's current status, and timestamps of the last emission.

The observability layer now consists of **four** parts:

1. **Metrics tracking** — `ScannerMetricsService` implements `ScannerMetricsPort` — pure in-memory storage for file counts, discovered counts, and emission timestamps
2. **Event push** — `ScannerEventBus` implements `ScannerEventPort` — receives file events and pushes to UI callbacks
3. **Orchestration** — `ScannerObservabilityUseCase` coordinates metrics recording + event publishing as a single entry point
4. **Real-time UI** — `ScannerListView` maps `ScannerFileResult` to visual states (`Active` for 10s, `Filtered` for 2s, `Error` until cleared, `Idle` otherwise) with **UI-owned timers**

### ScannerFileResult (Domain Enum)

File event results are now a distinct domain concept, separate from scanner lifecycle state:

| Value | Description |
|-------|-------------|
| `EMITTED` | File was processed and emitted through the reactive stream |
| `FILTERED` | File was rejected by hash filter (unchanged / already known) |
| `ERROR` | File processing encountered an error |

This replaces the previous approach where `ScannerStatus` conflated file event results (`EMITTED`/`FILTERED`) with scanner lifecycle states (`EMITTING_INITIAL`/`IDLE`).

---

## 1. Metrics Architecture

### 1.1 Metric Types

Three metrics are tracked per scanner, identified by `agentId`:

| Metric | Type | Description |
|--------|------|-------------|
| **Current file count** | Gauge | Files currently in target directory (pushed from scanner via `recordFileEvent()`) |
| **Files discovered** | Counter | Total files found (CREATION/MODIFICATION events, monotonically incrementing) |
| **Last emission timestamp** | Timestamp | When the last file was emitted (used for idle detection at the scanner level) |

### 1.2 Port Architecture

The observability layer is split into two driven/driving adapter ports:

| Port | Type | Role |
|------|------|------|
| `ScannerMetricsPort` | Driven adapter | Pure metrics store — queries and updates metrics only. No push, no callbacks. |
| `ScannerEventPort` | Driving adapter | Push-only interface — receives events and pushes to registered UI callbacks. |

This separation means:
- **Metrics** (`ScannerMetricsPort`) is a pure data store — no side effects
- **Push notifications** (`ScannerEventPort`) is a driving mechanism — no data storage

### 1.3 AgentId Tag Strategy

All metrics are keyed by `agentId` (a `ConcurrentHashMap<String, AgentMetrics>`):

```
agentId = "my-agent"
  totalDiscovered = 150
  lastEmissionTimestamp = 2026-05-07T10:30:00
  fileCount = 7  ← pushed from scanner, not computed on-demand
```

- **`agentId`** — used by the UI to look up the correct metrics per scanner row
- Low-cardinality: one set of metrics per scanner, never file names or hashes

---

## 2. ScannerObservabilityUseCase (Orchestrator)

`ScannerObservabilityUseCase` is the single entry point for all observability operations. It coordinates metrics recording and event publishing as a unit.

### 2.1 API

```java
public class ScannerObservabilityUseCase {

    private final ScannerMetricsPort metricsPort;
    private final ScannerEventPort eventPort;

    public ScannerObservabilityUseCase(ScannerMetricsPort metricsPort,
                                       ScannerEventPort eventPort) {
        this.metricsPort = metricsPort;
        this.eventPort = eventPort;
    }

    // Record a file event — the primary entry point
    void recordFileEvent(String agentId, ScannerEventType eventType,
                         ScannerFileResult result, String folderPath,
                         String errorMessage, long fileCount);

    // Record a file emission (no file event, just emission tracking)
    void recordEmission(String agentId);

    // Query metrics (delegated to metricsPort)
    ScannerMetrics getMetrics(String agentId);
    List<ScannerMetrics> getAllMetrics();
    boolean isIdle(String agentId);
    LocalDateTime getLastEmissionTimestamp(String agentId);

    // Transition scanner to error state
    void transitionToError(String agentId, String errorMessage, long fileCount);
}
```

### 2.2 Implementation

```java
public void recordFileEvent(String agentId, ScannerEventType eventType,
                            ScannerFileResult result, String folderPath,
                            String errorMessage, long fileCount) {
    // 1. Record metrics (driven adapter)
    metricsPort.recordEvent(agentId, eventType, fileCount);

    // 2. Publish event (driving adapter)
    eventPort.publish(agentId, result, folderPath, errorMessage, fileCount);
}

public void transitionToError(String agentId, String errorMessage, long fileCount) {
    metricsPort.recordEvent(agentId, null, fileCount);
    eventPort.publish(agentId, ERROR, null, errorMessage, fileCount);
}
```

**Key design decision**: The use case does not know about `ScannerStatus` values that exist only for UI display. It only knows about domain concepts: `ScannerEventType` and `ScannerFileResult`.

---

## 3. ScannerMetricsService (Pure Metrics Store)

`ScannerMetricsService` implements `ScannerMetricsPort`. It stores metrics in memory and provides query methods. No callbacks, no push, no filesystem access.

### 3.1 API

```java
@Service
public class ScannerMetricsService implements ScannerMetricsPort {

    // Store metrics
    void recordEvent(String agentId, ScannerEventType eventType, long fileCount);

    // Query metrics
    ScannerMetrics getMetrics(String agentId);
    List<ScannerMetrics> getAllMetrics();
    boolean isIdle(String agentId);
    LocalDateTime getLastEmissionTimestamp(String agentId);
}
```

### 3.2 Event Dispatch Logic

`recordEvent()` dispatches based on `eventType`:

| eventType | Action |
|-----------|--------|
| `CREATION` / `MODIFICATION` | Increment `totalDiscovered`, store `fileCount`, update emission timestamp |
| `DELETION` / `UNCHANGED` | Store `fileCount`, no discovered increment |
| `null` (lifecycle events) | Store `fileCount`, update emission timestamp if emitting |

### 3.3 Thread Safety

- `ConcurrentHashMap` for metrics storage — thread-safe reads/writes
- Individual callback failures are caught and logged (in `ScannerEventBus`)
- Snapshots are immutable records — no synchronization needed when reading

### 3.4 Push-Based File Count

The file count is **pushed** from `ScannerService` during event processing:

```java
// In ScannerService.processRawEvent()
long fileCount = fileCounter.countFiles(folderPath);  // computed once
observability.recordFileEvent(agentId, eventType, result, folderPath, null, fileCount);
```

The scanner owns file counting via `FileCounterPort`. The metrics service is a pure store — it receives data and stores it.

---

## 4. ScannerEventBus (Event Push)

`ScannerEventBus` implements `ScannerEventPort`. It receives events and pushes them to registered UI callbacks. No metrics storage.

### 4.1 API

```java
@Service
public class ScannerEventBus implements ScannerEventPort {

    // Register/unregister UI callbacks
    void registerRefreshCallback(Consumer<ScannerEvent> callback);
    void unregisterRefreshCallback(Consumer<ScannerEvent> callback);

    // Push events
    void publish(String agentId, ScannerFileResult result,
                 String folderPath, String errorMessage, long fileCount);
}
```

### 4.2 Push Flow

```java
public void publish(String agentId, ScannerFileResult result,
                    String folderPath, String errorMessage, long fileCount) {
    ScannerEvent event = new ScannerEvent(agentId, result, eventType,
                                          folderPath, errorMessage, fileCount);
    callbacks.forEach(cb -> {
        try {
            cb.accept(event);
        } catch (Exception e) {
            log.error("Callback failed for agent {}", agentId, e);
        }
    });
}
```

---

## 5. ScannerMetrics

```java
public record ScannerMetrics(
    String agentId,
    long totalDiscovered,       // files found since scanner started
    LocalDateTime lastEmissionTimestamp,
    long fileCount              // pushed from scanner, not computed on-demand
) {}
```

Note: `fileCount` is computed once during event processing by `ScannerService` via `FileCounterPort`, then pushed to the metrics service. The UI reads it directly from `ScannerInfoDTO` — no separate call needed.

---

## 6. Scanner Event (Domain Event)

This is the event type used by `ScannerEventBus` for callback notifications:

```java
public record ScannerEvent(
    String agentId,
    ScannerFileResult result,
    String folderPath,
    String errorMessage,
    long fileCount
) {}
```

**Key difference from old architecture**: `ScannerEvent` carries `ScannerFileResult` (domain concept) instead of `ScannerStatus` (lifecycle + display concern). The event no longer carries lifecycle status values like `EMITTING_INITIAL` or `IDLE` — those are scanner-internal state managed by `ScannerService`.

---

## 7. UI-Owned Display State

The UI layer owns the display state mapping. `ScannerListView` receives `ScannerFileResult` events and maps them to visual states with **UI-owned timers**. This is a presentation concern — timer durations are UI choices, not scanner behavior.

### 7.1 Display State Mapping

| `ScannerFileResult` | Display State | Timer | Clear Condition |
|---------------------|--------------|-------|-----------------|
| `EMITTED` | **Active** | 10 seconds | Auto-fade after 10s |
| `FILTERED` | **Filtered** | 2 seconds | Auto-fade after 2s |
| `ERROR` | **Error** | None | Manual clear (error dismissal) |
| (none / stale) | **Idle** | — | Default when no recent event |

### 7.2 Timer Ownership

**Before (application layer)**: `ScannerService` scheduled the 2s `FILTERED → IDLE` reset and the 30s `IDLE` timeout.

**After (UI layer)**: `ScannerListView` tracks the last event time and applies its own timers. The view maintains a local `Map<String, LocalDateTime>` of last event timestamps and uses a `ScheduledExecutorService` to fade display states.

This means:
- The 10s "Active" and 2s "Filtered" display timers are UI choices
- Different UI implementations could use different timer durations
- The scanner application layer no longer manages display timing

### 7.3 Status Colors

| Display State | Color | Meaning |
|---------------|-------|---------|
| `Active` | Blue (`#4a90d9`) | File was recently emitted (within 10s) |
| `Filtered` | Orange (`#e67e22`) | File was rejected by hash filter (within 2s) |
| `Error` | Red (`#e74c3c`) | Scanner encountered an error |
| `Idle` | Green (`#27ae60`) | No recent file events; watching, waiting |

### 7.4 Scanner Lifecycle Status (Still in Application Layer)

`ScannerService` still manages scanner-internal lifecycle status, which is separate from UI display state:

| Status | Description | Owner |
|--------|-------------|-------|
| `EMITTING_INITIAL` | Performing an initial full scan | `ScannerService` |
| `EMITTING_UPDATES` | Actively processing file events | `ScannerService` |
| `ERROR` | Unrecoverable error; manual recovery required | `ScannerService` |
| `IDLE` | Default / recovery target | `ScannerService` |

The UI converts scanner lifecycle status + file result events into display state. When the scanner is in `EMITTING_INITIAL`, the UI shows the Active state. When in `EMITTING_UPDATES` with no recent file event, the UI shows Idle (after the 10s Active timer expires).

---

## 8. Hexagonal Layering

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `ScannerFileResult` | Value object: EMITTED, FILTERED, ERROR — what happened to a file |
| **Domain** | `ScannerMetrics`, `ScannerEventType`, `ScannerEvent` | Value objects for metrics and event typing |
| **Application (use case)** | `ScannerObservabilityUseCase` | Orchestrates metrics + event publishing. Single entry point for observability. |
| **Application (port)** | `ScannerMetricsPort` | Driven adapter — pure metrics queries and storage (no push, no callbacks) |
| **Application (port)** | `ScannerEventPort` | Driving adapter — push-only interface for event publishing |
| **Application (service)** | `ScannerMetricsService` | Implements `ScannerMetricsPort` — stores file counts, discovered counts, emission timestamps |
| **Application (service)** | `ScannerEventBus` | Implements `ScannerEventPort` — receives events, pushes to UI callbacks |
| **Application (use case)** | `ScannerService` | Per-scanner orchestrator: calls `ScannerObservabilityUseCase`, owns scanner lifecycle |
| **Inbound adapter** | `ScannerListView` | Vaadin view — maps `ScannerFileResult` to display state with UI-owned timers |
| **Inbound adapter** | `ScannerService` (UI) | Thin wrapper around `ScannerRegistry` for the view |
| **Outbound adapter** | `FileSystemFileCounter` | Walks real filesystem via `Files.walk()` |

### Hexagonal Flow

```
ScannerService (application)
  └─ observability.recordFileEvent(agentId, eventType, fileCount, folderPath)
       └─ ScannerObservabilityUseCase
            ├─ metricsPort.recordEvent(agentId, eventType, fileCount)     ← ScannerMetricsPort
            └─ eventPort.publish(agentId, result, folderPath)             ← ScannerEventPort
                 └─ callbacks.forEach(cb → cb.accept(event))
                      └─ ScannerListView.refreshCallback
                           └─ ui.access(() → refreshScanners())
                                └─ maps result → display state with UI-owned timers
                                     └─ Active (10s), Filtered (2s), Error (manual), Idle

ScannerController / ScannerService (UI)
  └─ metricsPort.getMetrics(agentId)                                     ← ScannerMetricsPort
  └─ scanner.toInfo()                                                    ← ScannerInfo (lifecycle status)
```

### Why This Works

| Concern | Previous Owner | New Owner | Rationale |
|---------|---------------|-----------|-----------|
| File event result (Emitted/Filtered/Error) | `ScannerStatus` (enum) | `ScannerFileResult` (new enum) | Domain concept, not a scanner lifecycle state |
| Observability orchestration | Implicit in `ScannerService` | `ScannerObservabilityUseCase` | Explicit use case, single entry point |
| Metrics storage (count, timestamps) | `ScannerObserverService` | `ScannerMetricsService` via `ScannerMetricsPort` | Pure data, driven adapter |
| UI push notifications | `ScannerObserverService` (callbacks) | `ScannerEventBus` via `ScannerEventPort` | Driving adapter mechanism, separated from metrics |
| Display state (Active/Idle/Filtered) | Application layer (`ScannerStatus`) | UI layer (`ScannerListView`) | Presentation concern — timer durations are UI choices |
| Scanner lifecycle (initial scan, error) | `ScannerService` | `ScannerService` | Scanner-internal state the application layer needs |
| Idle timeout | `ScannerService` (30s) | UI layer (10s for Emitted, 2s for Filtered) | Display timing, not scanner behavior |

---

## 9. UI: ScannerListView

### 9.1 Column Definitions

```java
// Agent column
grid.addColumn(ScannerInfoDTO::agentId)
    .setHeader("Agent").setAutoWidth(true);

// Target Directory column
grid.addColumn(ScannerInfoDTO::targetDirectory)
    .setHeader("Target Directory").setFlexGrow(2).setSortable(true);

// Status column: component column with colored dot + text
grid.addComponentColumn(this::renderStatusComponent)
    .setHeader("Status").setAutoWidth(true);

// Created column (formatted)
grid.addColumn(info -> info.createdAt() != null ? info.createdAt().format(DATE_TIME_FMT) : "N/A")
    .setHeader("Created").setAutoWidth(true).setSortable(true);

// Last Emitted column (formatted)
grid.addColumn(info -> info.lastEmittedAt() != null ? info.lastEmittedAt().format(DATE_TIME_FMT) : "N/A")
    .setHeader("Last Emitted").setAutoWidth(true).setSortable(true);

// Files column: fileCount from DTO (pushed from scanner, no observer call)
grid.addColumn(info -> {
    try {
        long count = info.fileCount() != null ? info.fileCount() : 0L;
        return count + " files";
    } catch (Exception e) {
        return "—";
    }
}).setHeader("Files").setAutoWidth(true);

// Actions column: delete button
grid.addComponentColumn(this::renderActionsColumn)
    .setHeader("Actions").setAutoWidth(true);
```

### 9.2 Display State Mapping (UI-Owned)

The view maps file events to display state with timers:

```java
// Track last event per agent for timer-based display state
private final ConcurrentHashMap<String, LocalDateTime> lastFileEvents = new ConcurrentHashMap<>();

private void refreshCallback(ScannerEvent event) {
    ui.access(() -> {
        lastFileEvents.put(event.agentId(), LocalDateTime.now());
        refreshScanners();
    });
}

private Component renderStatusComponent(ScannerInfoDTO info) {
    LocalDateTime lastEvent = lastFileEvents.get(info.agentId());
    DisplayState state = mapToFileResultDisplayState(info, lastEvent);
    return renderStatusWrapper(state);
}

private DisplayState mapToFileResultDisplayState(ScannerInfoDTO info, LocalDateTime lastEvent) {
    if (info.errorStatus()) {
        return DisplayState.ERROR;
    }
    if (lastEvent != null && Duration.between(lastEvent, LocalDateTime.now()).getSeconds() <= 10) {
        // Recent file event — check if it was filtered
        ScannerMetrics metrics = metricsPort.getMetrics(info.agentId());
        // ... check for filtered state
        return DisplayState.ACTIVE;
    }
    return DisplayState.IDLE;
}
```

### 9.3 StatusWrapper Component

Status is rendered as a `StatusWrapper` (extends `Div`) containing a colored dot (`Div` with circular style) and a text `Span`, displayed horizontally via flexbox:

```java
private static class StatusWrapper extends Div {
    public StatusWrapper(Div dot, Span text) {
        super(dot, text);
        getElement().getStyle().set("display", "flex");
        getElement().getStyle().set("align-items", "center");
    }
}
```

### 9.4 Grid Refresh Strategy

Two refresh paths:

| Path | Trigger | Method |
|------|---------|--------|
| **Quiet refresh** | Real-time callback (file event) | `refreshScanners()` → `grid.setItems()` |
| **Full refresh** | Manual button / auto-refresh (30s) | `loadScanners()` → `grid.setItems()` |

Both use `grid.setItems(scanners)` rather than `grid.getDataProvider().refreshAll()` because `ScannerInfoDTO` is an immutable record — the status value is baked in at construction. `refreshAll()` only re-renders existing items; it does not re-fetch data from the service.

---

## 10. Performance Considerations

### 10.1 Memory

- Each scanner has per-agent `AgentMetrics` (long + LocalDateTime + long) in `ConcurrentHashMap` — minimal overhead
- The `CopyOnWriteArrayList` of callbacks in `ScannerEventBus` is safe for concurrent reads (typical case)
- Snapshots are immutable records — no synchronization needed when reading

### 10.2 Event Frequency

- Events are pushed on every file event (CREATION, MODIFICATION, DELETION)
- The UI debounces via `ui.access()` which batches UI updates
- File count is computed once during event processing by `ScannerService`, not walked on every render

### 10.3 File Counting Performance

**Before (pull-based)**: `countFiles()` walked the directory on every grid render.

**After (push-based)**: `FileCounterPort.countFiles()` is called once during event processing in `ScannerService`. The computed `fileCount` is pushed to the metrics service and stored in `AgentMetrics`. The UI reads it from `ScannerInfoDTO` — no directory walking on render.

This eliminates redundant directory walks and provides consistent file counts across UI updates.

### 10.4 Thread Safety

- `ConcurrentHashMap` is thread-safe for metrics storage
- `CopyOnWriteArrayList` is thread-safe for callback iteration in `ScannerEventBus`
- `volatile status` in `ScannerService` ensures `toInfo()` reads the latest value
- `UI.access()` ensures UI updates happen on the Vaadin UI thread, not the background watch thread

---

## 11. Testing

### 11.1 Test Classes

| Test Class | Layer | What it covers |
|------------|-------|---------------|
| `ScannerMetricsServiceTest` | Application | Metrics tracking only — no callbacks, pure store behavior |
| `ScannerEventBusTest` | Application | Push/callback behavior — no metrics storage |
| `ScannerObservabilityUseCaseTest` | Application | Integration: metrics + event bus coordination |
| `ScannerFileResultTest` | Domain | Enum behavior, value semantics |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, file events through use case |
| `ScannerServiceTest` (UI) | Inbound adapter | `getAllScannerInfos()` conversion, error handling |
| `ScannerListViewTest` | Inbound adapter | Display state mapping (Active/Filtered/Error/Idle), timer behavior |
| `ScannerRegistryTest` | Application | Registry CRUD operations |
| `ScannerRegistryIntegrationTest` | Application | Full scanner lifecycle with registry |

### 11.2 Key Tests

```java
// ScannerMetricsServiceTest — pure metrics, no callbacks
@Test
void givenFileEvent_WhenRecorded_ThenMetricsStored() {
    service.recordEvent("agent-1", CREATION, 42L);
    assertThat(service.getMetrics("agent-1").fileCount()).isEqualTo(42L);
    assertThat(service.getMetrics("agent-1").totalDiscovered()).isEqualTo(1L);
}

// ScannerEventBusTest — push only, no metrics
@Test
void givenFileEvent_WhenPublished_ThenCallbacksInvoked() {
    CopyOnWriteArrayList<ScannerEvent> events = new CopyOnWriteArrayList<>();
    bus.registerRefreshCallback(events::add);
    bus.publish("agent-1", EMITTED, "/tmp", null, 7L);
    assertThat(events.get(0).result()).isEqualTo(EMITTED);
    assertThat(events.get(0).fileCount()).isEqualTo(7L);
}

// ScannerObservabilityUseCaseTest — coordinates both
@Test
void givenFileEvent_WhenRecorded_ThenMetricsAndEventBothUpdated() {
    useCase.recordFileEvent("agent-1", CREATION, EMITTED, "/tmp", null, 42L);
    assertThat(useCase.getMetrics("agent-1").fileCount()).isEqualTo(42L);
    // Event pushed to callback
    assertThat(events.get(0).result()).isEqualTo(EMITTED);
}

// ScannerFileResultTest — enum values
@Test
void givenEmittedFile_WhenResultCreated_ThenResultIsEMITTED() {
    assertThat(ScannerFileResult.valueOf("EMITTED")).isEqualTo(ScannerFileResult.EMITTED);
}

// ScannerListViewTest — display state mapping
@Test
void givenEmittedEvent_WhenWithin10s_ThenDisplayStateIsActive() {
    // Last event 5 seconds ago → Active
    assertThat(mapToDisplayState(EMITTED, 5)).isEqualTo(DisplayState.ACTIVE);
}

@Test
void givenFilteredEvent_WhenWithin2s_ThenDisplayStateIsFiltered() {
    // Last event 1 second ago → Filtered
    assertThat(mapToDisplayState(FILTERED, 1)).isEqualTo(DisplayState.FILTERED);
}

@Test
void givenEvent_WhenOlderThanTimer_ThenDisplayStateIsIdle() {
    // Last event 15 seconds ago → Idle (past 10s Active timer)
    assertThat(mapToDisplayState(EMITTED, 15)).isEqualTo(DisplayState.IDLE);
}
```

---

## 12. Related Documents

| Document | Type | Description |
|----------|------|-------------|
| [DPR: Scanner Concept](dpr-scanner-concept.md) | DPR | Scanner lifecycle, status transitions, hash-based change detection, `ScannerFileResult` |
| [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) | DPR | How agents subscribe to scanners, `ScannerRegistry` API |
| [DPR: File History Model](dpr-file-history-model.md) | DPR | `FileHistory` event model, hashing, and metadata storage |

---

*Scanner observability is implemented with `ScannerMetricsService` for pure in-memory metrics storage, `ScannerEventBus` for event push to UI callbacks, and `ScannerObservabilityUseCase` as the single orchestration entry point. File counting is pushed from `ScannerService` (computed via `FileCounterPort`). Display-state timers (Active 10s, Filtered 2s) are owned by the UI layer. No database persistence, no Micrometer, no Spring events — just counters, push-based file counts, and event-driven UI updates.*
