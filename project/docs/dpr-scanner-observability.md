# DPR: Scanner Observability

> **Context**: This document describes the observability layer built on top of the dynamic multi-scanner architecture.

---

## Overview

This document describes **how** scanner observability is implemented — the metrics instrumentation, real-time event-driven UI updates, and the full data flow from file system events to the Vaadin dashboard.

Scanners are the bridge between the file system and the AI pipeline. They watch directories for file changes and emit `FileHistory` events through a reactive stream. Observability lets operators see what scanners are doing in real time: how many files they've discovered, how many were unchanged, and the current file count per directory.

The observability layer consists of three parts:
1. **Micrometer metrics** — counters and gauges attached to every scanner
2. **Spring events** — `ScannerMetricsChangedEvent` published on file activity
3. **Real-time UI** — `ScannerListView` updates the grid via `UI.access()`

---

## 1. Metrics Architecture

### 1.1 Metric Types

Three metrics are tracked per scanner, each tagged with `agentId` and `folder`:

| Metric | Type | Name | Tag: `agentId` | Tag: `folder` | Purpose |
|--------|------|------|----------------|---------------|---------|
| **Current file count** | Gauge | `ai_workflow.scanner.file_count` | Agent ID | Target directory path | Files currently in target directory |
| **Files discovered** | Counter | `ai_workflow.scanner.files_discovered` | Agent ID | Target directory path | Total files found (initial scan + incremental) |
| **Files unchanged** | Counter | `ai_workflow.scanner.files_unchanged` | Agent ID | Target directory path | Files whose hash matches previous record |

### 1.2 Why Micrometer, Not Database

- The database is H2 in-memory with `create-drop` — metrics would be lost on restart anyway
- Micrometer adds zero persistence dependency and has minimal overhead
- Gauges provide real-time values without polling the database
- `/actuator/metrics` endpoint is available for external tooling (Prometheus, Grafana)

### 1.3 Tag Strategy

All metrics are tagged with both `agentId` and `folder`:

```
ai_workflow.scanner.files_discovered{agentId="my-agent", folder="/data/inbox"}
ai_workflow.scanner.files_unchanged{agentId="my-agent", folder="/data/inbox"}
ai_workflow.scanner.file_count{agentId="my-agent", folder="/data/inbox"}
```

- **`agentId`** — used by the UI to look up the correct metrics per scanner row
- **`folder`** — provides a secondary dimension for observability (useful for /actuator/metrics endpoint)
- Both are low-cardinality: one value per scanner, never file names or hashes

---

## 2. Instrumentation: FileSystemScannerAdapter

The `FileSystemScannerAdapter` is the entry point. It owns the counters and the `AtomicLong`-backed gauge, and passes them down to `NativeFileWatcher`.

### 2.1 Constructor

```java
public FileSystemScannerAdapter(String agentId,
                                String folderPath,
                                Duration delayBetweenReads,
                                FileMetadataStore fileMetadataStore,
                                MeterRegistry meterRegistry,
                                Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
    // Create metrics tagged with agentId and folder
    this.filesDiscoveredCounter = meterRegistry.counter(
            "ai_workflow.scanner.files_discovered", "agentId", agentId, "folder", folderPath);
    this.filesUnchangedCounter = meterRegistry.counter(
            "ai_workflow.scanner.files_unchanged", "agentId", agentId, "folder", folderPath);
    
    // AtomicLong-backed gauge (Micrometer 1.16 has no AtomicLongGauge)
    this.fileCount = new AtomicLong(0);
    Gauge.builder("ai_workflow.scanner.file_count", fileCount, AtomicLong::get)
            .tag("agentId", agentId)
            .tag("folder", folderPath)
            .register(meterRegistry);

    // Pass counters, gauge, and event callback to NativeFileWatcher
    this.nativeFileWatcher = new NativeFileWatcher(
            Path.of(folderPath), delayBetweenReads, fileMetadataStore,
            filesDiscoveredCounter, filesUnchangedCounter, fileCount,
            history -> {
                if (metricsEventPublisher != null) {
                    metricsEventPublisher.accept(
                        ScannerMetricsChangedEvent.fileCountUpdated(agentId));
                }
            });
}
```

### 2.2 Gauge Updates

The gauge is updated in **three places**:

1. **After initial scan** — in `initSource()`, after `nativeFileWatcher.start()` completes the initial full scan
2. **After reset scan** — in `scanAllFiles()`, after walking the directory tree
3. **After every file event** — in `NativeFileWatcher.emitFile()`, via `fileCount.set(countFiles())`

```java
// In initSource() — after initial scan
long currentCount = Files.walk(folder)
        .filter(Files::isRegularFile)
        .count();
fileCount.set(currentCount);

// In scanAllFiles() — after full walk
fileCount.set(countFiles());

// In NativeFileWatcher.emitFile() — after each file event
fileCount.set(countFiles());
```

### 2.3 Counter Increments

Counters are incremented in **two places**:

1. **In `FileSystemScannerAdapter.scanAllFiles()`** — during reset-to-full-scan operations
2. **In `NativeFileWatcher.emitFile()`** — during watch event processing (CREATE/MODIFY)

```java
// In scanAllFiles() — for files found during full scan
if (!history.hashMatches()) {
    filesDiscoveredCounter.increment();
    log.debug("Full scan - emitting new file: {}", relativePath);
    fileMetadataStore.save(metadata);
    nativeFileWatcher.emit(history);
} else {
    filesUnchangedCounter.increment();
    log.debug("Full scan - skipping existing file: {}", relativePath);
}

// In emitFile() — for files found during watch events
if (!history.hashMatches()) {
    filesDiscoveredCounter.increment();
    log.debug("New or changed file: {}", relativePath);
    fileMetadataStore.save(metadata);
    sink.tryEmitNext(history);
} else {
    filesUnchangedCounter.increment();
    log.debug("Unchanged file (skipped): {}", relativePath);
}
```

---

## 3. Instrumentation: NativeFileWatcher

`NativeFileWatcher` is instantiated with `new` (not a Spring bean), so metrics are passed in as constructor parameters rather than injected.

### 3.1 Constructor

```java
public NativeFileWatcher(Path directory,
        Duration pollInterval,
        FileMetadataStore fileMetadataStore,
        Counter filesDiscoveredCounter,
        Counter filesUnchangedCounter,
        AtomicLong fileCount,
        Consumer<FileHistory> emitCallback) {
    // ...
    this.filesDiscoveredCounter = filesDiscoveredCounter;
    this.filesUnchangedCounter = filesUnchangedCounter;
    this.fileCount = fileCount;
    this.emitCallback = emitCallback;  // triggers Spring event
}
```

### 3.2 File Event Processing

When a CREATE or MODIFY event fires:

1. The file is read and hashed
2. `FileComparator` checks if the hash differs from the stored metadata
3. The appropriate counter is incremented
4. The gauge is updated via `countFiles()`
5. The `emitCallback` fires, triggering a Spring event

```java
private void emitFile(Path path) {
    try {
        String content = Files.readString(path);
        String hash = FileHash.hash(content);
        String relativePath = directory.relativize(path).toString().replace("\\", "/");
        FileMetadata metadata = new FileMetadata(relativePath, content, hash);
        FileHistory history = fileComparator.matches(metadata);

        if (!history.hashMatches()) {
            filesDiscoveredCounter.increment();
            fileMetadataStore.save(metadata);
            sink.tryEmitNext(history);
        } else {
            filesUnchangedCounter.increment();
        }
    } catch (IOException e) {
        log.warn("Failed to read file for event: {}", path, e);
    }

    // Update gauge after any file event
    fileCount.set(countFiles());

    // Trigger event for real-time UI updates
    if (emitCallback != null) {
        emitCallback.accept(history);
    }
}
```

### 3.3 countFiles() Helper

This method walks the directory tree to count regular files. It's called after every file event to keep the gauge accurate:

```java
private long countFiles() {
    try {
        return Files.walk(directory)
                .filter(Files::isRegularFile)
                .count();
    } catch (IOException e) {
        return 0L;
    }
}
```

> **Note**: For large directories, this could be expensive. Currently acceptable because events are rate-limited by the 100ms sleep in `processEvent()` and the 5-second poll interval.

---

## 4. Event-Driven UI Updates

The UI updates in real time when files are created or modified. This is **not** a polling mechanism — it's an event-driven push from the watch service thread to the Vaadin UI thread.

### 4.1 Event Flow

```
WatchService thread (background)
  └─ NativeFileWatcher.emitFile()
       ├─ filesDiscoveredCounter.increment()
       ├─ fileCount.set(countFiles())
       └─ emitCallback.accept(history)
            └─ metricsEventPublisher.accept(event)
                 └─ eventPublisher.publishEvent(event)
                      └─ ScannerMetricsPushService.onScannerMetricsChanged()
                           └─ metricsService.pushToUI(event)
                                └─ callback.accept(event)
                                     └─ ui.access(() → grid.refreshAll())
```

### 4.2 ScannerMetricsChangedEvent

```java
public class ScannerMetricsChangedEvent {
    private final String agentId;
    private final String type;  // "discovered", "unchanged", "file_count"

    public static ScannerMetricsChangedEvent fileDiscovered(String agentId) { ... }
    public static ScannerMetricsChangedEvent fileUnchanged(String agentId) { ... }
    public static ScannerMetricsChangedEvent fileCountUpdated(String agentId) { ... }
}
```

### 4.3 ScannerMetricsEventPublisher

Spring bean that wraps `ApplicationEventPublisher`:

```java
@Component
public class ScannerMetricsEventPublisher implements Consumer<ScannerMetricsChangedEvent> {
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void accept(ScannerMetricsChangedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
```

### 4.4 ScannerMetricsPushService

`@EventListener` receives the event and delegates to the registered UI callback:

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

### 4.5 ScannerMetricsService

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

### 4.6 ScannerListView

The view registers a callback on attach that wraps the refresh in `UI.access()`:

```java
public class ScannerListView extends VerticalLayout {
    @Autowired
    public ScannerListView(ScannerService scannerService, ScannerMetricsService metricsService) {
        this.metricsService = metricsService;
        initLayout();
    }

    private void initLayout() {
        // ...
        
        // Register refresh callback so background threads can push real-time updates
        addAttachListener(event -> {
            com.vaadin.flow.component.UI ui = event.getUI();
            metricsService.registerRefreshCallback(e -> {
                log.debug("UI refresh callback triggered: agent={}, type={}",
                        e.getAgentId(), e.getType());
                ui.access(() -> grid.getDataProvider().refreshAll());
            });
        });

        addDetachListener(event -> {
            // Clear the callback to avoid stale references
            metricsService.registerRefreshCallback(e -> {});
        });
    }
}
```

### 4.7 Why Not @EventListener on the View

> **Important**: `@EventListener` only works on Spring `@Component`/`@Service` beans. Vaadin views are not Spring beans, so the event listener cannot be placed directly on `ScannerListView`. The `ScannerMetricsPushService` (`@Service`) acts as the bridge.

---

## 5. UI: ScannerListView Files Column

The "Files" column reads metrics from `ScannerMetricsService` and displays them in the grid.

### 5.1 Column Definition

```java
grid.addColumn(info -> {
    try {
        ScannerMetricsSnapshot m = metricsService.getMetrics(info.agentId());
        return m.fileCount() + " files";
    } catch (Exception e) {
        return "—";
    }
})
.setHeader("Files")
.setAutoWidth(true);
```

### 5.2 ScannerMetricsSnapshot DTO

```java
public record ScannerMetricsSnapshot(
    String agentId,
    long fileCount,        // current files in target directory
    long totalDiscovered,  // files found since scanner started
    long unchanged         // files matching previous hash (skipped)
) {}
```

### 5.3 Metrics Reading

```java
public ScannerMetricsSnapshot getMetrics(String agentId) {
    double fileCount = getGaugeValue("ai_workflow.scanner.file_count", "agentId", agentId);
    double discovered = getCounterValue("ai_workflow.scanner.files_discovered", "agentId", agentId);
    double unchanged = getCounterValue("ai_workflow.scanner.files_unchanged", "agentId", agentId);

    return new ScannerMetricsSnapshot(agentId, (long) fileCount, (long) discovered, (long) unchanged);
}

private double getGaugeValue(String name, String tagKey, String tagValue) {
    Gauge gauge = registry.get(name).tag(tagKey, tagValue).gauge();
    return gauge != null ? gauge.value() : 0.0;
}

private double getCounterValue(String name, String tagKey, String tagValue) {
    Counter counter = registry.get(name).tag(tagKey, tagValue).counter();
    return counter != null ? counter.count() : 0.0;
}
```

---

## 6. Configuration

### 6.1 pom.xml

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

This pulls in Micrometer Core and auto-configures `SimpleMeterRegistry` as a fallback.

### 6.2 application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics
  metrics:
    tags:
      app: ai-workflow
```

---

## 7. Testing

### 7.1 Test Classes

| Test | Type | What it verifies |
|------|------|------------------|
| `FileSystemScannerAdapterMetricsTest` | Unit (Mockito + SimpleMeterRegistry) | Counters increment on discovery, gauge updates after scan and event |
| `NativeFileWatcherMetricsTest` | Unit | Counters increment during initial scan, file count gauge updates |
| `ScannerMetricsServiceTest` | Unit | Reads correct values from MeterRegistry for given agentId, sums across agents |

### 7.2 Example: FileSystemScannerAdapterMetricsTest

```java
@Test
void givenFileInDirectory_WhenAdapterCreated_ThenDiscoveredCounterIncrements() throws Exception {
    // Create a test file
    Files.writeString(inputDir.resolve("test-metrics.txt"), "test content");

    adapter = new FileSystemScannerAdapter(
            "test-agent", inputDir.toString(), Duration.ofSeconds(1),
            fileMetadataDatabase, meterRegistry);

    Thread.sleep(1000);  // Wait for initial scan

    double discovered = meterRegistry.find("ai_workflow.scanner.files_discovered")
            .tag("agentId", "test-agent")
            .counters()
            .stream()
            .mapToDouble(Counter::count)
            .findFirst()
            .orElse(0.0);

    assertThat(discovered).isEqualTo(1.0);
}
```

### 7.3 Test Results

All 272 tests pass, 0 failures, 2 skipped.

---

## 8. Performance Considerations

### 8.1 Gauge Update Frequency

The gauge is updated on **every file event** (CREATE/MODIFY), not just during full scans. This means `countFiles()` is called frequently, which walks the directory tree.

**Current mitigation**:
- Watch events are rate-limited by the 100ms sleep in `processEvent()`
- Default poll interval is 5 seconds
- For most use cases (tens to hundreds of files), this is acceptable

**For large directories** (thousands of files), consider:
- Sampling: update gauge every N events instead of every event
- Caching: maintain an in-memory file count that increments/decrements instead of walking the tree
- Lazy: only update gauge when the UI requests it (but this defeats the real-time goal)

### 8.2 Memory

- Each scanner has 2 counters + 1 gauge + 1 AtomicLong in memory — minimal overhead
- Counters are monotonic and never reset (they accumulate across restarts)
- The gauge value is a single `long` per scanner

### 8.3 Thread Safety

- `AtomicLong` is thread-safe for the gauge value
- `Counter.increment()` is thread-safe
- The `emitCallback` is called from the watch service thread; `UI.access()` ensures UI updates happen on the UI thread

---

## 9. Related Documents

| Document | Type | Description |
|----------|------|-------------|
| dpr-testing-strategy | DPR | Testing pyramid and patterns used in scanner tests |

---

*Scanner observability is implemented with Micrometer counters and gauges, Spring events for real-time UI updates, and direct service injection into the Vaadin view. No REST endpoints, no database persistence — just counters, gauges, and event-driven updates.*
