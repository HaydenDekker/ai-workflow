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
5. **Report**: Update status and metrics when files are discovered, filtered, or emitted
6. **Count**: Compute file count via `FileCounterPort` and push to observer

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
│  └── Manages status transitions (IDLE → EMITTING_INITIAL → …)    │
│  └── Controls emission delay throttling                          │
│  └── Schedules FILTERED → IDLE reset after 2s                    │
│  └── Computes fileCount via FileCounterPort (pushed to observer) │
└──────────────────────┬───────────────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ScannerRegistry  ScannerObserverService
   (lifecycle)      (metrics + UI push)
```

### Key Classes

| Class | Role | Package |
|-------|------|---------|
| `FileWatcherPort` | Port interface for file watching (implementation: `FileSystemFileWatcher`) | `file/port/` |
| `FileCounterPort` | Port interface for counting files (implementation: `FileSystemFileCounter`) | `file/port/` |
| `ScannerService` | Application-layer orchestrator: status, idle timer, emission logic, **file counting** | `scanner/` |
| `ScannerRegistry` | Lifecycle management, one scanner per agent, status tracking, idle detection | `pipeline/` |
| `FileComparator` | Compares file hash against stored metadata to detect changes | `file/` |
| `FileHash` | Computes SHA-256 hash of file content | `shared/` |
| `ScannerObserverService` | Stores metrics (discovered, fileCount) and pushes UI callbacks (pure messenger) | `scanner/` |
| `ScannerMetricsEvent` | Domain event emitted by observer for UI push notifications | `scanner/` |

---

## Scanner Status Lifecycle

A scanner transitions through the following statuses:

| Status | Description | Transition Trigger |
|--------|-------------|-------------------|
| **IDLE** | No event for 30 seconds; watching, waiting | Default state; after 30s of no emissions |
| **EMITTING_INITIAL** | Performing a full scan of all existing files | `initSource()` or `resetToFullScan()` called |
| **EMITTING_UPDATES** | File system event detected and file emitted | Hash mismatch detected (new/changed file) |
| **FILTERED** | Hash filter rejected a file (unchanged / already known) | Hash matches stored metadata |
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
         │                      FILTERED ──(2s)─► IDLE    New file found
         │                       │                        │
         │                       │                hash mismatch
         │                       │                        ▼
         │                       │                  EMITTING_UPDATES
         │                       │                        │
         │                       │              (30s no emissions)
         │                       │                        ▼
         │                       └──────────────────── IDLE
         │
         │         (unrecoverable error from any state)
         └────────────────────────────────────────────────► ERROR
                                                          │
                                                          │ manual recovery
                                                          ▼
                                                    EMITTING_INITIAL
```

### FILTERED Status Details

The `FILTERED` status is emitted when a file's hash matches previously stored metadata, indicating the file is unchanged and should be skipped. This status:

1. Is triggered from **two paths**:
   - `NativeFileWatcher.emitFile()` — during watch event processing (CREATE/MODIFY)
   - `FileSystemScannerAdapter.scanAllFiles()` — during reset-to-full-scan operations
2. Is **transient**: automatically resets to `IDLE` after 2 seconds via a scheduled task
3. Does **not** emit the file through the flux (the file is skipped)
4. Triggers the `onUnchanged` metrics callback (increments `unchanged` counter)

```java
// In NativeFileWatcher.emitFile() — when hash matches
if (history.hashMatches()) {
    onUnchanged.accept(directory.toString());       // metrics
    onFiltered.accept(directory.toString());        // status → FILTERED
    log.debug("Unchanged file (skipped): {}", relativePath);
}
```

### Idle Detection

A shared `ScheduledExecutorService` runs every 10 seconds to check all scanners. If a scanner is in `EMITTING_UPDATES` and has not emitted for 30 seconds (`IDLE_TIMEOUT`), it transitions to `IDLE`.

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
   │     │     ├── New/changed → recordEvent(CREATION, fileCount), emit FileHistory
   │     │     └── Unchanged → recordEvent(UNCHANGED, fileCount), emit FILTERED status
   │     ├── Transitions to EMITTING_UPDATES (if files were buffered)
   │     │     or stays IDLE (if all files unchanged)
   │     └── Pushes initial fileCount to observer
   └── Returns ScannerInfo DTO
```

### Destruction

```
1. Agent removed (DELETE /api/agents/{id})
2. ScannerRegistry.destroyForAgent(scannerId)
   ├── Removes scanner from ConcurrentHashMap
   ├── Calls scanner.destroy()
   │     ├── Stops FileWatcherPort (closes WatchService)
   │     ├── Cancels pending FILTERED reset task
   │     └── Shuts down filteredResetScheduler
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
   │     │     │     └── Hash match → FILTERED status
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
   - **Hash match** → Unchanged file → `hashMatches() = true` → File is skipped, FILTERED status emitted

```java
FileHistory history = fileComparator.matches(metadata);

if (!history.hashMatches()) {
    // New or changed — emit
    long fileCount = fileCounter.countFiles(folderPath);  // compute once
    ScannerEventType eventType = history.previousFile().isEmpty()
        ? ScannerEventType.CREATION : ScannerEventType.MODIFICATION;
    observer.recordEvent(agentId, eventType, ScannerStatus.EMITTING_UPDATES,
                         folderPath, null, fileCount);  // push fileCount
    fileMetadataStore.save(metadata);
    emitWithDelay(history);
} else {
    // Unchanged — skip
    long fileCount = fileCounter.countFiles(folderPath);
    observer.recordEvent(agentId, ScannerEventType.UNCHANGED,
                         ScannerStatus.FILTERED, folderPath, null, fileCount);
    cancelAndScheduleFilteredReset();
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

Metrics are tracked via `ScannerObserverService` (not Micrometer). Each scanner has three metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `fileCount` | Gauge | Current number of files in the watched directory (pushed from scanner) |
| `totalDiscovered` | Counter | Total files found (initial scan + incremental) |
| `unchanged` | Counter | Files whose hash matches previous record (skipped) |

### Metrics Event Flow (Push-Based)

```
ScannerService.processRawEvent(rawEvent)
  └─ fileCounter.countFiles(folderPath)              ← computed once
  └─ observer.recordEvent(agentId, eventType, status, folderPath, null, fileCount)
       ├─ stores fileCount in AgentMetrics
       └─ pushToUI(agentId, status, eventType, folderPath, null, fileCount)
            └─ callbacks.forEach(cb -> cb.accept(new ScannerMetricsEvent(..., fileCount)))
                 └─ ScannerListView.refreshCallback
                      └─ ui.access(() → refreshScanners())
                           └─ scannerService.getAllScannerInfos()
                                └─ grid.setItems(scanners)
                                     └─ reads info.fileCount() from DTO
```

**Key change**: File count is **pushed** from `ScannerService` during event processing, not pulled via `countFiles()` on every render. The scanner owns file counting; the observer owns metrics tracking and event distribution.

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
    observer                         // metrics + UI push
);

ScannerMetadata metadata = new ScannerMetadata(
    scanner, agentId, targetDirectory, ScannerStatus.IDLE,
    LocalDateTime.now(), null, null);

scanners.put(agentId, metadata);
scanner.initSource(agentId);  // triggers initial scan, computes fileCount
```

### Status Change Callback Chain

```java
// ScannerService processes an unchanged file event
private void cancelAndScheduleFilteredReset() {
    if (filteredResetTask != null) {
        filteredResetTask.cancel(false);
    }
    notifyStatusChange(ScannerStatus.FILTERED);  // pushes via observer callback
    filteredResetTask = filteredResetScheduler.schedule(
        () -> notifyStatusChange(ScannerStatus.IDLE), 2, TimeUnit.SECONDS);
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
}```
```

---

## Testing

### Test Classes

| Test Class | Layer | What it verifies |
|------------|-------|------------------|
| `ScannerObserverServiceTest` | Application | Metrics tracking, callback registration, concurrency, fileCount storage and preservation |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, full scan, fileCount computation and push, status transitions |
| `ScannerServiceTest` (UI) | Inbound adapter | `getAllScannerInfos()` conversion, error handling |
| `ScannerListViewTest` | Inbound adapter | Route and page title annotations |
| `ScannerRegistryTest` | Application | Registry CRUD operations |
| `ScannerRegistryIntegrationTest` | Application | Full agent-scanner lifecycle, flux connectivity |

### Example: Testing File Count Push

```java
@Test
void givenFileEventWithFileCount_WhenRecorded_ThenStoredInMetrics() {
    useCase.recordEvent("agent-1", CREATION, EMITTING_UPDATES,
            "/tmp/agent-1", null, 42L);
    assertThat(useCase.getMetrics("agent-1").fileCount()).isEqualTo(42L);
}

@Test
void givenFileEventWithFileCount_WhenPushed_ThenEventContainsFileCount() {
    CopyOnWriteArrayList<ScannerMetricsEvent> events = new CopyOnWriteArrayList<>();
    useCase.registerRefreshCallback(events::add);
    useCase.recordEvent("agent-1", CREATION, EMITTING_UPDATES,
            "/tmp/agent-1", null, 77L);
    assertThat(events.get(0).fileCount()).isEqualTo(77L);
}

@Test
void givenFileEventWithCount_WhenEmissionRecorded_ThenFileCountPreserved() {
    // Regression test: emission events must not reset fileCount to 0
    useCase.recordEvent("agent-1", CREATION, EMITTING_UPDATES,
            "/tmp/agent-1", null, 7L);
    useCase.recordEmission("agent-1");
    useCase.recordEvent("agent-1", null, EMITTING_UPDATES, null, null, 7L);
    assertThat(useCase.getMetrics("agent-1").fileCount()).isEqualTo(7L);
}
```

### Example: Testing Status Transitions

```java
@Test
void givenNewFileEvent_WhenProcessed_ThenStatusTransitionsToEmittingUpdates() throws Exception {
    // Capture status via callback
    CopyOnWriteArrayList<ScannerStatus> statusHistory = new CopyOnWriteArrayList<>();
    ScannerObserverService statusObserver = new ScannerObserverService();
    statusObserver.registerRefreshCallback(e -> statusHistory.add(e.status()));

    // Create scanner with flux that emits a CREATION event
    RawFileEvent rawEvent = new RawFileEvent(testFile, testContent);
    FileWatcherPort emittingWatcher = mock(FileWatcherPort.class);
    when(emittingWatcher.flux()).thenReturn(Flux.just(rawEvent));
    // ... configure watcher ...

    scanner = new ScannerService("agent", inputDir.toString(),
        Duration.ofMillis(500), Duration.ZERO, emittingWatcher,
        comparator, fileCounter, statusObserver);
    scanner.initSource("agent");
    Thread.sleep(1000);

    // Assert both statuses present
    assertThat(statusHistory).contains(EMITTING_INITIAL, EMITTING_UPDATES);
}

@Test
void givenUnchangedFileEvent_WhenProcessed_ThenStatusTransitionsToFiltered() throws Exception {
    // Pre-populate repo with matching hash so hashMatches() returns true
    FileMetadata existingMeta = new FileMetadata(fileName, content, hash);
    when(matchingRepo.findById(fileName)).thenReturn(Optional.of(existingMeta));

    // ... create scanner with matching comparator ...
    scanner.initSource("agent");
    Thread.sleep(1000);

    assertThat(statusHistory).contains(FILTERED);
}
```

---

## Migration Notes

### What Changed from the Old Architecture

| Aspect | Old | New |
|--------|-----|-----|
| Metrics service | `ScannerObserverUseCase` / `ScannerMetricsService` | `ScannerObserverService` (implements `ScannerMetricsPort`) |
| Metrics snapshot | `ScannerMetricsSnapshot` | `ScannerMetrics` (includes `fileCount`) |
| Event type | `ScannerMetricsChangedEvent` (Spring event) | `ScannerMetricsEvent` (domain record, includes `fileCount`) |
| Push bridge | `ScannerMetricsPushService` (@Service) | Direct callback list in `ScannerObserverService` |
| UI refresh | `grid.getDataProvider().refreshAll()` | `grid.setItems(scanners)` (re-fetches data) |
| Status colors | Gray IDLE, Green ACTIVE | Green IDLE, Amber EMITTING_INITIAL, Blue EMITTING_UPDATES, Orange FILTERED |
| File count | `getMetrics().fileCount` (computed on-demand) | `ScannerInfoDTO.fileCount` (pushed from scanner) |
| File count source | `observer.countFiles(agentId)` (pull-based) | `info.fileCount()` from DTO (push-based) |
| Folder storage | `storeFolder()` in observer | Removed — no longer needed |
| File counting location | Observer (via `FileCounterPort`) | Scanner (via `FileCounterPort`) |
| Hexagonal separation | Observer accessed filesystem | Observer is pure messenger; scanner owns filesystem access |

### Push-Based FileCount Architecture

**Before (pull-based)**:
```
UI → observer.countFiles(agentId)
         → agentFolders.get(agentId)
              → fileCounter.countFiles(folderPath)
```
Required `storeFolder()` to be called, `countFiles()` on the port interface, `agentFolders` map in the observer, and a separate call from UI to observer for file count.

**After (push-based)**:
```
ScannerService.processRawEvent()
  └─ fileCounter.countFiles(folderPath)         ← computed once
  └─ observer.recordEvent(agentId, ..., fileCount)
       └─ pushToUI(agentId, status, fileCount)
            └─ UI renders directly from event data or getAllScannerInfos()

ScannerInfoDTO now includes fileCount — no separate observer call needed.
```

The observer becomes a pure messenger — it receives data from the scanner and pushes to UI. It no longer reaches into the filesystem or tracks folder paths. The scanner owns file counting; the observer owns metrics tracking and event distribution.

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

---

## See Also

- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanners, `ScannerRegistry` API
- [DPR: Scanner Observability](dpr-scanner-observability.md) — Metrics instrumentation and real-time UI updates
- [DPR: File History Model](dpr-file-history-model.md) — `FileHistory` event model, hashing, and metadata storage
