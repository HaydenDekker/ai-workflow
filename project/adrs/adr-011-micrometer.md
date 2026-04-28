# ADR-011: Metrics with Micrometer

## Context

The application needs observability into component behavior — counters for discrete events, gauges for current state, and timers for duration tracking. The application runs on Spring Boot, which has built-in support for a metrics library.

We needed to decide:
- Which metrics library to use
- How to expose metrics to the Vaadin UI without HTTP round-trips
- How to instrument components that are created with `new` (not Spring beans)
- Whether to persist metrics or keep them in-memory

## Decision

We use **Micrometer** (via `spring-boot-starter-actuator`) for all metrics instrumentation.

### Key Design Decisions

1. **In-memory metrics via `SimpleMeterRegistry`** — Spring Boot auto-configures this as the default. Metrics are lost on restart, which is acceptable because the application is stateless and metrics are for real-time monitoring, not historical analysis.

2. **No REST endpoints for metrics** — The Vaadin UI runs server-side and can inject `MeterRegistry` directly. No HTTP round-trip needed.

3. **`MeterRegistry` injected into Spring beans, passed to non-`@Component` classes** — Services (`@Service`) and registries (`@Component`) receive `MeterRegistry` via constructor injection. Classes created with `new` (like adapters, watchers, handlers) receive metric handles (counters, gauges) as constructor parameters.

4. **Low-cardinality tags** — Only tag with dimensions that have few distinct values (e.g., `agentId`, `folder`, `app`). Never tag with file names, hashes, or request IDs.

5. **Gauges use `AtomicLong` or `AtomicReference`** — Micrometer 1.16 does not provide `AtomicLongGauge`. Use `Gauge.builder(name, AtomicLong, AtomicLong::get).register(meterRegistry)` instead.

### Metrics Types Used

| Type | When to Use | Example |
|------|-------------|---------|
| **Counter** | Discrete events that only increase (files discovered, errors, requests) | `ai_workflow.scanner.files_discovered` |
| **Gauge** | Current state that goes up and down (file count, queue depth, memory usage) | `ai_workflow.scanner.file_count` |
| **Timer** | Duration of operations (scan time, HTTP call latency) — future use | — |

### Tag Convention

All metrics are tagged with:
- **`app`** — common tag set globally via `application.yml` (`metrics.tags.app`)
- **`agentId`** — the owning agent's ID (low cardinality: one per agent)
- **`folder`** — the target directory path (low cardinality: one per scanner)

No other tags are added. File names, hashes, timestamps, or request IDs are never used as tags.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│ Application Layer                                                   │
│                                                                     │
│  ┌──────────────────────┐    ┌─────────────────────────────────┐   │
│  │  ScannerMetricsService│    │  ScannerListView (Vaadin)      │   │
│  │  (@Service)          │    │  - injects MeterRegistry        │   │
│  │  - reads metrics     │    │  - injects ScannerMetricsService│   │
│  │  - pushToUI()        │    │  - grid refresh via UI.access() │   │
│  └──────────────────────┘    └─────────────────────────────────┘   │
│           ▲                                  ▲                      │
│           │ injects                          │ injects              │
│           │                                  │                      │
│  ┌────────┴──────────────────────────────────┴─────────────────┐   │
│  │  Spring Boot Actuator                                        │   │
│  │  - SimpleMeterRegistry (auto-configured)                    │   │
│  │  - /actuator/metrics endpoint                                │   │
│  │  - CompositeMeterRegistry (can add Prometheus, etc.)         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│           ▲                                                          │
│           │ injects                                                  │
│           │                                                          │
│  ┌────────┴────────────────────────────────────────────────────┐    │
│  │  ScannerRegistry (@Component)                                │    │
│  │  - injects MeterRegistry                                     │    │
│  │  - passes metric handles to non-@Component classes           │    │
│  └──────────────────────────────────────────────────────────────┘    │
│           ▲                                                          │
│           │ passes counters/gauges                                   │
│           │                                                          │
│  ┌────────┴────────────────────────────────────────────────────┐    │
│  │  FileSystemScannerAdapter (new, not a Spring bean)           │    │
│  │  - receives Counter, AtomicLong, Consumer                    │    │
│  │  - calls counter.increment() on events                       │    │
│  │  - calls gauge.setValue() on state changes                   │    │
│  └──────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### Pattern: Injecting Metrics into Non-Spring Beans

When a class is created with `new` (not managed by Spring), you cannot inject `MeterRegistry` via `@Autowired`. The pattern is:

1. **Spring bean receives `MeterRegistry`** via constructor injection
2. **Spring bean creates metric handles** using `meterRegistry.counter(...)`, `Gauge.builder(...).register(meterRegistry)`, etc.
3. **Metric handles are passed to the non-Spring class** as constructor parameters

```java
// Spring bean — receives MeterRegistry
@Component
public class ScannerRegistry {
    private final MeterRegistry meterRegistry;
    private final Consumer<ScannerMetricsChangedEvent> metricsEventPublisher;

    public ScannerRegistry(MeterRegistry meterRegistry,
                           Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
        this.meterRegistry = meterRegistry;
        this.metricsEventPublisher = metricsEventPublisher;
    }

    public ScannerInfo createForAgent(String agentId, String targetDirectory) {
        // Create metric handles here
        Counter discovered = meterRegistry.counter(
            "ai_workflow.scanner.files_discovered",
            "agentId", agentId, "folder", targetDirectory);

        AtomicLong fileCount = new AtomicLong(0);
        Gauge.builder("ai_workflow.scanner.file_count", fileCount, AtomicLong::get)
            .tag("agentId", agentId)
            .tag("folder", targetDirectory)
            .register(meterRegistry);

        // Pass handles to non-Spring class
        FileSystemScannerAdapter adapter = new FileSystemScannerAdapter(
            agentId, targetDirectory, discovered, fileCount, metricsEventPublisher);

        return new ScannerInfo(...);
    }
}

// Non-Spring class — receives metric handles
public class FileSystemScannerAdapter {
    private final Counter filesDiscoveredCounter;
    private final AtomicLong fileCount;

    public FileSystemScannerAdapter(String agentId, String folderPath,
                                    Counter filesDiscoveredCounter,
                                    AtomicLong fileCount,
                                    Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
        this.filesDiscoveredCounter = filesDiscoveredCounter;
        this.fileCount = fileCount;
        // ...
    }

    public void onFileDiscovered() {
        filesDiscoveredCounter.increment();  // no MeterRegistry reference
    }
}
```

### Pattern: Spring Events for Real-Time UI Updates

When metrics change and the UI needs to update in real time, use Spring events:

1. **Event class** — simple POJO with relevant data
2. **Event publisher** — `Consumer<T>` that wraps `ApplicationEventPublisher`
3. **Event listener** — `@EventListener` on a Spring `@Service` (not on Vaadin views)
4. **UI callback** — view registers a refresh callback on attach; service calls it via `UI.access()`

```java
// 1. Event class
public class ScannerMetricsChangedEvent {
    private final String agentId;
    private final String type;
    // ...
}

// 2. Event publisher
@Component
public class ScannerMetricsEventPublisher implements Consumer<ScannerMetricsChangedEvent> {
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void accept(ScannerMetricsChangedEvent event) {
        eventPublisher.publishEvent(event);
    }
}

// 3. Event listener on a Spring service
@Service
public class ScannerMetricsPushService {
    private final ScannerMetricsService metricsService;

    @EventListener
    public void onScannerMetricsChanged(ScannerMetricsChangedEvent event) {
        metricsService.pushToUI(event);
    }
}

// 4. UI callback registered by the view
public class ScannerListView extends VerticalLayout {
    @Autowired
    public ScannerListView(ScannerMetricsService metricsService) {
        metricsService.setUiSupplier(() -> Optional.of(UI.getCurrent()));
        initLayout();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        metricsService.setUiSupplier(() -> Optional.empty());
        super.onDetach(detachEvent);
    }
}

@Service
public class ScannerMetricsService {
    private final AtomicReference<Supplier<Optional<UI>>> uiSupplierRef
        = new AtomicReference<>(() -> Optional.empty());

    public void setUiSupplier(Supplier<Optional<UI>> supplier) {
        this.uiSupplierRef.set(supplier);
    }

    void pushToUI(ScannerMetricsChangedEvent event) {
        Supplier<Optional<UI>> supplier = uiSupplierRef.get();
        if (supplier != null) {
            Optional<UI> uiOpt = supplier.get();
            uiOpt.ifPresent(ui -> ui.access(() -> {
                // refresh grid, update component, etc.
            }));
        }
    }
}
```

> **Important**: `@EventListener` only works on Spring `@Component`/`@Service` beans. Vaadin views are not Spring beans, so the event listener cannot be placed directly on a view. Use a Spring service as the bridge.

## Consequences

### Positive

1. **Zero HTTP overhead for UI updates** — Vaadin views read metrics directly from `MeterRegistry`; no REST endpoints or polling needed
2. **Minimal boilerplate for non-Spring classes** — counters and gauges are simple objects passed as constructor parameters; no dependency on Spring
3. **Real-time updates via events** — Spring events + `UI.access()` provide near-instant UI updates without polling
4. **Easy to extend** — add Prometheus, Datadog, or other backends by adding a dependency; the application code doesn't change
5. **Low cardinality enforced by convention** — tag strategy is documented and followed consistently

### Negative

1. **Metrics lost on restart** — `SimpleMeterRegistry` is in-memory. Historical analysis requires a persistent backend (Prometheus, database)
2. **Gauge updates on every event** — `countFiles()` walks the directory tree on each file event. Acceptable for small directories but could be optimized for large ones
3. **Thread boundary complexity** — metrics are updated on background threads (watch service), UI updates must use `UI.access()`. Easy to forget the thread boundary
4. **No built-in alerting** — Micrometer exposes metrics but does not trigger alerts. Alerting requires an external system (Prometheus Alertmanager, Grafana)

### Neutral

1. **Spring Boot dependency** — `spring-boot-starter-actuator` adds ~200KB to the JAR. Acceptable for a full-stack application
2. **MeterRegistry API changes** — Micrometer versions may change APIs (e.g., `AtomicLongGauge` removed in 1.16). Pin the version and test upgrades

## Alternatives Considered

### Alternative 1: Custom Metrics with Manual Tracking

Track counters and gauges using `Map<String, Long>` and `AtomicLong` fields directly in components.

**Rejected**: No standardized API, no `/actuator/metrics` endpoint, no Prometheus export, no tooling support. Micrometer provides all of this for free.

### Alternative 2: REST Endpoint for Metrics

Expose metrics via a REST endpoint (`@RestController`) and have the Vaadin UI poll them via HTTP calls.

**Rejected**: Adds HTTP overhead for every UI refresh. Vaadin runs server-side and can inject `MeterRegistry` directly — no need for HTTP round-trips.

### Alternative 3: Database Persistence for Metrics

Store metric snapshots in H2/SQLite every N minutes for historical trends.

**Rejected**: The database is in-memory (`create-drop`). Metrics would still be lost on restart. Persistence requires a separate backend (Prometheus, time-series database). Add later if needed.

### Alternative 4: Dropwizard Metrics

Use `dropwizard-metrics` instead of Micrometer.

**Rejected**: Micrometer is the Spring Boot standard, has better integration with Spring Boot Actuator, and supports multiple backends (Prometheus, Datadog, New Relic, etc.) via adapters.

## Configuration

### pom.xml

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### application.yml

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

### Adding a Backend (Future)

To add Prometheus support, add:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

No application code changes needed — Micrometer's `CompositeMeterRegistry` handles the routing.

## See Also

- [dpr-scanner-observability](../docs/dpr-scanner-observability.md) — How scanner metrics are implemented
- [Spring Boot Actuator documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#actuator)
- [Micrometer documentation](https://micrometer.io/docs)

---

**Author**: AI Workflow Team  
**Date**: 2026-04-28  
**Last Updated**: 2026-04-28
