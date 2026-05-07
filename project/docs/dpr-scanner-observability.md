# DPR: Scanner Observability

> **Context**: This document describes the observability layer built on top of the scanner architecture — how metrics are tracked, how real-time UI updates are pushed, and the full data flow from file system events to the Vaadin dashboard.

---

## Overview

Scanners are the bridge between the file system and the AI pipeline. They watch directories for file changes and emit `FileHistory` events through a reactive stream. Observability lets operators see what scanners are doing in real time: the actual file count per directory, the scanner's current status, and timestamps of the last emission.

The observability layer consists of three parts:
1. **Metrics tracking** — `ScannerObserverService` tracks discovered counts, emission timestamps, and file counts per agent
2. **Status push chain** — `ScannerObserverService` callbacks push status changes to the Vaadin UI
3. **Real-time UI** — `ScannerListView` updates the grid via `grid.setItems()` when status or file events arrive

---

## 1. Metrics Architecture

### 1.1 Metric Types

Three metrics are tracked per scanner, identified by `agentId`:

| Metric | Type | Description |
|--------|------|-------------|
| **Current file count** | Gauge | Files currently in target directory (computed on-demand via `FileCounterPort`) |
| **Files discovered** | Counter | Total files found (CREATION/MODIFICATION events, monotonically incrementing) |
| **Last emission timestamp** | Timestamp | When the last file was emitted (used for idle detection) |

### 1.2 Service Architecture

`ScannerObserverService` is the central metrics tracker. It is a `@Service` that implements `ScannerMetricsPort` — the application-layer port for scanner metrics. It provides:

- Per-agent metrics storage via `ConcurrentHashMap<String, AgentMetrics>`
- Per-agent folder path tracking via `ConcurrentHashMap<String, String> agentFolders`
- Callback registration for real-time UI push notifications
- On-demand file counting via `FileCounterPort`

There is no Micrometer, no Spring events, no separate push service. The observer service directly holds the callback list and pushes to it.

### 1.3 AgentId Tag Strategy

All metrics are keyed by `agentId` (a `ConcurrentHashMap<String, AgentMetrics>`):

```
agentId = "my-agent"
  totalDiscovered = 150
  lastEmissionTimestamp = 2026-05-07T10:30:00
  folderPath = "/tmp/my-agent"
```

- **`agentId`** — used by the UI to look up the correct metrics per scanner row
- Low-cardinality: one set of metrics per scanner, never file names or hashes

---

## 2. ScannerObserverService

`ScannerObserverService` is the central metrics tracker. It is a `@Service` that implements `ScannerMetricsPort` and provides in-memory tracking with callback-based push notifications.

### 2.1 API

```java
@Service
public class ScannerObserverService implements ScannerMetricsPort {

    // Core tracking methods
    void recordEvent(String agentId, ScannerEventType eventType,
                     ScannerStatus status, String folderPath, String errorMessage);
    void recordEmission(String agentId);

    // Queries
    ScannerMetrics getMetrics(String agentId);              // Snapshot for one agent
    long countFiles(String agentId);                        // On-demand file count
    List<ScannerMetrics> getAllMetrics();                   // Snapshots for all agents
    boolean isIdle(String agentId);
    LocalDateTime getLastEmissionTimestamp(String agentId);

    // Callbacks
    void registerRefreshCallback(Consumer<ScannerMetricsEvent> callback);
    void unregisterRefreshCallback(Consumer<ScannerMetricsEvent> callback);

    // Folder management
    void storeFolder(String agentId, String folderPath);

    // Push
    void pushToUI(String agentId, ScannerStatus status);
}
```

### 2.2 Event Dispatch Logic

`recordEvent()` dispatches based on `eventType`:

| eventType | Action |
|-----------|--------|
| `CREATION` / `MODIFICATION` | Store folder, increment `totalDiscovered`, update emission timestamp |
| `DELETION` / `UNCHANGED` | Store folder, no discovered increment |
| `null` (lifecycle events) | Update emission timestamp if status is `EMITTING_UPDATES` |

After dispatch, `pushToUI()` is called with the event details, which iterates all registered callbacks.

### 2.3 Thread Safety

- `ConcurrentHashMap` for metrics and folder storage — thread-safe reads/writes
- `CopyOnWriteArrayList` for callbacks — safe for concurrent iteration
- Individual callback failures are caught and logged (don't break other callbacks)
- `volatile` fields for scanner status in `ScannerService`

### 2.4 countFiles()

The file count is **computed on-demand** by walking the watched directory via `FileCounterPort`:

```java
public long countFiles(String agentId) {
    String folderPath = agentFolders.get(agentId);
    if (folderPath == null) return 0;
    try {
        return fileCounter.countFiles(folderPath);
    } catch (Exception e) {
        log.warn("Failed to count files for folder {}: {}", folderPath, e.getMessage());
        return 0;
    }
}
```

The folder must be registered via `storeFolder()` first (now called in `ScannerService.initSource()`). If no folder is stored, or if the file counter throws, the method returns `0` gracefully.

---

## 3. ScannerMetrics

```java
public record ScannerMetrics(
    String agentId,
    long totalDiscovered,       // files found since scanner started
    LocalDateTime lastEmissionTimestamp
) {}
```

Note: `totalDiscovered` is a monotonically incrementing counter. The UI no longer uses it for the Files column — `countFiles()` is used instead, which walks the directory on each render.

---

## 4. Scanner Status Tracking

Status is managed by `ScannerService` (the per-scanner orchestrator) and pushed via `ScannerObserverService`:

| Status | Description |
|--------|-------------|
| `IDLE` | No emission for 30 seconds; watching, waiting |
| `EMITTING_INITIAL` | Performing an initial full scan |
| `EMITTING_UPDATES` | File system event detected and file emitted |
| `FILTERED` | Hash filter rejected a file (transient, resets after 2s) |
| `ERROR` | Unrecoverable error; manual recovery required |

The status is exposed via `ScannerService.ScannerInfo` (internal record) and converted to `ScannerInfoDTO` by the UI service layer.

```java
public record ScannerInfoDTO(
    String id,
    String agentId,
    String targetDirectory,
    String status,           // IDLE, EMITTING_INITIAL, EMITTING_UPDATES, FILTERED, ERROR
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt,
    String errorMessage
) {}
```

### Status Transitions

```
IDLE
  └─ initSource() → EMITTING_INITIAL
       ├─ files buffered → EMITTING_UPDATES
       └─ no files → IDLE
EMITTING_UPDATES
  └─ no emission for 30s → IDLE (idle checker)
EMITTING_UPDATES
  └─ unchanged file → FILTERED → (2s timer) → IDLE
ERROR
  └─ recover() → IDLE
```

---

## 5. Event-Driven UI Updates

The UI updates in real time when files are created, modified, or filtered. This is an **event-driven push** from the observer service to the Vaadin UI thread.

### 5.1 Push Chain

```
ScannerService (background thread)
  └─ notifyStatusChange(newStatus)
       ├─ this.status = newStatus (volatile)
       └─ observer.pushToUI(effectiveAgentId, newStatus)
            └─ callbacks.forEach(cb -> cb.accept(new ScannerMetricsEvent(agentId, status, ...)))
                 └─ ScannerListView.refreshCallback (registered on attach)
                      └─ ui.access(() → refreshScanners())
                           └─ scannerService.getAllScannerInfos()
                                └─ scannerRegistry.listAll()
                                     └─ scanner.toInfo() → new ScannerInfo record each call
                                          └─ reads volatile status.name()
                           └─ updateGrid(scanners, false)
                                └─ grid.setItems(scanners)  ← Vaadin re-renders all rows
```

### 5.2 Key Design Decisions

1. **`toInfo()` creates a new record each call** — The status value is baked in at construction time. `grid.getDataProvider().refreshAll()` would NOT update the status because it re-renders existing items without re-fetching data. `grid.setItems()` replaces the entire item list, forcing Vaadin to re-render all rows with fresh data.

2. **`volatile status` field** — The `ScannerService.status` field is `volatile`, so `toInfo().status()` always reads the latest value. The push chain ensures the status is updated before callbacks fire.

3. **No Spring events** — Unlike the old architecture, there is no `ScannerMetricsChangedEvent` or `ScannerMetricsPushService`. The observer service directly holds and invokes callbacks.

### 5.3 ScannerMetricsEvent (domain event)

This is the event type used by the observer service for callback notifications:

```java
public record ScannerMetricsEvent(
    String agentId,
    ScannerStatus status,
    ScannerEventType eventType,
    String folderPath,
    String errorMessage
) {}
```

This is distinct from `ScannerMetricsChangedEvent` (Spring event, if it still exists elsewhere) — `ScannerMetricsEvent` is the domain event emitted by `ScannerObserverService.pushToUI()`.

### 5.4 ScannerListView Callback

The view registers a callback on attach that wraps the refresh in `ui.access()`:

```java
addAttachListener(event -> {
    com.vaadin.flow.component.UI ui = event.getUI();
    refreshCallback = e -> {
        log.debug("UI refresh callback triggered: agent={}, type={}",
                e.agentId(), e.eventType() != null ? e.eventType().name().toLowerCase() : e.status().name().toLowerCase());
        ui.access(() -> refreshScanners());
    };
    observer.registerRefreshCallback(refreshCallback);
});

addDetachListener(event -> {
    if (refreshCallback != null) {
        observer.unregisterRefreshCallback(refreshCallback);
        refreshCallback = null;
    }
});
```

`refreshScanners()` calls `scannerService.getAllScannerInfos()` (the UI-layer thin wrapper) which re-fetches all scanner data, then calls `grid.setItems(scanners)` to re-render.

---

## 6. UI: ScannerListView

### 6.1 Column Definitions

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

// Files column: actual file count from countFiles()
grid.addColumn(info -> {
    try {
        long count = observer.countFiles(info.agentId());
        return count + " files";
    } catch (Exception e) {
        return "—";
    }
}).setHeader("Files").setAutoWidth(true);

// Actions column: delete button
grid.addComponentColumn(this::renderActionsColumn)
    .setHeader("Actions").setAutoWidth(true);
```

### 6.2 Status Colors

| Status | Color | Meaning |
|--------|-------|---------|
| `FILTERED` | Orange (`#e67e22`) | File was rejected by hash filter (brief, transient) |
| `ERROR` | Red (`#e74c3c`) | Scanner encountered an unrecoverable error |
| `IDLE` | Green (`#27ae60`) | No emission for 30 seconds; waiting for changes |
| `EMITTING_INITIAL` | Amber (`#f5a623`) | Scanner is performing an initial full scan |
| `EMITTING_UPDATES` | Blue (`#4a90d9`) | Scanner is actively processing file events |

### 6.3 StatusWrapper Component

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

### 6.4 Grid Refresh Strategy

Two refresh paths:

| Path | Trigger | Method |
|------|---------|--------|
| **Quiet refresh** | Real-time callback (file event) | `refreshScanners()` → `grid.setItems()` |
| **Full refresh** | Manual button / auto-refresh (30s) | `loadScanners()` → `grid.setItems()` |

Both use `grid.setItems(scanners)` rather than `grid.getDataProvider().refreshAll()` because `ScannerInfoDTO` is an immutable record — the status value is baked in at construction. `refreshAll()` only re-renders existing items; it does not re-fetch data from the service.

---

## 7. Hexagonal Layering

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `ScannerMetrics`, `ScannerStatus`, `ScannerMetricsEvent`, `ScannerEventType` | Value objects for metrics and status |
| **Application (port)** | `ScannerMetricsPort` | Interface for metrics observation and file counting |
| **Application (use case)** | `ScannerObserverService` | Tracks metrics, `countFiles()`, UI push callbacks |
| **Application (use case)** | `ScannerService` | Per-scanner orchestrator: status, idle timer, emission logic |
| **Inbound adapter** | `ScannerListView` | Vaadin view rendering the grid, callback registration |
| **Inbound adapter** | `ScannerService` (UI) | Thin wrapper around `ScannerRegistry` for the view |
| **Outbound adapter** | `FileSystemFileCounter` | Walks real filesystem via `Files.walk()` |

All changes stay within their layers. The inbound adapter (`ScannerListView`) calls through the application port (`ScannerObserverService` / `ScannerMetricsPort`). No cross-layer dependencies are introduced.

---

## 8. Testing

### 8.1 Test Classes

| Test Class | Layer | What it covers |
|------------|-------|---------------|
| `ScannerObserverServiceTest` | Application | Metrics tracking, callback registration, concurrency, `countFiles()` with mocked file counter, error handling |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, full scan, raw event processing, `initSource()` folder storage, status transitions |
| `ScannerServiceTest` (UI) | Inbound adapter | `getAllScannerInfos()` conversion, error handling |
| `ScannerListViewTest` | Inbound adapter | Route and page title annotations |
| `ScannerRegistryTest` | Application | Registry CRUD operations |
| `ScannerRegistryIntegrationTest` | Application | Full scanner lifecycle with registry |

### 8.2 Key New Tests

```java
// countFiles() returns mocked non-zero value when folder is stored
@Test
void givenAgentWithFolder_WhenCountFilesCalled_ThenReturnsMockedCount() {
    useCase.storeFolder(agentId, folderPath);
    when(fileCounter.countFiles(folderPath)).thenReturn(7L);
    assertThat(useCase.countFiles(agentId)).isEqualTo(7L);
}

// countFiles() returns 0 when no folder stored
@Test
void givenAgentWithoutFolder_WhenCountFilesCalled_ThenReturnsZero() {
    assertThat(useCase.countFiles("nonexistent-agent")).isZero();
}

// countFiles() handles exceptions gracefully
@Test
void givenFileCounterThrows_WhenCountFilesCalled_ThenReturnsZero() {
    useCase.storeFolder(agentId, folderPath);
    doThrow(new RuntimeException("disk full")).when(fileCounter).countFiles(folderPath);
    assertThat(useCase.countFiles(agentId)).isZero();
}

// initSource() stores folder so countFiles() works
@Test
void givenScannerCreated_WhenInitSourceCalled_ThenFolderStoredInObserver() {
    // Non-zero FileCounterPort mock → proves folder was stored
    assertThat(statusObserver.countFiles(agentId)).isEqualTo(42L);
}

// Status transitions include EMITTING_INITIAL and EMITTING_UPDATES
@Test
void givenNewFileEvent_WhenProcessed_ThenStatusTransitionsToEmittingUpdates() {
    assertThat(statusHistory).contains(EMITTING_INITIAL, EMITTING_UPDATES);
}

// Unchanged files transition to FILTERED
@Test
void givenUnchangedFileEvent_WhenProcessed_ThenStatusTransitionsToFiltered() {
    assertThat(statusHistory).contains(FILTERED);
}
```

---

## 9. Performance Considerations

### 9.1 Memory

- Each scanner has per-agent `AgentMetrics` (long + LocalDateTime) + folder path (String) in `ConcurrentHashMap` — minimal overhead
- The `CopyOnWriteArrayList` of callbacks is safe for concurrent reads (typical case)
- Snapshots are immutable records — no synchronization needed when reading

### 9.2 Event Frequency

- Events are pushed on every status change (CREATION, MODIFICATION, DELETION, UNCHANGED, idle detection)
- The UI debounces via `ui.access()` which batches UI updates
- `countFiles()` walks the directory on every grid render (quiet refresh or auto-refresh) — acceptable for now; cache with TTL if performance becomes an issue

### 9.3 Thread Safety

- `ConcurrentHashMap` is thread-safe for metrics and folder storage
- `CopyOnWriteArrayList` is thread-safe for callback iteration
- `volatile status` in `ScannerService` ensures `toInfo()` reads the latest value
- `UI.access()` ensures UI updates happen on the Vaadin UI thread, not the background watch thread

---

## 10. Related Documents

| Document | Type | Description |
|----------|------|-------------|
| [DPR: Scanner Concept](dpr-scanner-concept.md) | DPR | Scanner lifecycle, status transitions, hash-based change detection |
| [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) | DPR | How agents subscribe to scanners, `ScannerRegistry` API |
| [DPR: File History Model](dpr-file-history-model.md) | DPR | `FileHistory` event model, hashing, and metadata storage |

---

*Scanner observability is implemented with `ScannerObserverService` for in-memory metrics tracking, direct callback-based push for real-time UI updates, and `grid.setItems()` for full grid re-renders. No database persistence, no Micrometer, no Spring events — just counters, on-demand file counts, and event-driven updates.*
