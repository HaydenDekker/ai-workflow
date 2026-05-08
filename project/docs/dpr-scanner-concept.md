# DPR: Scanner Concept

> **Purpose**: This document explains how scanners watch directories for file changes, emit `FileHistory` events through reactive streams, and manage status lifecycle. It is the companion to the agent-scanner relationship document.

---

## Overview

A **scanner** watches a single directory on the file system and emits a reactive stream of file change events. Each scanner is owned by exactly one agent (one-to-one mapping) and is managed by the `ScannerRegistry`.

### Scanner Responsibilities

1. **Watch**: Monitor a folder for file system events (CREATE, MODIFY, DELETE)
2. **Detect**: Use hash comparison to determine if a file is new, changed, or unchanged
3. **Filter**: Skip files whose hash matches stored metadata (unchanged / already known)
4. **Emit**: Push `FileHistory` events through a shared reactive `Flux`
5. **Report**: Coordinate observability (metrics + event publishing) via `ScannerObservabilityUseCase`
6. **Count**: Compute file count via `FileCounterPort` and push to metrics service

### ScannerFileResult (Domain Enum)

File event results are now a distinct domain concept, separate from scanner lifecycle state:

| Value | Description |
|-------|-------------|
| `EMITTED` | File was processed and emitted through the reactive stream |
| `FILTERED` | File was rejected by hash filter (unchanged / already known) |
| `ERROR` | File processing encountered an error |

This replaces the previous approach where `ScannerStatus` conflated file event results (`Emitted`/`Filtered`) with scanner lifecycle states (`EMITTING_INITIAL`/`IDLE`). The `ScannerFileResult` enum is a domain concept — it answers "what happened to this file?" — while `ScannerStatus` answers "what is the scanner doing right now?"

---

## Architecture

### Current Implementation

The scanner uses Java's `WatchService` directly (via `NativeFileWatcher`) — no Spring Integration pipeline.

```
┌──────────────────────────────────────────────────────────────────┐
│  WatchService (OS-level NIO)                                      │
│  └── Detects: CREATE, MODIFY, DELETE events immediately          │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  FileWatcherPort (implementation: FileSystemFileWatcher)          │
│  └── Reads file, computes hash, compares with stored metadata    │
│  └── Emits RawFileEvent through Flux (reactive)                  │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  ScannerService (application layer)                               │
│  └── Bridges FileWatcherPort to ScannerRegistry                  │
│  └── Manages scanner lifecycle (EMITTING_INITIAL → EMITTING_UPDATES → IDLE) |
│  └── Controls emission delay throttling                          │
│  └── Computes fileCount via FileCounterPort (pushed to metrics)  │
│  └── Calls ScannerObservabilityUseCase for metrics + event push  │
└──────────────────────┬───────────────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ScannerRegistry  ScannerMetricsService    ScannerEventBus
   (lifecycle)       (metrics store)         (event push)
```

### Key Classes

| Class | Role | Package |
|-------|------|---------|
| `FileWatcherPort` | Port interface for file watching (implementation: `FileSystemFileWatcher`) | `file/port/` |
| `FileCounterPort` | Port interface for counting files (implementation: `FileSystemFileCounter`) | `file/port/` |
| `ScannerService` | Application-layer orchestrator: lifecycle, status, emission logic, file counting | `scanner/` |
| `ScannerRegistry` | Lifecycle management, one scanner per agent, status tracking | `pipeline/` |
| `FileComparator` | Compares file hash against stored metadata to detect changes | `file/` |
| `FileHash` | Computes SHA-256 hash of file content | `shared/` |
| `ScannerMetricsService` | Pure metrics store — file counts, discovered counts, timestamps | `scanner/` |
| `ScannerEventBus` | Event push — receives events, pushes to UI callbacks | `scanner/` |
| `ScannerObservabilityUseCase` | Orchestrates metrics + event publishing | `scanner/` |
| `ScannerFileResult` | Domain enum: EMITTED, FILTERED, ERROR | `scanner/` |

---

## Scanner Status Lifecycle

A scanner transitions through the following lifecycle statuses (managed by `ScannerService`):

| Status | Description | Transition Trigger |
|--------|-------------|-------------------|
| **IDLE** | Default state; waiting for events | Initial state; after recovery |
| **EMITTING_INITIAL** | Performing a full scan of all existing files | `initSource()` or `resetToFullScan()` called |
| **EMITTING_UPDATES** | File system event detected and file emitted | New/changed file detected (hash mismatch) |
| **ERROR** | Scanner encountered an unrecoverable error | Exception during scan or watch |

### Status Transitions

```
                         initSource() / resetToFullScan()
    IDLE ─────────────────────────────────────────────► EMITTING_INITIAL
         ▲                                               │
         │                                               ▼
         │                                      ┌────────┴────────┐
         │                                      │                 │
         │                    (hash matches)    ▼                 ▼
         │                      FILTERED ──(UI timer)─► IDLE   New file found
         │                       │                        │
         │                       │                hash mismatch
         │                       │                        ▼
         │                       │                  EMITTING_UPDATES
         │                       │                        │
         │                       │              (error occurs)
         │                       └────────────────────────► ERROR
         │                                                │
         │         (unrecoverable error from any state)    │
         └────────────────────────────────────────────────┘
                                                          │ manual recovery
                                                          ▼
                                                    EMITTING_INITIAL
```

### Key Change from Previous Architecture

**Before**: `FILTERED` was a scanner lifecycle status managed by `ScannerService` with a 2s scheduled reset.

**After**: `FILTERED` is now a `ScannerFileResult` (domain concept). The scanner service calls `ScannerObservabilityUseCase.recordFileEvent()` with `result = FILTERED`. The UI layer owns the timer that fades the display state back to Idle after 2 seconds. The scanner service no longer schedules the reset.

---

## File Event Processing

When a raw file event arrives, the scanner processes it through the observability use case:

```java
// In ScannerService.processRawEvent()
RawFileEvent rawEvent = ...;

// 1. Compute file count once
long fileCount = fileCounter.countFiles(folderPath);

// 2. Compare hash
FileHistory history = fileComparator.matches(metadata);

if (!history.hashMatches()) {
    // New or changed — emit
    ScannerEventType eventType = history.previousFile().isEmpty()
        ? ScannerEventType.CREATION : ScannerEventType.MODIFICATION;
    observability.recordFileEvent(agentId, eventType, EMITTED,
                                  folderPath, null, fileCount);
    emitWithDelay(history);
} else {
    // Unchanged — skip
    observability.recordFileEvent(agentId, ScannerEventType.UNCHANGED,
                                  FILTERED, folderPath, null, fileCount);
    // No more scheduled reset — UI owns the timer now
}
```

### File Event Result (ScannerFileResult)

| Result | When Set | Action |
|--------|----------|--------|
| `EMITTED` | Hash mismatch (new or changed file) | File emitted through flux |
| `FILTERED` | Hash match (unchanged file) | File skipped, metrics incremented |
| `ERROR` | Exception during processing | Error stored, scanner transitions to ERROR status |

### Lifecycle Status (ScannerStatus)

| Status | When Set | Purpose |
|--------|----------|---------|
| `EMITTING_INITIAL` | `initSource()` / `resetToFullScan()` | Scanner is performing initial/full scan |
| `EMITTING_UPDATES` | After first successful emission | Scanner is actively processing events |
| `IDLE` | After recovery / default | Scanner is watching, waiting for events |
| `ERROR` | Unrecoverable exception | Manual recovery required |

---

## Scanner Lifecycle

### Creation

```
1. Agent created (POST /api/agents)
2. ScannerRegistry.createForAgent(agentId, targetDir, delaySeconds)
   ├── Validates directory exists and is readable
   ├── Creates FileCounterPort (injected via ScannerRegistry)
   ├── Registers scanner in ConcurrentHashMap<agentId, ScannerService>
   ├── Calls scanner.initSource(agentId)
   │     ├── Computes initial fileCount via fileCounter.countFiles(folderPath)
   │     ├── Transitions to EMITTING_INITIAL
   │     ├── Starts FileWatcherPort (initial full scan)
   │     ├── Hash filter processes all existing files
   │     │     ├── New/changed → observability.recordFileEvent(CREATION, EMITTED, fileCount)
   │     │     └── Unchanged → observability.recordFileEvent(UNCHANGED, FILTERED, fileCount)
   │     ├── Transitions to EMITTING_UPDATES (if files were buffered)
   │     │     or stays IDLE (if all files unchanged)
   │     └── Pushes initial fileCount to metrics service
   └── Returns ScannerInfo DTO
```

### Destruction

```
1. Agent removed (DELETE /api/agents/{id})
2. ScannerRegistry.destroyForAgent(scannerId)
   ├── Removes scanner from ConcurrentHashMap
   ├── Calls scanner.destroy()
   │     ├── Stops FileWatcherPort (closes WatchService)
   │     └── Shuts down emission delay scheduler
   └── Cleans up all resources
```

### Refresh (Reset to Full Scan)

```
1. Agent refreshed (POST /api/agents/{id}/refresh)
2. ScannerRegistry.refreshAgent(scannerId)
   ├── Calls scanner.resetToFullScan()
   │     ├── Transitions to EMITTING_INITIAL
   │     ├── Calls fileWatcherPort.rawScan()
   │     │     ├── Walks directory tree
   │     │     ├── For each file:
   │     │     │     ├── Hash mismatch → emit FileHistory
   │     │     │     └── Hash match → FILTERED result
   │     │     └── Updates buffer
   │     └── Transitions to EMITTING_UPDATES
   └── Logs refresh complete
```

---

## Hash-Based Change Detection

### How It Works

The scanner uses SHA-256 hashing to detect file changes without re-processing unchanged files:

1. **On initial scan**: Every file in the directory is read, hashed, and the hash is stored in the `FileMetadataStore`
2. **On watch events**: When a CREATE or MODIFY event fires, the file is re-read and hashed
3. **Comparison**: `FileComparator.matches()` checks if the new hash differs from the stored hash
4. **Result**:
   - **Hash mismatch** → New or changed file → `hashMatches() = false` → File is emitted
   - **Hash match** → Unchanged file → `hashMatches() = true` → File is skipped, FILTERED result set

```java
FileHistory history = fileComparator.matches(metadata);

if (!history.hashMatches()) {
    // New or changed — emit
    long fileCount = fileCounter.countFiles(folderPath);  // compute once
    ScannerEventType eventType = history.previousFile().isEmpty()
        ? ScannerEventType.CREATION : ScannerEventType.MODIFICATION;
    observability.recordFileEvent(agentId, eventType, EMITTED,
                                  folderPath, null, fileCount);
    fileMetadataStore.save(metadata);
    emitWithDelay(history);
} else {
    // Unchanged — skip
    long fileCount = fileCounter.countFiles(folderPath);
    observability.recordFileEvent(agentId, ScannerEventType.UNCHANGED,
                                  FILTERED, folderPath, null, fileCount);
}
```

### FileMetadata Model

```java
public record FileMetadata(
    String url,        // relative path within watched directory
    String body,       // file content (for hash computation)
    String hash        // SHA-256 hash of content
) {}
```

### FileComparator

```java
public class FileComparator {
    private final FileMetadataStore store;

    public FileHistory matches(FileMetadata current) {
        Optional<FileMetadata> previous = store.findById(current.url());
        boolean changed = previous.map(prev -> !prev.hash().equals(current.hash()))
                                  .orElse(true);  // new file if no previous record

        return new FileHistory(
            previous.orElse(null),   // previous file (if exists)
            current                 // current file
        );
    }
}
```

---

## File Emission Throttling

Consecutive file changes arriving in quick succession (e.g., a file being written in chunks) are coalesced to avoid redundant emissions.

### Mechanism

1. When a file event arrives, the watcher checks if `emissionDelay` has elapsed since the last emission
2. If **not elapsed**: the file is buffered (`latestBufferedHistory`), and a `DelayedEmitter` is started
3. When the delay elapses: the buffered file is emitted, and the delay timer resets
4. If a **new event arrives during the delay window**: it replaces the buffered file (coalescing)

```java
private void tryEmitWithDelay(FileHistory history) {
    if (Duration.between(lastEmissionTime, LocalDateTime.now()).compareTo(emissionDelay) < 0) {
        // Delay not elapsed — buffer and coalesce
        latestBufferedHistory = history;
        startDelayedEmitter();
        return;
    }
    // Delay elapsed — emit immediately
    sink.tryEmitNext(history);
    lastEmissionTime = LocalDateTime.now();
}
```

---

## Metrics Tracking

Metrics are tracked via `ScannerMetricsService` (not Micrometer). Each scanner has three metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `fileCount` | Gauge | Current number of files in the watched directory (pushed from scanner) |
| `totalDiscovered` | Counter | Total files found (initial scan + incremental) |
| `unchanged` | Counter | Files whose hash matches previous record (skipped) |

### Metrics Event Flow (Push-Based)

```
ScannerService.processRawEvent(rawEvent)
  └─ fileCounter.countFiles(folderPath)              ← computed once
  └─ observability.recordFileEvent(agentId, eventType, result, folderPath, null, fileCount)
       ├─ metricsPort.recordEvent(agentId, eventType, fileCount)   ← stores in memory
       └─ eventPort.publish(agentId, result, folderPath, null, fileCount)
            └─ callbacks.forEach(cb -> cb.accept(new ScannerEvent(...)))
                 └─ ScannerListView.refreshCallback
                      └─ ui.access(() → refreshScanners())
                           └─ maps result → display state (Active/Filtered/Error/Idle)
                                └─ grid.setItems(scanners)
                                     └─ reads info.fileCount() from DTO
```

**Key change**: File count is **pushed** from `ScannerService` during event processing, not pulled via `countFiles()` on every render. The scanner owns file counting; the metrics service stores metrics; the event bus pushes to UI.

### Event Types

| Event Type | When Published |
|------------|----------------|
| `CREATION` | New file detected (no previous hash record) |
| `MODIFICATION` | Existing file changed (hash mismatch) |
| `DELETION` | File removed from watched directory |
| `UNCHANGED` | File hash matches stored metadata (filtered) |

---

## Code Examples

### Creating a Scanner

```java
// In ScannerRegistry.createForAgent()
ScannerService scanner = new ScannerService(
    agentId,
    targetDirectory,
    delay,                           // poll interval (e.g., 5 seconds)
    emissionDelay,                   // emission throttle (e.g., 2 seconds)
    fileWatcherPort,                 // file watching port
    fileComparator,                  // hash comparison
    fileCounter,                     // file counting port
    observability                    // ScannerObservabilityUseCase
);

ScannerMetadata metadata = new ScannerMetadata(
    scanner, agentId, targetDirectory, ScannerStatus.IDLE,
    LocalDateTime.now(), null, null);

scanners.put(agentId, metadata);
scanner.initSource(agentId);  // triggers initial scan, computes fileCount
```

### Observability Use Case Integration

```java
// ScannerService processes a file event
private void processRawEvent(RawFileEvent rawEvent) {
    // Compute file count once
    long fileCount = fileCounter.countFiles(folderPath);

    // Determine result and event type
    FileHistory history = fileComparator.matches(metadata);
    ScannerFileResult result;
    ScannerEventType eventType;

    if (!history.hashMatches()) {
        eventType = history.previousFile().isEmpty()
            ? ScannerEventType.CREATION : ScannerEventType.MODIFICATION;
        result = ScannerFileResult.EMITTED;
    } else {
        eventType = ScannerEventType.UNCHANGED;
        result = ScannerFileResult.FILTERED;
    }

    // Single entry point — coordinates metrics + event push
    observability.recordFileEvent(agentId, eventType, result,
                                  folderPath, null, fileCount);
}
```

### Reading File Count in the UI

```java
// In ScannerListView — Files column definition (reads from DTO, no observer call)
grid.addColumn(info -> {
    long count = info.fileCount() != null ? info.fileCount() : 0L;
    return count + " files";
}).setHeader("Files").setAutoWidth(true);

// Status column — component column with colored dot + text
grid.addComponentColumn(this::renderStatusComponent)
    .setHeader("Status").setAutoWidth(true);

// Grid refresh — uses setItems() not refreshAll()
private void refreshScanners() {
    scannerService.getAllScannerInfos()
        .subscribe(scanners -> grid.getUI().get().access(() -> updateGrid(scanners, false)),
                   error -> log.warn("Error refreshing scanners: {}", error.getMessage()));
}

private void updateGrid(List<ScannerInfoDTO> scanners, boolean notify) {
    grid.setItems(scanners);  // re-fetches fresh data (not just re-renders existing rows)
}
```

---

## Testing

### Test Classes

| Test Class | Layer | What it verifies |
|------------|-------|------------------|
| `ScannerMetricsServiceTest` | Application | Pure metrics storage — file counts, discovered counts, timestamps |
| `ScannerEventBusTest` | Application | Push/callback behavior — no metrics storage |
| `ScannerObservabilityUseCaseTest` | Application | Integration: coordinates metrics + event publishing |
| `ScannerFileResultTest` | Domain | Enum values and behavior |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, file events through use case |
| `ScannerServiceTest` (UI) | Inbound adapter | `getAllScannerInfos()` conversion, error handling |
| `ScannerListViewTest` | Inbound adapter | Display state mapping (Active/Filtered/Error/Idle) |
| `ScannerRegistryTest` | Application | Registry CRUD operations |
| `ScannerRegistryIntegrationTest` | Application | Full agent-scanner lifecycle, flux connectivity |

### Example: Testing Observability Use Case

```java
@Test
void givenFileEvent_WhenRecorded_ThenMetricsAndEventBothUpdated() {
    CopyOnWriteArrayList<ScannerEvent> events = new CopyOnWriteArrayList<>();
    ScannerEventBus eventBus = new ScannerEventBus();
    eventBus.registerRefreshCallback(events::add);

    ScannerObservabilityUseCase useCase = new ScannerObservabilityUseCase(metricsService, eventBus);
    useCase.recordFileEvent("agent-1", CREATION, EMITTED, "/tmp", null, 42L);

    // Metrics stored
    assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(42L);
    assertThat(metricsService.getMetrics("agent-1").totalDiscovered()).isEqualTo(1L);

    // Event pushed
    assertThat(events.get(0).result()).isEqualTo(EMITTED);
    assertThat(events.get(0).fileCount()).isEqualTo(42L);
}

@Test
void givenFilteredEvent_WhenRecorded_ThenMetricsIncrementedButNotEmitting() {
    useCase.recordFileEvent("agent-1", UNCHANGED, FILTERED, "/tmp", null, 7L);

    assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(7L);
    assertThat(events.get(0).result()).isEqualTo(FILTERED);
}
```

### Example: Testing File Count Push

```java
@Test
void givenFileEventWithFileCount_WhenRecorded_ThenStoredInMetrics() {
    useCase.recordFileEvent("agent-1", CREATION, EMITTED, "/tmp", null, 42L);
    assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(42L);
}

@Test
void givenFileEventWithCount_WhenEmissionRecorded_ThenFileCountPreserved() {
    // Regression test: emission events must not reset fileCount to 0
    useCase.recordFileEvent("agent-1", CREATION, EMITTED, "/tmp", null, 7L);
    useCase.recordEmission("agent-1");
    useCase.recordFileEvent("agent-1", null, EMITTED, null, null, 7L);
    assertThat(metricsService.getMetrics("agent-1").fileCount()).isEqualTo(7L);
}
```

---

## Migration Notes

### What Changed from the Old Architecture

| Aspect | Old | New |
|--------|-----|-----|
| Metrics service | `ScannerObserverService` (metrics + callbacks) | `ScannerMetricsService` (metrics only) + `ScannerEventBus` (push only) |
| Event port | None (callbacks in metrics service) | `ScannerEventPort` / `ScannerEventBus` (driving adapter) |
| Domain enum | `ScannerStatus` (lifecycle + display + file result) | `ScannerFileResult` (EMITTED/FILTERED/ERROR) + `ScannerStatus` (lifecycle only) |
| Event record | `ScannerMetricsEvent` (carried `ScannerStatus`) | `ScannerEvent` (carries `ScannerFileResult`) |
| Orchestration | Implicit in `ScannerService.processRawEvent()` | `ScannerObservabilityUseCase` (explicit use case) |
| Display timers | `ScannerService` (2s FILTERED, 30s IDLE) | `ScannerListView` (10s Active, 2s Filtered, Error manual) |
| File count source | `observer.countFiles(agentId)` (pull-based) | `info.fileCount()` from DTO (push-based) |
| File count computation | Pushed from `ScannerService` via `FileCounterPort` | Same — but now through `ScannerObservabilityUseCase` |
| Hexagonal separation | Metrics service held both data and callbacks | Metrics (driven) and events (driving) are separate ports |

### What Changed from Phase 1 to Phase 8

The scanner view regressions were fixed across eight phases:
- **Phase 1**: Added `fileCount` field to `ScannerMetrics`, `ScannerMetricsEvent`, `AgentMetrics`
- **Phase 2**: Updated `recordEvent()` to accept and store `fileCount`
- **Phase 3**: Updated `getMetrics()` to return `fileCount` from stored metrics
- **Phase 4**: `ScannerService` computes `fileCount` via `FileCounterPort` and passes to `recordEvent()`
- **Phase 5**: `ScannerInfoDTO` includes `fileCount`; UI/REST services include it in DTO conversion
- **Phase 6**: `ScannerListView` reads `fileCount` from DTO instead of `observer.countFiles()`
- **Phase 7**: Removed `storeFolder()`, `countFiles()`, `agentFolders`, `FileCounterPort` from observer
- **Phase 8**: Removed old countFiles tests, added fileCount preservation regression tests
- **Phase 9**: Split metrics from push into separate ports; introduced `ScannerFileResult`, `ScannerObservabilityUseCase`, `ScannerEventBus`, `ScannerMetricsService`; UI owns display timers

---

## See Also

- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanners, `ScannerRegistry` API
- [DPR: Scanner Observability](dpr-scanner-observability.md) — Metrics instrumentation, event push, UI-owned display timers
- [DPR: File History Model](dpr-file-history-model.md) — `FileHistory` event model, hashing, and metadata storage
