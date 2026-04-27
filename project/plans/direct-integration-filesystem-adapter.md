# Plan: Direct Integration FileSystem Adapter

**Status**: Draft  
**Related**: [`agent-scanners.md`](./agent-scanners.md) — Phase 2 adapter architecture  
**Created**: 2026-04-25  
**Problem**: `FluxMessageChannel` delivery failure — message sent to channel with no subscribers  

---

## 1. Problem

### Error

```
java.lang.IllegalStateException: The [bean 'fileInboundFluxChannel'] doesn't have 
subscribers to accept messages
    at org.springframework.integration.channel.FluxMessageChannel.doSend(FluxMessageChannel.java:72)
```

### Root Cause

`FileSystemScannerAdapter.initFlow()` uses a **two-step channel setup**:

1. `IntegrationFlowContext.registration(flow).register()` — registers the flow, which internally creates a `FluxMessageChannel` named `fileInboundFluxChannel`.
2. A new `FluxMessageChannel` is **manually constructed** in the adapter code and subscribed to via `IntegrationReactiveUtils.messageChannelToFlux(filesChannel)`.

The problem: the manually-created `filesChannel` is **not** the same instance as the one inside the registered flow. The flow's internal channel gets subscribers from the flow's transform/filter chain, but the adapter's separate `filesChannel` has no subscriber connected to the flow's output. When the poller fires, Spring Integration tries to send to the flow's internal channel (which works), but the adapter's own subscription to its `filesChannel` never receives anything — and in certain timing scenarios, the `FluxMessageChannel` send path fails because the flow's internal plumbing hasn't fully settled.

The `FileSystemSimplePollerFluxAdapterTest` prototype avoids this entirely by using:

```java
FileReadingMessageSource source = new FileReadingMessageSource();
source.setDirectory(folder);
source.setUseWatchService(true);
// ...
return IntegrationReactiveUtils.messageSourceToFlux(source)
    .map(...)
    .filter(...)
    .onBackpressureBuffer();
```

No `IntegrationFlowContext`, no `IntegrationFlowRegistration`, no `FluxMessageChannel` intermediary. **Direct from source to Flux.**

---

## 2. Target Architecture

### Current (broken)

```
┌──────────────────────────────────────────────────────────────┐
│ FileSystemScannerAdapter                                     │
│                                                              │
│  IntegrationFlowContext                                      │
│       │                                                      │
│       ▼                                                      │
│  IntegrationFlowRegistration                                 │
│       │                                                      │
│       ▼                                                      │
│  IntegrationFlow (DSL)                                       │
│  ┌─────────────────────────────────────┐                     │
│  │ Files.inboundAdapter(folder)        │                     │
│  │   ──► toStringTransformer()         │                     │
│  │   ──► channel("fileInboundFlux")    │                     │
│  └─────────────────────────────────────┘                     │
│                                                              │
│  FluxMessageChannel (manual, disconnected) ←─── NO SUBSCRIBER│
│       │                                                      │
│       ▼                                                      │
│  Sinks.Many<FileHistory>                                     │
└──────────────────────────────────────────────────────────────┘
```

### Target (prototype approach)

```
┌──────────────────────────────────────────────────────────────┐
│ FileSystemScannerAdapter (simplified)                        │
│                                                              │
│  FileReadingMessageSource                                    │
│  ┌───────────────────────────────────┐                       │
│  │ directory = folderPath            │                       │
│  │ useWatchService = true            │                       │
│  │ watchEvents = CREATE/MODIFY/DELETE│                       │
│  └───────────────────────────────────┘                       │
│             │                                                │
│             ▼                                                │
│  IntegrationReactiveUtils.messageSourceToFlux(source)        │
│             │                                                │
│             ▼                                                │
│  Flux<FileMetadata> ──► FileComparator ──► .filter()         │
│             │                                                │
│             ▼                                                │
│  Sinks.Many<FileHistory>                                     │
│             │                                                │
│             ▼                                                │
│  Flux<FileHistory> (public flux())                           │
└──────────────────────────────────────────────────────────────┘
```

**Key difference**: No `IntegrationFlowContext`, no `IntegrationFlowRegistration`, no `FluxMessageChannel`. Direct pipeline from source to sink.

---

## 3. Implementation Steps

### Step 1: Refactor `FileSystemScannerAdapter` to use direct `FileReadingMessageSource`

**File**: `src/main/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapter.java`

**Changes**:
- Remove `IntegrationFlowContext` dependency from constructor
- Remove `IntegrationFlowRegistration` field
- Remove `initFlow()` method (the entire DSL-based flow setup)
- Create `FileReadingMessageSource` directly in constructor with watch service configured
- Convert source to Flux via `IntegrationReactiveUtils.messageSourceToFlux(source)`
- Keep the same `.map()` / `.filter()` / `.doOnNext()` chain for:
  - File content → `FileMetadata`
  - `FileComparator` match check → `FileHistory`
  - Filter out unchanged files
  - Save to `FileMetadataDatabase`
  - Emit through `Sinks.Many<FileHistory>`

**Constructor signature change**:

```java
// Before:
public FileSystemScannerAdapter(
    String folderPath,
    Duration delayBetweenReads,
    IntegrationFlowContext integrationFlowContext,
    FileMetadataDatabase fileMetadataDatabase)

// After:
public FileSystemScannerAdapter(
    String folderPath,
    Duration delayBetweenReads,
    FileMetadataDatabase fileMetadataDatabase)
```

**Removed imports**:
- `org.springframework.integration.dsl.IntegrationFlow`
- `org.springframework.integration.dsl.IntegrationFlowBuilder`
- `org.springframework.integration.dsl.IntegrationFlowContext`
- `org.springframework.integration.dsl.IntegrationFlowContext.IntegrationFlowRegistration`
- `org.springframework.integration.dsl.Pollers`
- `org.springframework.integration.channel.FluxMessageChannel`
- `org.springframework.integration.util.IntegrationReactiveUtils` (keep — still needed)
- `org.springframework.integration.file.dsl.Files`

**New imports**:
- `org.springframework.integration.file.inbound.FileReadingMessageSource`

### Step 2: Update `ScannerRegistry` to not pass `IntegrationFlowContext`

**File**: `src/main/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistry.java`

**Changes**:
- Remove `IntegrationFlowContext` from constructor
- Remove `IntegrationFlowRegistration` references from `ScannerMetadata` record (if it exists)
- The `createForAgent()` method now creates `FileSystemScannerAdapter` without `integrationFlowContext`
- Remove any `flowRegistration.start()` / `flowRegistration.destroy()` calls
- `destroy()` method now just calls `meta.scanner().destroy()` (no flow cleanup)

**Constructor signature change**:

```java
// Before:
public ScannerRegistry(
    IntegrationFlowContext integrationFlowContext,
    ApplicationContext applicationContext,
    FileMetadataDatabase fileMetadataDatabase)

// After:
public ScannerRegistry(
    ApplicationContext applicationContext,
    FileMetadataDatabase fileMetadataDatabase)
```

### Step 3: Update `DynamicAgentManagerConfiguration` to not wire `IntegrationFlowContext`

**File**: `src/main/java/com/hdekker/ai_workflow/pipeline/management/DynamicAgentManagerConfiguration.java`

**Changes**:
- Remove `IntegrationFlowContext` parameter from `ScannerRegistry` bean creation
- Keep `ApplicationContext` and `FileMetadataDatabase`

### Step 4: Update tests

**Files**:
- `src/test/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapterTest.java`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistryTest.java`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistryIntegrationTest.java`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManagerTest.java`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManagerPersistenceTest.java`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManagerScannerRestoreTest.java`

**Changes**:
- Remove `IntegrationFlowContext` from all constructor calls
- Update mock setups — no need to mock flow registration
- Existing tests should pass with minimal changes since the adapter's public API (`flux()`, `resetToFullScan()`, `destroy()`, `isDisposed()`) remains unchanged

### Step 5: Remove `FileSystemRecursiveFileScannerAdapter` or simplify it

**File**: `src/main/java/com/hdekker/ai_workflow/files/FileSystemRecursiveFileScannerAdapter.java`

**Decision**: This was the original singleton adapter. Since `FileSystemScannerAdapter` now handles everything (and is the only adapter), this file can be:
- **Option A (recommended)**: Deleted — it's only referenced for backward compat with YAML agents. If YAML agents are being deprecated, delete it.
- **Option B**: Refactor to delegate to `FileSystemScannerAdapter` with a fixed path.

### Step 6: Handle full-scan reset

**Challenge**: The prototype's `createFluxWithPoller` / `createFluxWithWatchService` create the source once and convert to Flux once. The current `FileSystemScannerAdapter` has `resetToFullScan()` which needs to:
1. Emit all current files in the directory
2. Continue watching for incremental changes

**Solution**: Keep the `Sinks.Many` approach but restructure `resetToFullScan()`:

```java
public void resetToFullScan() {
    // Complete the old sink to signal end of old batch
    sink.tryEmitComplete();
    
    // Scan all current files and emit them
    scanAllFiles();
}

private void scanAllFiles() {
    // Walk directory, compare hashes, emit new files through sink
    // (same logic as existing scanAllFiles())
}
```

The watch service continues to emit incremental changes through the same `Sinks.Many` because it was set up in the constructor with `setUseWatchService(true)`. Full scan is a one-time "burst" of existing files, followed by the watch service's ongoing incremental emissions.

---

## 4. Detailed Adapter Implementation

```java
public class FileSystemScannerAdapter implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScannerAdapter.class);

    private final String folderPath;
    private final Duration delayBetweenReads;
    private final FileMetadataDatabase fileMetadataDatabase;

    private final Sinks.Many<FileHistory> sink;
    private volatile FileReadingMessageSource messageSource;
    private volatile boolean disposed = false;

    public FileSystemScannerAdapter(
            String folderPath,
            Duration delayBetweenReads,
            FileMetadataDatabase fileMetadataDatabase) {
        this.folderPath = folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.fileMetadataDatabase = fileMetadataDatabase;
        this.sink = Sinks.many().multicast().directBestEffort();
        initSource();
    }

    private void initSource() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner for folder: {}", folder);

            messageSource = new FileReadingMessageSource();
            messageSource.setDirectory(folder.toFile());
            messageSource.setUseWatchService(true);
            messageSource.setWatchEvents(
                    FileReadingMessageSource.WatchEventType.CREATE,
                    FileReadingMessageSource.WatchEventType.MODIFY,
                    FileReadingMessageSource.WatchEventType.DELETE);

            // Build the reactive pipeline directly from source to sink
            Flux<FileHistory> sourceFlux = IntegrationReactiveUtils.messageSourceToFlux(messageSource)
                    .doOnSubscribe(s -> log.info("Starting scanner at {}", folderPath))
                    .map(msg -> {
                        File file = (File) msg.getPayload();
                        try {
                            String content = Files.readString(file.toPath());
                            String hash = FileHash.hash(content);
                            String relativePath = folder.relativize(file.toPath()).toString().replace("\\", "/");
                            return new FileMetadata(file.toPath().toAbsolutePath().toString(), content, hash);
                        } catch (IOException e) {
                            log.warn("Failed to read file: {}", file, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(fileComparator::matches)
                    .filter(fh -> {
                        boolean passes = !fh.hashMatches();
                        log.debug("Filter result for {}: {}", fh.currentFile().url(), passes);
                        return passes;
                    })
                    .doOnNext(fh -> {
                        log.debug("Saving to database: {}", fh.currentFile().url());
                        fileMetadataDatabase.save(fh.currentFile());
                    })
                    .onBackpressureBuffer();

            sourceFlux.subscribe(sink::tryEmitNext);

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
        }
    }

    @Override
    public Flux<FileHistory> flux() {
        return sink.asFlux().onBackpressureBuffer();
    }

    public void resetToFullScan() {
        log.info("Resetting scanner to full scan at: {}", folderPath);
        sink.tryEmitComplete();
        scanAllFiles();
        log.info("Full scan complete for: {}", folderPath);
    }

    private FileComparator fileComparator() {
        return new FileComparator(fileMetadataDatabase);
    }

    private void scanAllFiles() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            if (!Files.exists(folder)) {
                log.warn("Target folder does not exist: {}", folderPath);
                return;
            }

            FileComparator comparator = fileComparator();

            Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String hash = FileHash.hash(content);
                            String relativePath = folder.relativize(p).toString().replace("\\", "/");
                            FileMetadata metadata = new FileMetadata(p.toAbsolutePath().toString(), content, hash);
                            FileHistory history = comparator.matches(metadata);

                            if (!history.hashMatches()) {
                                log.debug("Full scan - emitting new file: {}", p);
                                fileMetadataDatabase.save(metadata);
                                sink.tryEmitNext(history);
                            }
                        } catch (IOException e) {
                            log.warn("Failed to read file during full scan: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
        }
    }

    public void destroy() {
        if (disposed) return;
        disposed = true;
        sink.tryEmitComplete();
        if (messageSource != null) {
            messageSource.stop();
        }
        log.info("Scanner destroyed for folder: {}", folderPath);
    }

    public boolean isDisposed() {
        return disposed;
    }

    public String getFolderPath() {
        return folderPath;
    }
}
```

---

## 5. File Change Summary

| File | Change |
|------|--------|
| `FileSystemScannerAdapter.java` | **Major refactor** — remove IntegrationFlow/FlowRegistration, use direct `FileReadingMessageSource` + `messageSourceToFlux()` |
| `ScannerRegistry.java` | Remove `IntegrationFlowContext` from constructor and all usages |
| `DynamicAgentManagerConfiguration.java` | Remove `IntegrationFlowContext` from bean wiring |
| `FileSystemScannerAdapterTest.java` | Remove `IntegrationFlowContext` mocks |
| `ScannerRegistryTest.java` | Remove `IntegrationFlowContext` mocks |
| `ScannerRegistryIntegrationTest.java` | Remove `IntegrationFlowContext` mocks |
| `DynamicAgentManagerTest.java` | Remove `IntegrationFlowContext` mocks |
| `DynamicAgentManagerPersistenceTest.java` | Remove `IntegrationFlowContext` mocks |
| `DynamicAgentManagerScannerRestoreTest.java` | Remove `IntegrationFlowContext` mocks |
| `FileSystemRecursiveFileScannerAdapter.java` | Consider deletion or simplify |

---

## 6. Testing Strategy

### Unit Tests (must pass)
- `FileSystemScannerAdapterTest` — adapter lifecycle, flux access, destroy idempotency
- `ScannerRegistryTest` — create, delete, list, duplicate prevention
- `DynamicAgentManagerTest` — agent creation with scanner assignment

### Integration Tests
- `FileSystemSimplePollerFluxAdapterTest` — **promote this to a main source test** (the prototype already validates the direct approach works)
- `ScannerRegistryIntegrationTest` — full lifecycle: create → refresh → delete → scanner cleanup

### What the prototype already proves
The `FileSystemSimplePollerFluxAdapterTest` tests demonstrate that:
1. ✅ File creation detected via polling
2. ✅ File modification detected with previous/next comparison
3. ✅ Unchanged files are skipped (timeout = no emission)
4. ✅ File creation detected via watch service
5. ✅ File modification detected via watch service

**These tests can be adapted (not rewritten) for the new adapter** by simply replacing the factory methods with direct `FileSystemScannerAdapter` instantiation.

---

## 7. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| `FileReadingMessageSource` lifecycle management | Source may leak file handles | Call `messageSource.stop()` in `destroy()` |
| No explicit polling rate control | Watch service may be too aggressive | Set `source.setDelay(delayBetweenReads.toMillis())` on the source |
| Full scan + watch service concurrent emissions | Duplicate file emissions during transition | `Sinks.Many.directBestEffort()` handles backpressure; `tryEmitComplete()` + re-scan is atomic enough |
| `messageSourceToFlux()` auto-starts the source | Source may start before we're ready | Subscribe to the Flux only after all downstream is set up — `IntegrationReactiveUtils.messageSourceToFlux()` creates a cold Flux; it starts on first subscription |
| `FileSystemRecursiveFileScannerAdapter` still referenced | Unused code, confusion | Delete or convert to delegate in Phase 4 |

---

## 8. Execution Order

1. **Step 1** — Refactor `FileSystemScannerAdapter` (big change, need to compile first)
2. **Step 2** — Update `ScannerRegistry` constructor
3. **Step 3** — Update `DynamicAgentManagerConfiguration`
4. **Step 4** — Update all tests (6 test files)
5. **Step 5** — Decide on `FileSystemRecursiveFileScannerAdapter` (deferred, non-blocking)
6. **Verify** — `./mvnw test` (unit tests)
7. **Verify** — `./mvnw verify` (all tests including integration)

---

*This plan replaces the IntegrationFlowRegistration-based adapter with the direct `FileReadingMessageSource` approach from the prototype, eliminating the `FluxMessageChannel` subscriber race condition.*
