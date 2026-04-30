# DPR: Scanner Observability

> **Context**: This document describes the observability layer built on top of the scanner architecture — how metrics are tracked, how real-time UI updates are pushed, and the full data flow from file system events to the Vaadin dashboard.

---

## Overview

Scanners are the bridge between the file system and the AI pipeline. They watch directories for file changes and emit `FileHistory` events through a reactive stream. Observability lets operators see what scanners are doing in real time: how many files they've discovered, how many were filtered (unchanged), the current file count per directory, and the scanner's current status.

The observability layer consists of three parts:
1. **Metrics tracking** — `ScannerObserverUseCase` tracks discovered, unchanged, and fileCount per agent
2. **Spring events** — `ScannerMetricsChangedEvent` published on file activity
3. **Real-time UI** — `ScannerListView` updates the grid via `UI.access()`

---

## 1. Metrics Architecture

### 1.1 Metric Types

Three metrics are tracked per scanner, identified by `agentId`:

| Metric | Type | Description |
|--------|------|-------------|
| **Current file count** | Gauge | Files currently in target directory |
| **Files discovered** | Counter | Total files found (initial scan + incremental) |
| **Files unchanged** | Counter | Files whose hash matches previous record (filtered/skipped) |

### 1.2 Why ScannerObserverUseCase, Not Micrometer

- The scanner metrics are lightweight and don't need the full Micrometer stack
- `ScannerObserverUseCase` provides a simple in-memory tracking mechanism with zero external dependencies
- No persistence layer needed — metrics are for real-time UI display only
- Simpler to test and mock in unit tests

### 1.3 AgentId Tag Strategy

All metrics are keyed by `agentId` (a `ConcurrentHashMap<String, ScannerMetricsSnapshot>`):

```
agentId = "my-agent"
  fileCount = 42
  totalDiscovered = 150
  unchanged = 108
```

- **`agentId`** — used by the UI to look up the correct metrics per scanner row
- Low-cardinality: one set of metrics per scanner, never file names or hashes

---

## 2. ScannerObserverUseCase

`ScannerObserverUseCase` is the central metrics tracker. It is a simple in-memory service that tracks counters per agent and publishes events to registered callbacks.

### 2.1 API

```java
public class ScannerObserverUseCase {

    // Core tracking methods
    void recordDiscovery(String agentId);      // Increment discovered counter
    void recordUnchanged(String agentId);      // Increment unchanged counter
    void updateFileCount(String agentId, long count);  // Set file count

    // Queries
    ScannerMetricsSnapshot getMetrics(String agentId);  // Snapshot for one agent
    List<ScannerMetricsSnapshot> getAllMetrics();       // Snapshots for all agents

    // Callbacks
    void registerRefreshCallback(Consumer<ScannerMetricsChangedEvent> callback);
    void unregisterRefreshCallback(Consumer<ScannerMetricsChangedEvent> callback);
}
```

### 2.2 Event Publishing

Each tracking method publishes a `ScannerMetricsChangedEvent` to all registered callbacks:

```java
public void recordDiscovery(String agentId) {
    AtomicInteger count = discovered.computeIfAbsent(agentId, k -> new AtomicInteger(0));
    count.incrementAndGet();
    publishEvent(ScannerMetricsChangedEvent.discoveryOccurred(agentId));
}

private void publishEvent(ScannerMetricsChangedEvent event) {
    for (Consumer<ScannerMetricsChangedEvent> callback : callbacks) {
        try {
            callback.accept(event);
        } catch (Exception e) {
            log.warn("Error in refresh callback: {}", e.getMessage());
        }
    }
}
```

### 2.3 Thread Safety

- All counters are `AtomicInteger` — thread-safe for concurrent increments
- Callbacks are stored in `CopyOnWriteArrayList` — safe for concurrent iteration
- Individual callback failures are caught and logged (don't break other callbacks)

---

## 3. Scanner Metrics Snapshot

```java
public record ScannerMetricsSnapshot(
    String agentId,
    long fileCount,        // current files in target directory
    long totalDiscovered,  // files found since scanner started
    long unchanged         // files matching previous hash (skipped)
) {}
```

---

## 4. Scanner Status Tracking

In addition to numeric metrics, scanners track a **status** that is managed by `ScannerRegistry`:

| Status | Description |
|--------|-------------|
| `IDLE` | No event for 30 seconds; watching, waiting |
| `EMITTING_INITIAL` | Performing a full scan |
| `EMITTING_UPDATES` | File system event detected and file emitted |
| `FILTERED` | Hash filter rejected a file (transient, resets after 2s) |
| `ERROR` | Unrecoverable error; manual recovery required |

The status is exposed via `ScannerInfo` DTO and displayed in the `ScannerListView`:

```java
public record ScannerInfo(
    String id,
    String agentId,
    String targetDirectory,
    String status,           // IDLE, EMITTING_INITIAL, EMITTING_UPDATES, FILTERED, ERROR
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt,
    String errorMessage
) {}
```

---

## 5. Event-Driven UI Updates

The UI updates in real time when files are created, modified, or filtered. This is an **event-driven push** from the scanner thread to the Vaadin UI thread.

### 5.1 Event Flow

```
WatchService thread (background)
  └─ NativeFileWatcher.emitFile()
       ├─ observer.recordDiscovery(agentId)  /  observer.recordUnchanged(agentId)
       │    └─ publishEvent(ScannerMetricsChangedEvent)
       │         └─ callbacks.forEach(cb -> cb.accept(event))
       │              └─ ScannerMetricsPushService.onScannerMetricsChanged()
       │                   └─ metricsService.pushToUI(event)
       │                        └─ callback.accept(event)
       │                             └─ ui.access(() → grid.refreshAll())
       └─ emitCallback.accept(history)
            └─ metricsEventPublisher.accept(ScannerMetricsChangedEvent.fileCountUpdated(agentId))
```

### 5.2 ScannerMetricsChangedEvent

```java
public class ScannerMetricsChangedEvent {
    private final String agentId;
    private final String type;  // "discovered", "unchanged", "file_count"

    // Factory methods
    public static ScannerMetricsChangedEvent discoveryOccurred(String agentId) { ... }
    public static ScannerMetricsChangedEvent unchangedOccurred(String agentId) { ... }
    public static ScannerMetricsChangedEvent fileCountUpdated(String agentId) { ... }
    public static ScannerMetricsChangedEvent errorOccurred(String agentId, String reason) { ... }
    public static ScannerMetricsChangedEvent recoveredFromError(String agentId) { ... }
    public static ScannerMetricsChangedEvent idleReached(String agentId) { ... }
}
```

### 5.3 ScannerMetricsPushService

`@Service` that receives Spring events and delegates to the registered UI callback:

```java
@Service
public class ScannerMetricsPushService {
    private final ScannerMetricsService metricsService;

    @EventListener
    public void onScannerMetricsChanged(ScannerMetricsChangedEvent event) {
        metricsService.pushToUI(event);
    }
}
```

### 5.4 ScannerMetricsService

Holds the reference to the UI callback (registered by the view on attach):

```java
@Service
public class ScannerMetricsService {
    private final AtomicReference<Consumer<ScannerMetricsChangedEvent>> refreshCallbackRef
            = new AtomicReference<>(event -> {});

    public void registerRefreshCallback(Consumer<ScannerMetricsChangedEvent> callback) {
        this.refreshCallbackRef.set(callback);
    }

    void pushToUI(ScannerMetricsChangedEvent event) {
        Consumer<ScannerMetricsChangedEvent> callback = refreshCallbackRef.get();
        if (callback != null) {
            callback.accept(event);
        }
    }
}
```

### 5.5 ScannerListView

The view registers a callback on attach that wraps the refresh in `UI.access()`:

```java
public class ScannerListView extends VerticalLayout {
    private final ScannerService scannerService;
    private final ScannerMetricsService metricsService;

    @AttachListener
    void onAttach(AttachEvent event) {
        com.vaadin.flow.component.UI ui = event.getUI();
        metricsService.registerRefreshCallback(e -> {
            log.debug("UI refresh callback triggered: agent={}, type={}",
                    e.getAgentId(), e.getType());
            ui.access(() -> {
                grid.getDataProvider().refreshAll();
                // Update file count column from snapshot
                scannerService.refreshScannerInfo();
            });
        });
    }

    @DetachListener
    void onDetach(DetachEvent event) {
        metricsService.registerRefreshCallback(e -> {});
    }
}
```

### 5.6 Why Not @EventListener on the View

> **Important**: `@EventListener` only works on Spring `@Component`/`@Service` beans. Vaadin views are not Spring beans, so the event listener cannot be placed directly on `ScannerListView`. The `ScannerMetricsPushService` (`@Service`) acts as the bridge.

---

## 6. UI: ScannerListView

### 6.1 Column Definitions

```java
// File count column
grid.addColumn(info -> {
    try {
        ScannerMetricsSnapshot m = metricsService.getMetrics(info.agentId());
        return m.fileCount() + " files";
    } catch (Exception e) {
        return "—";
    }
}).setHeader("Files").setAutoWidth(true);

// Status column with color coding
grid.addColumn(ScannerInfo::status)
    .setHeader("Status")
    .setAutoWidth(true)
    .setRenderer(new HtmlRenderer(status -> {
        switch (status) {
            case "FILTERED":   return "<span style='color:#e67e22'>FILTERED</span>";
            case "ERROR":      return "<span style='color:#e74c3c'>ERROR</span>";
            case "IDLE":       return "<span style='color:#95a5a6'>IDLE</span>";
            case "EMITTING_INITIAL":
            case "EMITTING_UPDATES":
                               return "<span style='color:#2ecc71'>ACTIVE</span>";
            default:           return status;
        }
    }));
```

### 6.2 Status Colors

| Status | Color | Meaning |
|--------|-------|---------|
| `FILTERED` | Orange (`#e67e22`) | File was rejected by hash filter (brief, transient) |
| `ERROR` | Red (`#e74c3c`) | Scanner encountered an unrecoverable error |
| `IDLE` | Gray (`#95a5a6`) | No event for 30 seconds; waiting for changes |
| `EMITTING_INITIAL` / `EMITTING_UPDATES` | Green (`#2ecc71`) | Scanner is actively processing files |

---

## 7. Configuration

No special configuration is required. The `ScannerObserverUseCase` is a simple Spring component with no external dependencies.

---

## 8. Testing

### 8.1 Test Classes

| Test | Type | What it verifies |
|------|------|-----------------|
| `ScannerObserverUseCaseTest` | Unit | Metrics tracking, callback registration, thread safety, missing agent returns zeroed snapshot |
| `FileSystemScannerAdapterMetricsTest` | Unit (Mockito) | Metrics counters increment on discovery, events published correctly |
| `NativeFileWatcherMetricsTest` | Unit | NativeFileWatcher callbacks invoked correctly during initial scan |
| `FileSystemScannerAdapterFilteredStatusTest` | Unit (Mockito) | FILTERED status emitted for unchanged files, not for new files |

### 8.2 Example: ScannerObserverUseCaseTest

```java
@Test
void givenDiscoveryEvent_WhenRecorded_ThenDiscoveredCountIncrements() {
    useCase.recordDiscovery("agent-1");

    ScannerMetricsSnapshot snapshot = useCase.getMetrics("agent-1");

    assertThat(snapshot.totalDiscovered()).isEqualTo(1);
    assertThat(snapshot.unchanged()).isZero();
}

@Test
void givenCallbackRegistered_WhenDiscoveryOccurs_ThenCallbackInvoked() {
    CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
    useCase.registerRefreshCallback(events::add);

    useCase.recordDiscovery("agent-1");

    assertThat(events).hasSize(1);
    assertThat(events.get(0).getType()).isEqualTo("discovered");
}
```

### 8.3 Example: Testing FILTERED Status

```java
@Test
void givenExistingFileWithStoredHash_WhenInitSourceCalled_ThenStatusFilteredEmitted() {
    // Pre-populate metadata store with file's hash
    when(fileMetadataStore.findById("known-file.txt"))
        .thenReturn(Optional.of(new FileMetadata("known-file.txt", content, hash)));

    // Create adapter with status callback
    adapter = new FileSystemScannerAdapter(..., statusChanges::add, ...);
    adapter.initSource(agentId);

    // Verify FILTERED was emitted
    assertThat(statusChanges).contains("FILTERED");
}
```

---

## 9. Performance Considerations

### 9.1 Memory

- Each scanner has 3 `AtomicInteger` counters + 1 `AtomicLong` file count in memory — minimal overhead
- The `CopyOnWriteArrayList` of callbacks is safe for concurrent reads (typical case)
- Snapshots are immutable records — no synchronization needed when reading

### 9.2 Event Frequency

- Events are published on every file discovery, unchanged detection, and file count update
- For large directories with rapid changes, this could result in many events
- The UI debounces via `UI.access()` which batches UI updates

### 9.3 Thread Safety

- `AtomicInteger` is thread-safe for counter increments
- `CopyOnWriteArrayList` is thread-safe for callback iteration
- The `emitCallback` is called from the watch service thread; `UI.access()` ensures UI updates happen on the UI thread

---

## 10. Related Documents

| Document | Type | Description |
|----------|------|-------------|
| [DPR: Scanner Concept](dpr-scanner-concept.md) | DPR | Scanner lifecycle, status transitions, hash-based change detection |
| [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) | DPR | How agents subscribe to scanners, `ScannerRegistry` API |
| [DPR: File History Model](dpr-file-history-model.md) | DPR | `FileHistory` event model, hashing, and metadata storage |

---

*Scanner observability is implemented with `ScannerObserverUseCase` for in-memory metrics tracking, Spring events for real-time UI updates, and direct service injection into the Vaadin view. No database persistence, no Micrometer — just counters, gauges, and event-driven updates.*
