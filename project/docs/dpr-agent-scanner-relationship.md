# DPR: Agent-Scanner Relationship

> **Purpose**: This document describes how agents are associated with scanners, the `ScannerRegistry` API, and the dynamic lifecycle of scanner instances. Each scanner is owned by exactly one agent (one-to-one mapping).

---

## Overview

In the current architecture, **each agent owns exactly one scanner**. This is a one-to-one relationship — a scanner is created when an agent is created and destroyed when the agent is removed. This simplifies the model compared to the previous many-to-one subscription approach.

---

## Relationship Model

### One-to-One Mapping

| Relationship | Description |
|-------------|-------------|
| **One-to-one** | One agent owns one scanner | Agent watches `/project/src` via its own scanner |

```
Agent-1 ──► Scanner-1 (folder: /data/inbox)
Agent-2 ──► Scanner-2 (folder: /data/output)
Agent-3 ──► Scanner-3 (folder: /data/inbox)   ← Same folder, separate scanner
```

### Why One-to-One?

- **Isolation**: Each agent has its own scanner lifecycle — one agent's errors don't affect others
- **Simplicity**: No subscription tracking, no shared flux complexity
- **Independent refresh**: Each scanner can be reset to full-scan mode independently
- **Clear ownership**: `agentId` is both the scanner key and the ownership identifier

---

## ScannerRegistry API

The `ScannerRegistry` manages scanner instances and tracks metadata per agent:

```java
@Component
public class ScannerRegistry {

    // Lifecycle
    ScannerInfo createForAgent(String agentId, String targetDirectory, int delaySeconds);
    void destroyForAgent(String scannerId);
    void refreshAgent(String scannerId);

    // Queries
    Flux<FileHistory> getScannerFlux(String scannerId);
    List<ScannerInfo> listAll();
    Optional<ScannerInfo> getById(String agentId);

    // Status management
    void updateStatus(String scannerId, String status);
    void transitionToError(String agentId, String reason);
    void recoverFromError(String agentId);
    void recordEmission(String agentId);

    // Internal (for testing)
    Optional<ScannerMetadata> getMetadata(String scannerId);
}
```

### ScannerInfo DTO

```java
public record ScannerInfo(
    String id,                     // Unique scanner ID (same as agentId)
    String agentId,                // Owning agent ID
    String targetDirectory,        // Directory being watched
    String status,                 // IDLE, EMITTING_INITIAL, EMITTING_UPDATES, FILTERED, ERROR
    LocalDateTime createdAt,       // When the scanner was created
    LocalDateTime lastEmittedAt,   // Last time a file was emitted
    String errorMessage,           // Error message, if in ERROR state
    Long fileCount                 // ← pushed from scanner, includes file count
) {}
```

### ScannerMetadata (Internal)

```java
private record ScannerMetadata(
    FileSystemScannerAdapter scanner,
    String agentId,
    String folderPath,
    String status,
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt,
    String errorMessage
) {
    ScannerMetadata withStatus(String newStatus) { ... }
    ScannerMetadata withError(String errorMsg) { ... }
}
```

---

## Scanner Lifecycle

### Creation Flow

```
Agent Created (POST /api/agents)
        │
        ▼
AgentLifecycleUseCase.addDynamicAgent(agent, targetDir)
        │
        ▼
ScannerRegistry.createForAgent(agentId, targetDir, delaySeconds)
        │
        ├── Validate directory exists and is readable
        │
        ├── Check for duplicate (return existing if agentId already registered)
        │
        ├── Create FileSystemScannerAdapter
        │     ├── Initialize NativeFileWatcher
        │     ├── Set up callbacks: onStatusChanged, onError, onEmission
        │     └── Start filtered reset scheduler
        │
        ├── Register in ConcurrentHashMap<agentId, ScannerService>
        │
        ├── Call scanner.initSource(agentId)
        │     ├── fileCounter.countFiles(folderPath)          ← compute fileCount once
        │     ├── Transition to EMITTING_INITIAL
        │     ├── Start NativeFileWatcher (initial full scan)
        │     ├── Hash filter processes all existing files
        │     │     ├── New/changed → recordEvent(CREATION, fileCount), emit FileHistory
        │     │     └── Unchanged → recordEvent(UNCHANGED, fileCount), STATUS_FILTERED
        │     ├── Transition to EMITTING_UPDATES (if files buffered)
        │     │     or stays IDLE (if all files unchanged)
        │     └── Push initial fileCount to observer
        │
        └── Return ScannerInfo DTO
```

### Destruction Flow

```
Agent Removed (DELETE /api/agents/{id})
        │
        ▼
AgentLifecycleUseCase.removeAgent(agentId)
        │
        ▼
ScannerRegistry.destroyForAgent(scannerId)
        │
        ├── Remove from ConcurrentHashMap
        │
        ├── Call adapter.destroy()
        │     ├── Stop NativeFileWatcher (closes WatchService)
        │     ├── Cancel pending FILTERED reset task
        │     └── Shut down filteredResetScheduler
        │
        └── Log destruction
```

### Refresh Flow

```
Agent Refreshed (POST /api/agents/{id}/refresh)
        │
        ▼
AgentLifecycleUseCase.refreshAgent(agentId)
        │
        ▼
ScannerRegistry.refreshAgent(scannerId)
        │
        ├── Find scanner by agentId
        │
        ├── Call adapter.resetToFullScan()
        │     ├── Transition to EMITTING_INITIAL
        │     ├── scanAllFiles() — walk directory, emit new files
        │     │     ├── Hash mismatch → emit FileHistory
        │     │     └── Hash match → STATUS_FILTERED
        │     └── Transition to EMITTING_UPDATES
        │
        └── Log refresh complete
```

---

## AgentLifecycleUseCase Integration

The `AgentLifecycleUseCase` coordinates between agents and the scanner registry:

```java
public class AgentLifecycleUseCase {

    private final ScannerRegistry scannerRegistry;
    private final FileWriter fileWriter;
    private final Path outputDir;
    private final ChatClient chatClient;

    public AgentInfo addDynamicAgent(AgentDefinition def, String targetDir) {
        // Create scanner (or get existing)
        ScannerInfo scannerInfo = scannerRegistry.createForAgent(
            def.id(), targetDir, 5);

        // Create agent with scanner flux
        Flux<FileHistory> flux = scannerRegistry.getScannerFlux(def.id());

        // Build agent pipeline...
        // ...

        return agentInfo;
    }

    public void removeAgent(String id) {
        scannerRegistry.destroyForAgent(id);
        // Remove agent from pipeline...
    }

    public AgentInfo refreshAgent(String id) {
        scannerRegistry.refreshAgent(id);
        // Return updated agent info...
    }
}
```

---

## Error Handling

### Inaccessible Folders

**Fail fast** with a clear error message during agent creation:

```java
if (!Files.exists(folderPath)) {
    throw new IllegalArgumentException("Target directory does not exist: " + targetDirectory);
}
if (!Files.isDirectory(folderPath)) {
    throw new IllegalArgumentException("Target path is not a directory: " + targetDirectory);
}
if (!Files.isReadable(folderPath)) {
    throw new IllegalArgumentException("Target directory is not readable: " + targetDirectory);
}
```

### Scanner Error Recovery

If a scanner encounters an unrecoverable error (e.g., directory becomes inaccessible):

1. `FileSystemScannerAdapter` calls the error callback: `onErrorCallback.accept(errorMsg)`
2. `ScannerRegistry.transitionToError(agentId, reason)` transitions the scanner to `ERROR` status
3. The error is logged and a `ScannerMetricsChangedEvent.errorOccurred()` event is published
4. Recovery is manual: `ScannerRegistry.recoverFromError(agentId)` resets to `EMITTING_INITIAL` and triggers a full rescan

```java
// Recovery flow
public void recoverFromError(String agentId) {
    ScannerMetadata updated = meta.withStatus(STATUS_EMITTING_INITIAL)
                                   .withError(null);
    scanners.put(key, updated);
    meta.scanner().resetToFullScan();
    pushMetricsEvent(ScannerMetricsChangedEvent.recoveredFromError(agentId));
}
```

---

## Idle Detection

A shared `ScheduledExecutorService` runs every 10 seconds to check all scanners for inactivity:

```java
private void checkAllScannersForIdle() {
    LocalDateTime now = LocalDateTime.now();
    for (Map.Entry<String, ScannerMetadata> entry : scanners.entrySet()) {
        ScannerMetadata meta = entry.getValue();

        // Only check scanners that are actively emitting
        if (!STATUS_EMITTING_UPDATES.equals(meta.status())) {
            continue;
        }

        // If no emission for IDLE_TIMEOUT (30s), transition to IDLE
        LocalDateTime lastEmit = meta.lastEmittedAt();
        if (lastEmit != null) {
            Duration sinceLastEmission = Duration.between(lastEmit, now);
            if (sinceLastEmission.compareTo(IDLE_TIMEOUT) >= 0) {
                updateStatus(meta.agentId(), STATUS_IDLE);
                pushMetricsEvent(ScannerMetricsChangedEvent.idleReached(meta.agentId()));
            }
        }
    }
}
```

---

## Test Coverage

The agent-scanner relationship is covered by the following test classes:

| Test | Type | What it verifies |
|------|------|-----------------|
| `ScannerRegistryTest` | Unit | CRUD operations, status updates, duplicate handling |
| `ScannerRegistryIntegrationTest` | Integration | Full agent-scanner lifecycle, flux connectivity, multiple agents, refresh, delete |
| `FileSystemScannerAdapterTest` | Integration | Adapter lifecycle, flux behavior, watch service events |
| `FileSystemScannerAdapterFilteredStatusTest` | Unit | FILTERED status emission for unchanged files |

### Example: Integration Test

```java
@Test
void givenAgentAdded_WhenScannerCreated_ThenScannerExistsAndIsListed() {
    AgentDefinition agent = createAgentDefinition("AGENT-CREATE-TEST");
    AgentInfo agentInfo = agentManager.addDynamicAgent(agent, inputDir.toString());

    // Scanner is listed in registry
    List<ScannerInfo> scanners = scannerRegistry.listAll();
    assertThat(scanners).hasSize(1);
    assertThat(scanners.get(0).agentId()).isEqualTo(agentInfo.id());
    assertThat(scanners.get(0).status()).isEqualTo("IDLE");
}

@Test
void givenAgentAdded_WhenAgentRemoved_ThenScannerDestroyed() {
    AgentInfo agentInfo = agentManager.addDynamicAgent(agent, inputDir.toString());
    assertThat(scannerRegistry.listAll()).hasSize(1);

    agentManager.removeAgent(agentInfo.id());

    assertThat(scannerRegistry.listAll()).hasSize(0);
}
```

---

## See Also

- [DPR: Scanner Concept](dpr-scanner-concept.md) — Scanner lifecycle, status transitions, hash-based change detection
- [DPR: Scanner Observability](dpr-scanner-observability.md) — Metrics tracking and real-time UI updates
- [DPR: File History Model](dpr-file-history-model.md) — FileHistory event model, hashing, and metadata storage
