# Plan: Scanner Metrics with Micrometer

**Status**: Phase 2 — ✅ Implemented & Tested  
**Related Plans**: [`observability-plan.md`](observability-plan.md), [`agent-scanners.md`](agent-scanners.md)  
**Created**: 2026-04-28  
**Last Updated**: 2026-04-28  

---

## 1. Overview

Add real-time scanner metrics using Micrometer (Spring Boot Actuator). Metrics are exposed directly to the Vaadin UI via service injection — no REST endpoints at this stage.

**Primary goal**: Show current file count per scanner in the Scanner ListView, with **real-time updates** when files are created/modified.

### Metrics to Track

| Metric | Type | Name | Tags | Purpose |
|--------|------|------|------|---------|
| **Current file count** | Gauge | `ai_workflow.scanner.file_count` | `agentId`, `folder` | Number of files currently in the target directory |
| **Files discovered** | Counter | `ai_workflow.scanner.files_discovered` | `agentId`, `folder` | Total files found (initial scan + incremental) |
| **Files unchanged** | Counter | `ai_workflow.scanner.files_unchanged` | `agentId`, `folder` | Files whose hash matches previous record (skipped) |

---

## 2. Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Micrometer over database** | Low overhead, zero persistence dependency, real-time values. DB is H2 in-memory (create-drop) so metrics would be lost on restart anyway. |
| **Direct injection over REST** | Vaadin runs server-side — no HTTP round-trip needed. Follows existing pattern (`ObservabilityView` injects `AgentStatusUsecase` directly). |
| **Pass `MeterRegistry` through `ScannerRegistry`** | `FileSystemScannerAdapter` is instantiated with `new` (not a Spring bean). `ScannerRegistry` is a Spring bean and can inject `MeterRegistry`, then pass it down. |
| **No `MeterBinder` for now** | `FileSystemScannerAdapter` is a thin adapter class. Direct `registry.counter(...)` calls in the scan method are simpler than creating a separate `MeterBinder` component. |
| **Event-driven UI updates** | Watch service fires events → Spring event → `UI.access()` → `grid.refreshAll()`. No polling needed for file count changes. |
| **`AtomicLong`-backed gauge** | Micrometer 1.16 has no `AtomicLongGauge` — use `Gauge.builder(name, AtomicLong, AtomicLong::get)`. |

---

## 3. Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ UI Layer (Vaadin)                                                     │
│                                                                        │
│  ┌──────────────────────────┐    ┌─────────────────────────────────┐  │
│  │  ScannerListView         │    │  ScannerMetricsService          │  │
│  │  Route: /scanners        │◄───│  - getMetrics(agentId)          │  │
│  │  Grid: "Files" column    │    │  - registerRefreshCallback()    │  │
│  │  Real-time: UI.refresh() │    │  - pushToUI(event)              │  │
│  └──────────────────────────┘    └─────────────────────────────────┘  │
│                                       ▲                                │
│                                       │ injects                        │
│                                       │                                │
├───────────────────────────────────────┼────────────────────────────────┤
│ Domain / Manager Layer                                      │          │
│                                                                        │
│  ┌──────────────────────────┐    ┌─────────────────────────────────┐  │
│  │  ScannerRegistry         │    │  FileSystemScannerAdapter       │  │
│  │  - injects MeterRegistry │    │  - receives MeterRegistry +     │  │
│  │  - injects event pub     │    │    Consumer<event>              │  │
│  │  - passes both to adapter│    │  - owns counters + AtomicLong   │  │
│  │                          │    │    - passes to NativeFileWatcher│  │
│  └──────────────────────────┘    └─────────────────────────────────┘  │
│                                       ▲                                │
│                                       │ passes counters + callback     │
│                                       │                                │
│  ┌──────────────────────────┐    ┌─────────────────────────────────┐  │
│  │  NativeFileWatcher       │    │  ScannerMetricsPushService      │  │
│  │  - emitFile()            │    │  - @EventListener               │  │
│  │    ├─ counter.increment()│    │    receives event               │  │
│  │    ├─ fileCount.set()    │    │    calls metricsService.pushToUI│  │
│  │    └─ emitCallback()     │    └─────────────────────────────────┘  │
│  └──────────────────────────┘                                         │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│ Spring Boot Actuator                                                  │
│                                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  CompositeMeterRegistry (auto-configured)                       │  │
│  │  - SimpleMeterRegistry (fallback, in-memory)                    │  │
│  │  - Exposes /actuator/metrics endpoint                           │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  ScannerMetricsEventPublisher                                   │  │
│  │  - Consumer<ScannerMetricsChangedEvent>                         │  │
│  │  - wraps ApplicationEventPublisher                              │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Real-Time Event Flow

```
WatchService thread (background)
  └─ NativeFileWatcher.emitFile()
       ├─ filesDiscoveredCounter.increment()
       ├─ fileCount.set(countFiles())          ← gauge updated
       └─ emitCallback.accept(history)
            └─ metricsEventPublisher.accept(event)
                 └─ eventPublisher.publishEvent(event)
                      └─ ScannerMetricsPushService.onScannerMetricsChanged()  ← @EventListener (Spring bean)
                           └─ metricsService.pushToUI(event)
                                └─ callback.accept(event)  ← registered by ScannerListView
                                     └─ ui.access(() → grid.refreshAll())  ← Vaadin UI thread
```

---

## 4. Deliverables

### 4.1 Dependency ✅

Added to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 4.2 Configuration ✅

Added to `application.yml`:

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

### 4.3 Instrument `FileSystemScannerAdapter` ✅

Added `MeterRegistry` + `Consumer<ScannerMetricsChangedEvent>` to constructor. Counters and `AtomicLong`-backed gauge created at construction. Both `scanAllFiles()` and `emitFile()` increment counters and update the gauge.

**Constructor signature:**
```java
public FileSystemScannerAdapter(String agentId,
                                String folderPath,
                                Duration delayBetweenReads,
                                FileMetadataStore fileMetadataStore,
                                MeterRegistry meterRegistry,
                                Consumer<ScannerMetricsChangedEvent> metricsEventPublisher)
```

### 4.4 Pass `MeterRegistry` Through `ScannerRegistry` ✅

```java
public ScannerRegistry(
        ApplicationContext applicationContext,
        FileMetadataDatabase fileMetadataDatabase,
        MeterRegistry meterRegistry,
        Consumer<ScannerMetricsChangedEvent> metricsEventPublisher)
```

### 4.5 `ScannerMetricsService` ✅

Reads metrics from `MeterRegistry` for the UI. Provides `registerRefreshCallback()` for real-time event push, and `pushToUI()` called by `ScannerMetricsPushService`.

### 4.6 `ScannerMetricsSnapshot` DTO ✅

```java
public record ScannerMetricsSnapshot(
    String agentId,
    long fileCount,
    long totalDiscovered,
    long unchanged
)
```

### 4.7 Update `ScannerListView` ✅

Added **Files** column + real-time event listener:

```java
// Files column
grid.addColumn(info -> {
    ScannerMetricsSnapshot m = metricsService.getMetrics(info.agentId());
    return m.fileCount() + " files";
}).setHeader("Files");

// Real-time: register callback on attach
addAttachListener(event -> {
    com.vaadin.flow.component.UI ui = event.getUI();
    metricsService.registerRefreshCallback(e -> {
        ui.access(() -> grid.getDataProvider().refreshAll());
    });
});
```

---

## 5. Tag Strategy ✅

**Implemented: both `agentId` and `folder` tags on all metrics.**

The plan originally noted that `agentId` was not available in the adapter. This was corrected — `ScannerRegistry.createForAgent()` knows the `agentId`, so it's threaded through to the adapter and used as the tag value.

| Tag | Cardinality | Notes |
|-----|-------------|-------|
| `agentId` | Low (one per agent) | Used for UI lookups — **required** |
| `folder` | Low (one per agent) | Path-based tag for observability |
| `app` | 1 (common tag) | Applied globally via config |

---

## 6. Implementation Order ✅

```
1. Add spring-boot-starter-actuator dependency to pom.xml                          ✅
2. Add management.metrics configuration to application.yml                         ✅
3. Create ScannerMetricsSnapshot DTO                                               ✅
4. Create ScannerMetricsService                                                    ✅
5. Add MeterRegistry + Consumer parameter to FileSystemScannerAdapter              ✅
6. Instrument FileSystemScannerAdapter.scanAllFiles() with counters + gauge        ✅
7. Update NativeFileWatcher: counters + gauge + event callback                     ✅
8. Add MeterRegistry + Consumer to ScannerRegistry constructor                     ✅
9. Wire MeterRegistry + Consumer through ScannerRegistry.createForAgent()          ✅
10. Update ScannerListView: add Files column                                       ✅
11. Create ScannerMetricsChangedEvent (Spring event)                               ✅
12. Create ScannerMetricsEventPublisher (wraps ApplicationEventPublisher)          ✅
13. Create ScannerMetricsPushService (@EventListener → UI access)                  ✅
14. Update existing tests (5 files, all constructor changes)                       ✅
15. Create new tests (4 files)                                                     ✅
```

---

## 7. Testing ✅

| Test | Type | What it verifies |
|------|------|------------------|
| `FileSystemScannerAdapterMetricsTest` | Unit (Mockito + SimpleMeterRegistry) | Counters increment on discovery, gauge updates after scan and event |
| `NativeFileWatcherMetricsTest` | Unit | Counters increment during initial scan, file count gauge updates |
| `ScannerMetricsServiceTest` | Unit | Reads correct values from MeterRegistry for given agentId, sums across agents |
| All existing tests | Unit | 272 tests pass, 0 failures, 2 skipped |

---

## 8. Files Created / Modified

### New Files

| # | Path | Description |
|---|------|-------------|
| 1 | `rest/dto/ScannerMetricsSnapshot.java` | Metrics DTO for scanner |
| 2 | `ui/service/ScannerMetricsService.java` | Reads metrics from MeterRegistry + registers refresh callback |
| 3 | `ui/service/ScannerMetricsPushService.java` | `@EventListener` — bridges background thread to UI thread |
| 4 | `ui/events/ScannerMetricsChangedEvent.java` | Spring event for metrics changes |
| 5 | `ui/events/ScannerMetricsEventPublisher.java` | `Consumer<ScannerMetricsChangedEvent>` wrapper |
| 6 | `files/FileSystemScannerAdapterMetricsTest.java` | 6 tests for adapter metrics |
| 7 | `files/NativeFileWatcherMetricsTest.java` | 4 tests for watcher metrics |
| 8 | `ui/service/ScannerMetricsServiceTest.java` | 8 tests for metrics service |

### Modified Files

| # | Path | Description |
|---|------|-------------|
| 1 | `pom.xml` | Added `spring-boot-starter-actuator` dependency |
| 2 | `application.yml` | Added `management.metrics` configuration |
| 3 | `files/FileSystemScannerAdapter.java` | Added `MeterRegistry` + `Consumer<event>` params, counters, gauge |
| 4 | `files/NativeFileWatcher.java` | Added `Consumer<FileHistory>` callback, counter/gauge updates, `countFiles()` helper |
| 5 | `app/pipeline/management/ScannerRegistry.java` | Added `MeterRegistry` + `Consumer<event>` params |
| 6 | `ui/views/ScannerListView.java` | Added Files column + attach listener for real-time updates |
| 7 | `files/FileSystemScannerAdapterTest.java` | Updated all constructor calls |
| 8 | `files/FileSystemSimplePollerFluxAdapterTest.java` | Updated NativeFileWatcher constructor |
| 9 | `app/pipeline/AgentPipelineTest.java` | Updated all 3 adapter constructor calls |
| 10 | `app/pipeline/management/ScannerRegistryTest.java` | Updated constructor call |
| 11 | `app/pipeline/management/ScannerRegistryIntegrationTest.java` | Updated constructor call |

---

## 9. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| `MeterRegistry` injection fails | Application won't start | `spring-boot-starter-actuator` auto-configures `SimpleMeterRegistry` as fallback |
| Gauge reading files during every snapshot | Performance hit on large directories | Gauge is updated only on file events (not polling), ~100ms delay for file write completion |
| Existing tests break due to new constructor params | Build fails | Updated all test constructors — 5 test files modified |
| `UI.getCurrent()` returns null on background thread | UI updates silently dropped | View registers callback on attach via `addAttachListener()`, stores `UI` reference, calls `ui.access()` |
| `@EventListener` not called on Vaadin views | No real-time updates | Moved listener to Spring `@Service` bean (`ScannerMetricsPushService`) instead of Vaadin view |
| High-cardinality tags | Memory leak in Micrometer | Only tag with `agentId` (low cardinality). Avoid tagging with file names or hashes |

---

## 10. Future Enhancements

| Enhancement | Description |
|-------------|-------------|
| **Database persistence** | Snapshot metrics every N minutes for historical trends (follow LLMStatus pattern) |
| **Prometheus export** | Add `micrometer-registry-prometheus` for Grafana/Prometheus dashboards |
| **Scan duration timer** | Record time taken for each full scan via `Timer` |
| **Error counter** | Count files that fail to read during scan |
| **Alerting** | Trigger alert if `file_count` drops to 0 for >5 minutes |

---

## 11. Divergences from Plan

The following items diverged from the original plan:

### 11.1 `AtomicLongGauge` → `AtomicLong`-backed `Gauge`

**Plan:** Use `AtomicLongGauge.builder(...)` from `io.micrometer.core.instrument`.  
**Reality:** `AtomicLongGauge` does not exist in Micrometer 1.16.3 (the version in use).  
**Fix:** Use `Gauge.builder(name, AtomicLong, AtomicLong::get).register(meterRegistry)`.

### 11.2 `MeterListener` → Event-driven callback

**Plan:** Use Micrometer's `MeterListener` interface to detect counter/gauge changes.  
**Reality:** `MeterListener` was removed in Micrometer 1.16.  
**Fix:** Introduced `ScannerMetricsChangedEvent` (Spring `ApplicationEvent`) published from `NativeFileWatcher` via a `Consumer<FileHistory>` callback wired through `FileSystemScannerAdapter` → `ScannerRegistry`.

### 11.3 `agentId` tag threaded through

**Plan:** Tag metrics with `folder` only, map back to `agentId` in `ScannerMetricsService` by iterating `registry.getMeters()`.  
**Reality:** The plan's own "Alternative (cleaner)" was chosen: pass `agentId` through to `FileSystemScannerAdapter` and tag with both `agentId` and `folder`. This makes lookups direct and correct.

### 11.4 Real-time UI updates added

**Plan:** UI reads metrics on its 30-second auto-refresh cycle only.  
**Reality:** Added event-driven real-time updates. When a file is created/modified, the watch service fires a Spring event → `ScannerMetricsPushService` receives it → calls the view's registered callback → `ui.access(() → grid.refreshAll())` updates the Files column immediately.

### 11.5 `@EventListener` moved to Spring service

**Plan:** `ScannerMetricsService` or `ScannerListView` listens for events directly.  
**Reality:** `@EventListener` only works on Spring `@Component`/`@Service` beans, not Vaadin views. Created `ScannerMetricsPushService` (`@Service`) as the `@EventListener`, which delegates to `ScannerMetricsService` → registered callback on the UI.

### 11.6 Gauge updated on file events (not just scans)

**Plan:** Gauge updated only after `scanAllFiles()` completes (initial scan + manual refresh).  
**Reality:** Gauge updated on **every** `emitFile()` call (CREATE/MODIFY events) and after `scanAllFiles()`. This ensures the file count stays accurate in real-time, not just after a full rescan.

### 11.7 `countFiles()` helper method

Added `countFiles()` to `NativeFileWatcher` that walks the directory tree and counts regular files. Called after every file event and after full scans to keep the gauge accurate.

---

*Implemented and tested. 272 tests pass, 0 failures. All metrics tracked, UI updates in real-time.*
