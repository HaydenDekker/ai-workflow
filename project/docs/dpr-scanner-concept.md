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
│  NativeFileWatcher                                                │
│  └── Reads file, computes hash, compares with stored metadata    │
│  └── Triggers callbacks: onDiscovery, onUnchanged, onFiltered    │
│  └── Emits FileHistory through Sinks.Many (reactive)             │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  FileSystemScannerAdapter                                         │
│  └── Bridges NativeFileWatcher to ScannerRegistry                │
│  └── Manages status transitions (IDLE → EMITTING_INITIAL → …)    │
│  └── Controls emission delay throttling                          │
│  └── Schedules FILTERED → IDLE reset after 2s                    │
└──────────────────────┬───────────────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ScannerRegistry  Observer    Event Publisher
   (status mgmt)   (metrics)   (Spring events)
```

### Key Classes

| Class | Role | Package |
|-------|------|---------|
| `NativeFileWatcher` | Pure NIO file watcher, reads files, computes hashes, emits `FileHistory` | `files/` |
| `FileSystemScannerAdapter` | Bridges watcher to registry, manages status transitions, controls emission | `files/` |
| `ScannerRegistry` | Lifecycle management, one adapter per agent, status tracking, idle detection | `pipeline/management/` |
| `FileComparator` | Compares file hash against stored metadata to detect changes | `files/` |
| `FileHash` | Computes SHA-256 hash of file content | `files/` |
| `ScannerObserverUseCase` | Tracks metrics (discovered, unchanged, fileCount) per agent | `usecases/` |
| `ScannerMetricsChangedEvent` | Spring event published when metrics change | `ui/events/` |

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
   ├── Creates FileSystemScannerAdapter
   ├── Registers adapter in ConcurrentHashMap<agentId, ScannerMetadata>
   ├── Calls adapter.initSource(agentId)
   │     ├── Transitions to EMITTING_INITIAL
   │     ├── Starts NativeFileWatcher (initial full scan)
   │     ├── Hash filter processes all existing files
   │     │     ├── New/changed → recordDiscovery, emit FileHistory
   │     │     └── Unchanged → recordUnchanged, emit STATUS_FILTERED
   │     ├── Transitions to EMITTING_UPDATES (if files were buffered)
   │     │     or stays IDLE (if all files unchanged)
   │     └── Updates fileCount gauge
   └── Returns ScannerInfo DTO
```

### Destruction

```
1. Agent removed (DELETE /api/agents/{id})
2. ScannerRegistry.destroyForAgent(scannerId)
   ├── Removes scanner from ConcurrentHashMap
   ├── Calls adapter.destroy()
   │     ├── Stops NativeFileWatcher (closes WatchService)
   │     ├── Cancels pending FILTERED reset task
   │     └── Shuts down filteredResetScheduler
   └── Cleans up all resources
```

### Refresh (Reset to Full Scan)

```
1. Agent refreshed (POST /api/agents/{id}/refresh)
2. ScannerRegistry.refreshAgent(scannerId)
   ├── Calls adapter.resetToFullScan()
   │     ├── Transitions to EMITTING_INITIAL
   │     ├── Calls scanAllFiles()
   │     │     ├── Walks directory tree
   │     │     ├── For each file:
   │     │     │     ├── Hash mismatch → emit FileHistory
   │     │     │     └── Hash match → STATUS_FILTERED
   │     │     └── Updates fileCount
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
    observer.recordDiscovery(agentId);
    fileMetadataStore.save(metadata);
    nativeFileWatcher.emit(history);
} else {
    // Unchanged — skip
    observer.recordUnchanged(agentId);
    onFiltered.accept(directory.toString());
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

Metrics are tracked via `ScannerObserverUseCase` (not Micrometer). Each scanner has three metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `fileCount` | Gauge | Current number of files in the watched directory |
| `totalDiscovered` | Counter | Total files found (initial scan + incremental) |
| `unchanged` | Counter | Files whose hash matches previous record (skipped) |

### Metrics Event Flow

```
NativeFileWatcher.emitFile()
  └─ observer.recordDiscovery(agentId)  /  observer.recordUnchanged(agentId)
       └─ publishes ScannerMetricsChangedEvent
            └─ ScannerMetricsPushService.onScannerMetricsChanged()
                 └─ metricsService.pushToUI(event)
                      └─ callback.accept(event)
                           └─ ui.access(() → grid.refreshAll())
```

### Event Types

| Event Type | When Published |
|------------|----------------|
| `"discovered"` | New or changed file detected |
| `"unchanged"` | File hash matches stored metadata |
| `"file_count"` | File count updated (after initial scan, reset, or file event) |

---

## Code Examples

### Creating a Scanner

```java
// In ScannerRegistry.createForAgent()
FileSystemScannerAdapter scanner = new FileSystemScannerAdapter(
    agentId,
    targetDirectory,
    delay,                           // poll interval (e.g., 5 seconds)
    emissionDelay,                   // emission throttle (e.g., 2 seconds)
    fileMetadataDatabase,            // hash comparison store
    observer,                        // metrics tracking
    metricsEventPublisher,           // Spring event publisher
    errMsg -> transitionToError(agentId, errMsg),  // error handler
    newStatus -> updateStatus(agentId, newStatus), // status callback
    aId -> recordEmission(aId)       // emission callback
);

ScannerMetadata metadata = new ScannerMetadata(
    scanner, agentId, targetDirectory, STATUS_IDLE,
    LocalDateTime.now(), null, null);

scanners.put(agentId, metadata);
scanner.initSource(agentId);  // triggers initial scan
```

### Status Change Callback Chain

```java
// FileSystemScannerAdapter receives the callback from NativeFileWatcher
aId -> {
    // Hash filter rejected a file
    if (onStatusChanged != null) {
        if (filteredResetTask != null) {
            filteredResetTask.cancel(false);
        }
        onStatusChanged.accept(STATUS_FILTERED);
        // Schedule reset to IDLE after 2 seconds
        filteredResetTask = filteredResetScheduler.schedule(
            () -> onStatusChanged.accept(STATUS_IDLE), 2, TimeUnit.SECONDS);
    }
}
```

### Reading Metrics in the UI

```java
// In ScannerListView — column definition
grid.addColumn(info -> {
    try {
        ScannerMetricsSnapshot m = metricsService.getMetrics(info.agentId());
        return m.fileCount() + " files";
    } catch (Exception e) {
        return "—";
    }
}).setHeader("Files").setAutoWidth(true);
```

---

## Testing

### Test Classes

| Test | Type | What it verifies |
|------|------|-----------------|
| `FileSystemScannerAdapterTest` | Integration | Adapter lifecycle, flux behavior, watch service events |
| `FileSystemScannerAdapterMetricsTest` | Unit (Mockito) | Metrics counters increment correctly, events published |
| `FileSystemScannerAdapterFilteredStatusTest` | Unit (Mockito) | FILTERED status emitted for unchanged files, not for new files |
| `FileSystemSimplePollerFluxAdapterTest` | Integration | File creation/modification detection via watch service |
| `NativeFileWatcherMetricsTest` | Unit | NativeFileWatcher callbacks invoked correctly |
| `ScannerObserverUseCaseTest` | Unit | Metrics tracking, callback registration, thread safety |
| `ScannerRegistryTest` | Unit | Registry CRUD operations, status updates |
| `ScannerRegistryIntegrationTest` | Integration | Full agent-scanner lifecycle, flux connectivity |

### Example: Testing FILTERED Status

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

## Migration Notes

### What Changed from the Old Architecture

| Aspect | Old (Spring Integration) | New (NativeFileWatcher) |
|--------|-------------------------|------------------------|
| Watch service | Spring Integration `FileReadingMessageSource` | Pure NIO `WatchService` |
| Metrics | Micrometer counters/gauges | `ScannerObserverUseCase` with `AtomicLong` |
| Events | Spring Integration message channel | Reactor `Sinks.Many` |
| Rate limiting | `delayElements()` on Flux | Coalescing buffer with `emissionDelay` |
| Metrics publishing | `MeterRegistry` direct | `ScannerMetricsChangedEvent` Spring events |
| Status tracking | External status management | Built-in `FileSystemScannerAdapter` status transitions |
| FILTERED status | Not present | New status for hash-filtered files |

---

## See Also

- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanners, `ScannerRegistry` API
- [DPR: Scanner Observability](dpr-scanner-observability.md) — Metrics instrumentation and real-time UI updates
- [DPR: File History Model](dpr-file-history-model.md) — `FileHistory` event model, hashing, and metadata storage
