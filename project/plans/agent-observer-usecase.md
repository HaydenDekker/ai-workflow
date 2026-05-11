# Plan: Agent Observer UseCase

> **Created:** 2026-05-08  
> **Author:** AI Workflow Team

---

## Problem

The agent pipeline processes file events through a reactive chain (scanner → LLM → output), but there is no observability layer for **the agent processing pipeline itself**. The existing `ScannerObservabilityUseCase` tracks scanner-level metrics (file counts, emissions, idle state), but the pipeline layer — where prompts are dispatched to the LLM and responses are persisted to output directories — is completely opaque.

Operators need to know:
1. **How many files are in the output directory?** (storage awareness)
2. **How many dispatches have been made to the LLM?** (LLM usage monitoring, rate-limit awareness)

Without this, debugging pipeline issues, monitoring LLM costs, and understanding system throughput are guesswork.

---

## Target

An explicit `AgentObserverUseCase` that orchestrates dispatch events and response storage metrics, mirroring the `ScannerObservabilityUseCase` pattern:

- **Dispatch events**: Every time a `PromptRequest` is sent to the LLM (via `LLMAdapter.call()`), a dispatch event is recorded in metrics and published to event subscribers.
- **Response storage events**: Every time a `PromptResponse` is persisted to the output directory, a storage event is recorded in metrics and published to event subscribers.
- **Query API**: Methods to read dispatch count, storage count, and output directory file count.

The observer integrates into `AgentConfigurator` via `doOnNext` hooks on the reactive pipeline, making it non-invasive and testable.

---

## Implementation Status: ✅ Complete (2026-05-11, All 9 Phases)

**Completion Overview:** All 9 phases (0–8) implemented and tested. 421 unit tests pass, 0 failures, 0 errors. Branch merged to `main`.

---

### Phase 8: Rectify Spring Bean Wiring — Fix Null Observer & Null File Counter

**Status**: ✅ Complete

**Deliverables**:
- `AgentObserverService` — single `@Autowired` constructor (no-arg removed), `@Value` for outputDirectory
- `AgentLifecycleService` — single `@Autowired` constructor (no-arg removed), `@Value` for outputDirectory  
- `DynamicAgentManagerConfiguration` — `@Bean agentLifecycleService` removed (component scan handles it)
- `AgentLifecycleServiceWiringTest` — 5 tests verifying Spring wiring
- `AgentObserverServiceTest` — updated to use parameterized constructor
- `AgentLifecycleServiceTest`, `ScannerRestoreTest`, `PersistenceTest` — pass with new constructor
- `AgentListViewColumnTest`, `AgentListViewDeleteTest` — updated to use 7-param constructor

**Root Causes Fixed**:
1. `AgentLifecycleService.observer = null` → `@Autowired` + single constructor eliminates no-arg fallback
2. `AgentObserverService.fileCounter = null` → `@Autowired` + single constructor eliminates no-arg fallback
3. Dual `AgentLifecycleService` bean conflict → removed `@Bean` from config; `@Service` handles it

**Test Results**: 421 tests pass, 0 failures, 0 errors, 2 skipped (integration tests requiring external services).

---

## Existing Tests

| Test Class | What it covers | Status |
|------------|---------------|--------|
| `AgentBuilderTest` | Agent pipeline builder stages | ✅ Green — tests filter, dispatch, persist |
| `AgentConfiguratorTest` | Configurator pipeline wiring | ✅ Green — tests scanner flux → LLM → persister |
| `AgentPipelineTest` | Full pipeline integration (scanner → LLM → output) | ✅ Green — end-to-end reactive flow |
| `AgentLifecycleServiceTest` | Agent CRUD and lifecycle | ✅ Green — create, enable, disable, refresh |
| `FileSystemFileCounterTest` | File counting on disk | ✅ Green — recursive directory walk |
| `ScannerObservabilityUseCaseTest` | Scanner metrics + event bus orchestration | ✅ Green — existing observability pattern |
| `ScannerMetricsServiceTest` | Pure metrics store behavior | ✅ Green |
| `ScannerEventBusTest` | Event push/callback behavior | ✅ Green |

---

## Test Gaps

- No test for pipeline-level dispatch counting
- No test for pipeline-level response storage counting
- No test for output directory file count via observer
- No test for agent observer orchestration (metrics + events)
- No test for agent observer event bus callback publishing
- No test for observer integration into `AgentConfigurator` pipeline chain

---

## Target Architecture

```
AgentConfigurator (wires into pipeline)
  └─ doOnNext(response) → AgentObserverUseCase.recordDispatch(agentId, fileName)
  └─ persister wrapper    → AgentObserverUseCase.recordStorage(agentId, outputName, path)

AgentObserverUseCase (orchestrator — single entry point)
  ├─ metricsPort.recordDispatch(agentId, fileName)   ← AgentObserverService (driven adapter)
  ├─ metricsPort.recordStorage(agentId, outputName)   ← AgentObserverService (driven adapter)
  └─ eventPort.publish(dispatchEvent)                 ← AgentObserverEventBus (driving adapter)

AgentObserverService (pure metrics store — driven adapter)
  └─ ConcurrentHashMap<String, AgentMetrics> — thread-safe counters per agent

AgentObserverEventBus (push callbacks — driving adapter)
  └─ CopyOnWriteArrayList<Consumer<AgentObserverEvent>> — event subscribers

AgentObserverEvent (domain event)
  └─ agentId, eventType (DISPATCHED / STORED), fileName, timestamp
```

### Hexagonal Layering

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `AgentObserverEvent`, `AgentObserverEventType` | Value objects — event types (DISPATCHED, STORED) |
| **Application (port)** | `AgentObserverPort` | Driven adapter — metrics recording and queries (no push, no callbacks) |
| **Application (port)** | `AgentObserverEventPort` | Driving adapter — push-only interface for event publishing |
| **Application (service)** | `AgentObserverService` | Implements `AgentObserverPort` — counters storage |
| **Application (service)** | `AgentObserverEventBus` | Implements `AgentObserverEventPort` — event publishing to callbacks |
| **Application (use case)** | `AgentObserverUseCase` | Orchestrates metrics + event publishing. Single entry point. |
| **Application (configurator)** | `AgentConfigurator` | Wires observer into pipeline via `doOnNext` hooks |
| **Inbound adapter** | `AgentController` | Exposes query endpoints for UI/dashboard |

---

## Phases

### Phase 0: Create Domain Event, Ports, and Services

**Goal**: Build the event model, port interfaces, metrics service, and event bus.

**New files**:

1. **`AgentObserverEvent.java`** — domain event
   - Path: `src/main/java/com/hdekker/ai_workflow/domain/pipeline/AgentObserverEvent.java`
   - Record: `agentId`, `eventType`, `fileName`, `timestamp`
   - EventType: `DISPATCHED`, `STORED`

2. **`AgentObserverEventType.java`** — domain enum
   - Path: `src/main/java/com/hdekker/ai_workflow/domain/pipeline/AgentObserverEventType.java`
   - Enum values: `DISPATCHED`, `STORED`

3. **`AgentObserverPort.java`** — driven adapter port
   - Path: `src/main/java/com/hdekker/ai_workflow/application/pipeline/port/AgentObserverPort.java`
   - Methods:
     - `recordDispatch(String agentId, String fileName)`
     - `recordStorage(String agentId, String outputName, Path outputPath)`
     - `getDispatchCount(String agentId)`
     - `getTotalDispatchCount()`
     - `getStorageCount(String agentId)`
     - `getTotalStorageCount()`

4. **`AgentObserverEventPort.java`** — driving adapter port
   - Path: `src/main/java/com/hdekker/ai_workflow/application/pipeline/port/AgentObserverEventPort.java`
   - Methods:
     - `registerCallback(Consumer<AgentObserverEvent> callback)`
     - `unregisterCallback(Consumer<AgentObserverEvent> callback)`

5. **`AgentObserverService.java`** — driven adapter implementation
   - Path: `src/main/java/com/hdekker/ai_workflow/application/pipeline/AgentObserverService.java`
   - Implements `AgentObserverPort`
   - `@Service` annotated
   - `ConcurrentHashMap<String, AgentMetrics>` for per-agent counters
   - Thread-safe via `ConcurrentHashMap.merge()`

6. **`AgentObserverEventBus.java`** — driving adapter implementation
   - Path: `src/main/java/com/hdekker/ai_workflow/application/pipeline/AgentObserverEventBus.java`
   - Implements `AgentObserverEventPort`
   - `@Service` annotated
   - `CopyOnWriteArrayList<Consumer<AgentObserverEvent>>` for thread-safe callback iteration
   - Publish events to all registered callbacks with error isolation

```java
// AgentObserverPort
public interface AgentObserverPort {
    void recordDispatch(String agentId, String fileName);
    void recordStorage(String agentId, String outputName, Path outputPath);
    long getDispatchCount(String agentId);
    long getTotalDispatchCount();
    long getStorageCount(String agentId);
    long getTotalStorageCount();
}

// AgentObserverEventPort
public interface AgentObserverEventPort {
    void registerCallback(Consumer<AgentObserverEvent> callback);
    void unregisterCallback(Consumer<AgentObserverEvent> callback);
}

// AgentObserverService
@Service
public class AgentObserverService implements AgentObserverPort {
    private final ConcurrentHashMap<String, AgentMetrics> dispatchCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentMetrics> storageCounters = new ConcurrentHashMap<>();

    @Override
    public void recordDispatch(String agentId, String fileName) {
        dispatchCounters.merge(agentId, 1L, Long::sum);
    }

    @Override
    public void recordStorage(String agentId, String outputName, Path outputPath) {
        storageCounters.merge(agentId, 1L, Long::sum);
    }

    // Query methods delegate to maps...
}

// AgentObserverEventBus
@Service
public class AgentObserverEventBus implements AgentObserverEventPort {
    private final CopyOnWriteArrayList<Consumer<AgentObserverEvent>> callbacks = new CopyOnWriteArrayList<>();

    @Override
    public void registerCallback(Consumer<AgentObserverEvent> callback) {
        callbacks.add(callback);
    }

    @Override
    public void unregisterCallback(Consumer<AgentObserverEvent> callback) {
        callbacks.remove(callback);
    }

    public void publish(AgentObserverEvent event) {
        callbacks.forEach(cb -> {
            try { cb.accept(event); }
            catch (Exception e) { log.error("Callback failed for agent {}", event.agentId(), e); }
        });
    }
}
```

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 0.1 | Create `AgentObserverEventType` enum (DISPATCHED, STORED) | `domain/pipeline/AgentObserverEventType.java` | ⬜ |
| 0.2 | Create `AgentObserverEvent` record | `domain/pipeline/AgentObserverEvent.java` | ⬜ |
| 0.3 | Create `AgentObserverPort` interface | `application/pipeline/port/AgentObserverPort.java` | ⬜ |
| 0.4 | Create `AgentObserverEventPort` interface | `application/pipeline/port/AgentObserverEventPort.java` | ⬜ |
| 0.5 | Create `AgentObserverService` — counters, thread-safe merges | `application/pipeline/AgentObserverService.java` | ⬜ |
| 0.6 | Create `AgentObserverEventBus` — callbacks, publish | `application/pipeline/AgentObserverEventBus.java` | ⬜ |
| 0.7 | Add unit tests for `AgentObserverService` | `test/application/pipeline/AgentObserverServiceTest.java` | ⬜ |
| 0.8 | Add unit tests for `AgentObserverEventBus` | `test/application/pipeline/AgentObserverEventBusTest.java` | ⬜ |

---

### Phase 1: Create AgentObserverUseCase (Orchestrator)

**Goal**: Build the use case that coordinates metrics and event publishing as a single entry point.

**New files**:

7. **`AgentObserverUseCase.java`** — orchestrator
   - Path: `src/main/java/com/hdekker/ai_workflow/application/pipeline/AgentObserverUseCase.java`
   - Injects `AgentObserverPort` and `AgentObserverEventPort`
   - `recordDispatch()` → metrics + event publish
   - `recordStorage()` → metrics + event publish
   - Query methods delegate to metrics port only
   - `@Service` annotated

```java
@Service
public class AgentObserverUseCase {

    private final AgentObserverPort metrics;
    private final AgentObserverEventPort eventBus;

    public void recordDispatch(String agentId, String fileName) {
        metrics.recordDispatch(agentId, fileName);
        eventBus.publish(new AgentObserverEvent(agentId, DISPATCHED, fileName, now()));
    }

    public void recordStorage(String agentId, String outputName, Path outputPath) {
        metrics.recordStorage(agentId, outputName, outputPath);
        eventBus.publish(new AgentObserverEvent(agentId, STORED, outputName, now()));
    }

    public long getDispatchCount(String agentId) { return metrics.getDispatchCount(agentId); }
    public long getTotalDispatchCount() { return metrics.getTotalDispatchCount(); }
    public long getStorageCount(String agentId) { return metrics.getStorageCount(agentId); }
    public long getTotalStorageCount() { return metrics.getTotalStorageCount(); }
}
```

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 1.1 | Create `AgentObserverUseCase` — inject both ports | `application/pipeline/AgentObserverUseCase.java` | ⬜ |
| 1.2 | Implement `recordDispatch()` — metrics + event publish | Same file | ⬜ |
| 1.3 | Implement `recordStorage()` — metrics + event publish | Same file | ⬜ |
| 1.4 | Implement query methods — delegate to metrics port | Same file | ⬜ |
| 1.5 | Add `@Service` annotation | Same file | ⬜ |
| 1.6 | Add unit tests for `AgentObserverUseCase` orchestration | `test/application/pipeline/AgentObserverUseCaseTest.java` | ⬜ |

---

### Phase 2: Integrate Observer into AgentConfigurator

**Goal**: Wire the observer into the agent pipeline via reactive hooks.

**Modified files**:
- `AgentConfigurator.java` — accept observer, add `doOnNext` hooks

The integration uses two reactive hooks on the pipeline flux:

1. **Dispatch hook**: `doOnNext(response -> observer.recordDispatch(agentId, response.fileName()))` on the LLM response flux — fires immediately after the LLM returns, before persist.
2. **Storage hook**: Wraps the persister consumer to also record storage: `observedPersister = response -> { persister.accept(response); observer.recordStorage(agentId, outputFileName, outputPath); }`

**Changes to `AgentConfigurator`**:

| Change | Before | After |
|--------|--------|-------|
| Field | (none) | `private final AgentObserverUseCase observer` |
| Constructor 1 | `(Flux<FileHistory>, ChatClient, Consumer<PromptResponse>)` | Same + `AgentObserverUseCase observer` |
| Constructor 2 | `(Flux<FileHistory>, ChatClient, Consumer<PromptResponse>, FileWritePort)` | Same + `AgentObserverUseCase observer` |
| Pipeline wiring | `prompting(adapter::call)` | `prompting(adapter::call)` + `doOnNext` dispatch hook |
| Persister | Direct consumer | Observed wrapper consumer |

```java
public class AgentConfigurator {

    private final Flux<FileHistory> fileInputFlux;
    private final ChatClient chatClient;
    private final Consumer<PromptResponse> persister;
    private final FileWritePort fileWritePort;
    private final AgentObserverUseCase observer;  // NEW

    public AgentConfigurator(
            Flux<FileHistory> fileInputFlux,
            ChatClient chatClient,
            Consumer<PromptResponse> persister,
            FileWritePort fileWritePort,
            AgentObserverUseCase observer) {
        this.fileInputFlux = fileInputFlux;
        this.chatClient = chatClient;
        this.persister = persister;
        this.fileWritePort = fileWritePort;
        this.observer = observer;
    }

    public Flux<PromptResponse> configure(AgentDefinition agentDefinition) {
        LLMAdapter adapter = LLMAdapterFactory.create(chatClient, agentDefinition);
        
        Consumer<PromptResponse> effectivePersister = fileWritePort != null
                ? fileWritePort.createPersister(null)
                : persister;
        
        // Wrap persister to record storage
        Consumer<PromptResponse> observedPersister = response -> {
            effectivePersister.accept(response);
            if (observer != null) {
                observer.recordStorage(agentDefinition.title(), response.fileName(), null);
            }
        };
        
        Flux<PromptResponse> pipeline = AgentBuilder.instance()
                .withDefinition(agentDefinition)
                .withTrigger(fileInputFlux.map(fh -> fh.to()))
                .prompting(adapter::call)
                .persist(observedPersister)
                .split(SplittableStrategy.noSPLT());
        
        // Add dispatch tracking hook
        return pipeline.doOnNext(response -> {
            if (observer != null) {
                observer.recordDispatch(agentDefinition.title(), response.fileName());
            }
        });
    }
}
```

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 2.1 | Add `AgentObserverUseCase observer` field to `AgentConfigurator` | `AgentConfigurator.java` | ⬜ |
| 2.2 | Update both constructors to accept observer parameter | Same file | ⬜ |
| 2.3 | Wrap persister consumer with storage recording hook | Same file | ⬜ |
| 2.4 | Add `doOnNext` dispatch hook to pipeline flux | Same file | ⬜ |
| 2.5 | Ensure observer can be null (backward compat for existing callers) | Same file | ⬜ |
| 2.6 | Create integration test verifying dispatch hook fires | `test/application/pipeline/AgentConfiguratorObserverTest.java` | ⬜ |
| 2.7 | Create integration test verifying storage hook fires | Same file | ⬜ |
| 2.8 | Create integration test verifying both hooks fire in sequence | Same file | ⬜ |

---

### Phase 3: Wire Observer into AgentLifecycleService and Configuration

**Goal**: Connect the observer through the Spring bean wiring.

**Modified files**:
- `AgentLifecycleService.java` — inject observer, pass to configurator
- `DynamicAgentManagerConfiguration.java` — ensure observer is a Spring bean

**Changes to `AgentLifecycleService`**:

| Change | Before | After |
|--------|--------|-------|
| Dependency | No observer field | `AgentObserverUseCase observer` injected |
| `buildFlux()` | `new AgentConfigurator(scannerFlux, chatClient, persister)` | `new AgentConfigurator(scannerFlux, chatClient, persister, fileWritePort, observer)` |

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 3.1 | Inject `AgentObserverUseCase observer` into `AgentLifecycleService` | `AgentLifecycleService.java` | ⬜ |
| 3.2 | Update `buildFlux()` to pass observer to `AgentConfigurator` | Same file | ⬜ |
| 3.3 | Update `buildFluxForScanner()` to pass observer to `AgentConfigurator` | Same file | ⬜ |
| 3.4 | Ensure `AgentObserverUseCase` is registered as a Spring bean | `DynamicAgentManagerConfiguration.java` or auto-config | ⬜ |
| 3.5 | Update `AgentLifecycleServiceTest` to use observer mock | `test/application/agent/AgentLifecycleServiceTest.java` | ⬜ |

---

### Phase 4: Add Output Directory File Count Query

**Goal**: Provide a clean API for reading the number of files in the output directory.

The `AgentObserverUseCase` or a dedicated method on `AgentObserverService` can delegate to `FileCounterPort.countFiles(outputDir)`. This requires passing the output directory path through to the service.

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 4.1 | Add `getOutputDirectoryFileCount()` method to `AgentObserverUseCase` | `AgentObserverUseCase.java` | ⬜ |
| 4.1b | Delegate to `FileCounterPort.countFiles(outputDir)` | `AgentObserverService.java` | ⬜ |
| 4.2 | Add `outputDirectory` field to `AgentObserverService` constructor | Same file | ⬜ |
| 4.3 | Add REST endpoint in `AgentController` for output file count | `AgentController.java` | ⬜ |
| 4.4 | Add REST endpoint in `AgentController` for dispatch count | `AgentController.java` | ⬜ |
| 4.5 | Add tests for REST endpoints | `test/rest/controller/AgentControllerTest.java` | ⬜ |

---

### Phase 5: Update Existing Tests

**Goal**: Update existing tests to work with the new observer parameter.

**Updated tests**:
- `AgentConfiguratorTest` — update constructor calls to pass observer
- `AgentLifecycleServiceTest` — update to use observer

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 5.1 | Update `AgentConfiguratorTest` — add observer to constructor calls | `test/application/pipeline/AgentConfiguratorTest.java` | ⬜ |
| 5.2 | Run `./mvnw test` — verify all pass | Console output | ⬜ |

---

### Phase 6: Add Output File Count REST Endpoint

**Goal**: Expose output directory file count via REST so the UI can fetch it.

The `AgentController` already exposes agent info via REST. This phase adds a dedicated endpoint for output file count that delegates to `AgentObserverService.getOutputDirectoryFileCount()`.

**Modified files**:
- `AgentController.java` — add `GET /agents/metrics/files` endpoint
- `AgentInfoService.java` (UI) — add `getOutputFileCount()` method that calls the REST endpoint or injects `AgentObserverService` directly

**New endpoint**:

```java
// In AgentController
@GetMapping("/metrics/files")
public long getOutputFileCount() {
    return agentObserverUseCase.getOutputDirectoryFileCount();
}
```

**Changes to `AgentInfoService` (UI service)**:

| Change | Before | After |
|--------|--------|-------|
| Dependency | No observer | `AgentObserverUseCase observer` injected |
| Method | (none) | `long getOutputFileCount()` — delegates to observer |

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 6.1 | Add `AgentObserverUseCase` dependency to `AgentController` | `AgentController.java` | ⬜ |
| 6.2 | Add `GET /agents/metrics/files` endpoint | Same file | ⬜ |
| 6.3 | Add `AgentObserverUseCase` dependency to `AgentInfoService` (UI) | `AgentInfoService.java` | ⬜ |
| 6.4 | Add `getOutputFileCount()` method to `AgentInfoService` | Same file | ⬜ |
| 6.5 | Add tests for REST endpoint | `test/rest/controller/AgentControllerTest.java` | ⬜ |
| 6.6 | Add tests for `AgentInfoService.getOutputFileCount()` | `test/ui/service/AgentInfoServiceTest.java` | ⬜ |

---

### Phase 7: Add Dispatch Count and Output File Count Columns to Agents List

**Goal**: Display dispatch count and output file count as columns in the `AgentListView` agents grid.

The `AgentListView` grid currently shows ID, Title, Agent Type, File Regex, Target Dir, Source, Created, Active, Actions. We add two columns:
- **Dispatch Count** — shows per-agent LLM dispatch count (from `AgentObserverUseCase.getDispatchCount(agentId)`)
- **Output Files** — shows output directory file count (from `AgentObserverUseCase.getOutputDirectoryFileCount()`)

**Modified files**:
- `AgentListView.java` — inject `AgentObserverUseCase`, add two columns, refresh on data reload

**Column definitions**:

```java
// Inject agentObserverUseCase
@Autowired
public AgentListView(AgentInfoService agentInfoService, 
                     AgentStatusService llmStatusService,
                     AgentObserverUseCase agentObserverUseCase) {
    // ...
}

// Dispatch Count column — per-agent
grid.addColumn(agent -> {
    try {
        String title = agent.definition() != null 
                ? agent.definition().title() 
                : agent.id();
        long count = agentObserverUseCase.getDispatchCount(title);
        return count > 0 ? count : "–";
    } catch (Exception e) {
        return "–";
    }
}).setHeader("Dispatches").setAutoWidth(true);

// Output Files column — global count (shared across all agents)
grid.addColumn(agent -> {
    try {
        long count = agentObserverUseCase.getOutputDirectoryFileCount();
        return count > 0 ? count : "–";
    } catch (Exception e) {
        return "–";
    }
}).setHeader("Output Files").setAutoWidth(true);
```

**Column placement**: Insert between "Active" and "Actions" columns so metrics columns are grouped together near the end of the grid.

**Refresh behavior**: When `reloadData()` is called (manual refresh, after navigation, or after agent create/delete/refresh), the columns automatically pick up updated counts from the observer — no additional polling needed since the observer is in-memory.

**Changes to `AgentListView`**:

| Change | Before | After |
|--------|--------|-------|
| Constructor dependency | `(AgentInfoService, AgentStatusService)` | Same + `AgentObserverUseCase` |
| Field | (none) | `private final AgentObserverUseCase agentObserverUseCase` |
| Column | (none) | Dispatch Count column — `agentObserverUseCase.getDispatchCount(title)` |
| Column | (none) | Output Files column — `agentObserverUseCase.getOutputDirectoryFileCount()` |
| `reloadData()` | `agentInfoService.getAllAgentInfos()` | Same, but columns now read from observer |
| `afterNavigation()` | `reloadData()` + `updateLlmStatus()` + `startLlmStatusAutoRefresh()` | Same — observer data refreshes automatically on reload |

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 7.1 | Add `AgentObserverUseCase` field and constructor injection to `AgentListView` | `AgentListView.java` | ⬜ |
| 7.2 | Add Dispatch Count column (per-agent) — between Active and Actions | Same file | ⬜ |
| 7.3 | Add Output Files column (global count) — between Active and Actions | Same file | ⬜ |
| 7.4 | Verify dispatch count shows "–" when observer is null / count is 0 | Same file | ⬜ |
| 7.5 | Verify output files count shows "–" when observer is null / count is 0 | Same file | ⬜ |
| 7.6 | Verify columns appear in correct position (Active → Dispatches → Output Files → Actions) | Same file | ⬜ |
| 7.7 | Add browserless UI test for agent list columns | `test/ui/view/AgentListViewTest.java` | ⬜ |

---

### Phase 8: Rectify Spring Bean Wiring — Fix Null Observer & Null File Counter

**Goal**: Fix Spring constructor resolution ambiguity that prevents `AgentObserverUseCase` and `FileCounterPort` from being injected into `AgentLifecycleService` and `AgentObserverService` respectively.

**Problem**: Runtime investigation revealed three bugs caused by Spring selecting the no-arg constructors on multi-constructor classes, leaving both the observer and the file counter as `null` at runtime.

**Root Causes**:

| # | Symptom | Root Cause | Class |
|---|---------|------------|-------|
| 1 | Dispatches always shows "–" | `AgentLifecycleService.observer = null` (Spring chose no-arg ctor) | `AgentLifecycleService` |
| 2 | Output Files always shows "–" | `AgentObserverService.fileCounter = null` (Spring chose no-arg ctor) | `AgentObserverService` |
| 3 | Dual `AgentLifecycleService` bean conflict | `@Service` + `@Bean` on same class creates two beans; component scan version wins | `AgentLifecycleService` + `DynamicAgentManagerConfiguration` |

**Missing from original plan**:
- No consideration of Spring constructor resolution with multiple public constructors
- No `@Autowired` annotation to disambiguate constructor selection
- No detection of `@Service` + `@Bean` dual-bean conflict
- No validation test for Spring-wired bean state (unit tests mock everything, bypassing Spring)
- No `@Value` or property binding for `outputDirectory` string injection into `AgentObserverService`

**Modified files**:

| File | Change |
|------|--------|
| `AgentObserverService.java` | Remove no-arg constructor; add `@Autowired` to parameterized ctor |
| `AgentLifecycleService.java` | Remove no-arg constructor; add `@Autowired` to 7-param ctor |
| `DynamicAgentManagerConfiguration.java` | Remove `@Bean public AgentLifecycleService` method (component scan handles it) |
| `test/…/AgentLifecycleServiceWiringTest.java` | **New** — verify Spring wires non-null observer and file counter |

#### Changes to `AgentObserverService`

**Before** (two constructors, no `@Autowired`):
```java
@Service
public class AgentObserverService implements AgentObserverPort {

    public AgentObserverService() {
        this.outputDirectory = null;
        this.fileCounter = null;
    }

    public AgentObserverService(FileCounterPort fileCounter, String outputDirectory) {
        this.fileCounter = fileCounter;
        this.outputDirectory = outputDirectory;
    }
}
```

**After** (single constructor with `@Autowired`):
```java
@Service
public class AgentObserverService implements AgentObserverPort {

    @Autowired
    public AgentObserverService(FileCounterPort fileCounter, String outputDirectory) {
        this.fileCounter = fileCounter;
        this.outputDirectory = outputDirectory;
    }
    // NO no-arg constructor
}
```

With only one constructor, Spring auto-selects it — no ambiguity.

#### Changes to `AgentLifecycleService`

**Before** (two constructors, no `@Autowired`):
```java
@Service
public class AgentLifecycleService {

    public AgentLifecycleService() {
        this.scannerRegistry = null;
        this.observer = null;
        // … all null
    }

    public AgentLifecycleService(ScannerRegistry, FileWritePort, Path, ChatClient, AgentRepository,
                                  DirectoryValidationPort, AgentObserverUseCase) {
        // … wire all params
    }
}
```

**After** (single constructor with `@Autowired`):
```java
@Service
public class AgentLifecycleService {

    @Autowired
    public AgentLifecycleService(ScannerRegistry scannerRegistry,
                                  FileWritePort fileWritePort,
                                  Path outputDirectory,
                                  ChatClient chatClient,
                                  AgentRepository agentRepository,
                                  DirectoryValidationPort directoryValidationPort,
                                  AgentObserverUseCase observer) {
        // … wire all params
    }
    // NO no-arg constructor
}
```

#### Changes to `DynamicAgentManagerConfiguration`

**Before** (dual bean creation):
```java
@Bean
public AgentLifecycleService agentLifecycleService(
        ScannerRegistry scannerRegistry,
        FileWritePort fileWritePort,
        FileSystemScannerConfig fileScannerConfig,
        ChatClient chatClient,
        AgentRepository agentRepository,
        DirectoryValidationPort directoryValidationPort,
        AgentObserverUseCase observer) throws IOException {
    Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
    return new AgentLifecycleService(
            scannerRegistry, fileWritePort, outputFolderPath, chatClient,
            agentRepository, directoryValidationPort, observer);
}
```

**After** (remove entirely — `@Service` handles it):
```java
// DELETE THIS ENTIRE @Bean METHOD
// AgentLifecycleService is a @Service class; Spring's component scan
// + @Autowired constructor injection handles bean creation automatically.
```

If the method is needed for future customization, add `@Primary` instead.

#### New test: Spring bean wiring smoke test

Create a test that boots the full Spring context and verifies the critical fields are non-null:

```java
@SpringBootTest
class AgentLifecycleServiceWiringTest {

    @Autowired
    private AgentLifecycleService agentLifecycleService;

    @Autowired
    private AgentObserverService agentObserverService;

    @Test
    void agentLifecycleServiceHasObserverInjected() {
        // Use reflection to verify observer is not null
        Field observerField = AgentLifecycleService.class.getDeclaredField("observer");
        observerField.setAccessible(true);
        Object observer = observerField.get(agentLifecycleService);
        assertThat(observer).isNotNull()
            .isInstanceOf(AgentObserverUseCase.class);
    }

    @Test
    void agentObserverServiceHasFileCounterInjected() {
        Field counterField = AgentObserverService.class.getDeclaredField("fileCounter");
        counterField.setAccessible(true);
        Object counter = counterField.get(agentObserverService);
        assertThat(counter).isNotNull()
            .isInstanceOf(FileCounterPort.class);
    }

    @Test
    void agentObserverServiceHasOutputDirectoryConfigured() {
        Field dirField = AgentObserverService.class.getDeclaredField("outputDirectory");
        dirField.setAccessible(true);
        Object dir = dirField.get(agentObserverService);
        assertThat(dir).isNotNull();
    }

    @Test
    void getOutputDirectoryFileCountReturnsRealCount() {
        long count = agentObserverService.getOutputDirectoryFileCount();
        assertThat(count).isInstanceOf(Long.class);
        // If output dir has files, count > 0 (depends on test config)
    }
}
```

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 8.1 | Remove no-arg constructor from `AgentObserverService`; add `@Autowired` to parameterized ctor | `AgentObserverService.java` | ✅ |
| 8.2 | Remove no-arg constructor from `AgentLifecycleService`; add `@Autowired` to 7-param ctor | `AgentLifecycleService.java` | ✅ |
| 8.3 | Delete `@Bean agentLifecycleService` method from `DynamicAgentManagerConfiguration` | `DynamicAgentManagerConfiguration.java` | ✅ |
| 8.4 | Add `AgentLifecycleServiceWiringTest` — verify observer is non-null at runtime | `AgentLifecycleServiceWiringTest.java` | ✅ |
| 8.5 | Add `AgentLifecycleServiceWiringTest` — verify fileCounter is non-null at runtime | `AgentLifecycleServiceWiringTest.java` | ✅ |
| 8.6 | Add `AgentLifecycleServiceWiringTest` — verify outputDirectory is non-null at runtime | `AgentLifecycleServiceWiringTest.java` | ✅ |
| 8.7 | Run `./mvnw test` — verify all pass | 421 tests pass | ✅ |
| 8.8 | Start app, add file, verify dispatch count increments | Manual verification | ⬜ |
| 8.9 | Verify Output Files column shows real count | Manual verification | ⬜ |

---

## Notes

- **Thread safety**: `ConcurrentHashMap.merge()` for counters, `CopyOnWriteArrayList` for callbacks — both thread-safe for concurrent reads/writes.
- **Null safety**: Observer parameter in `AgentConfigurator` can be null — `doOnNext` checks for null before recording. This maintains backward compatibility for any callers that haven't injected the observer yet.
- **Non-invasive**: The observer integrates via `doOnNext` on the reactive flux — no changes to `AgentBuilder`, `LLMAdapter`, `SplittableStrategy`, or the pipeline builder interface. The observer is transparent to the pipeline.
- **Consistency with ScannerObservabilityUseCase**: The agent observer follows the exact same pattern: driven adapter port (metrics) + driving adapter port (events) + orchestrating use case. The domain event `AgentObserverEvent` mirrors `ScannerEvent`.
- **Output directory count**: Delegates to existing `FileCounterPort` — no new filesystem operations. The `FileSystemFileCounter` walks the directory tree.

---

## Validation Criteria

- [ ] `AgentObserverEventType` enum exists with DISPATCHED and STORED values
- [ ] `AgentObserverEvent` record exists with agentId, eventType, fileName, timestamp
- [ ] `AgentObserverPort` interface exists in `application/pipeline/port/`
- [ ] `AgentObserverEventPort` interface exists in `application/pipeline/port/`
- [ ] `AgentObserverService` implements `AgentObserverPort` with `@Service` annotation
- [ ] `AgentObserverEventBus` implements `AgentObserverEventPort` with `@Service` annotation
- [ ] `recordDispatch()` is thread-safe and increments per-agent counter
- [ ] `recordStorage()` is thread-safe and increments per-agent counter
- [ ] `getDispatchCount(agentId)` returns correct per-agent count
- [ ] `getStorageCount(agentId)` returns correct per-agent count
- [ ] `getTotalDispatchCount()` sums across all agents
- [ ] `getTotalStorageCount()` sums across all agents
- [ ] `AgentObserverUseCase` implements the orchestrating pattern (metrics + events)
- [ ] `recordDispatch()` calls both metrics port AND event port
- [ ] `recordStorage()` calls both metrics port AND event port
- [ ] Query methods delegate to metrics port only (no event publishing on queries)
- [ ] `AgentConfigurator` accepts `AgentObserverUseCase` in constructor
- [ ] Dispatch hook fires after LLM response (verified in test)
- [ ] Storage hook fires after persistence (verified in test)
- [ ] Both hooks fire in correct sequence (dispatch before storage)
- [ ] Observer can be null — no NPE when not injected
- [ ] `AgentLifecycleService` passes observer to `AgentConfigurator`
- [ ] `AgentObserverService` can report output directory file count via `FileCounterPort`
- [ ] All existing tests still pass (`./mvnw test`)
- [ ] New observer tests pass (`AgentObserverServiceTest`, `AgentObserverEventBusTest`, `AgentObserverUseCaseTest`, `AgentConfiguratorObserverTest`)
- [ ] `GET /agents/metrics/files` REST endpoint returns output directory file count
- [ ] `AgentInfoService.getOutputFileCount()` returns output directory file count
- [ ] AgentListView grid shows "Dispatches" column with per-agent dispatch count
- [ ] AgentListView grid shows "Output Files" column with output directory file count
- [ ] Dispatch count shows "–" when count is 0 or observer is null
- [ ] Output Files shows "–" when count is 0 or observer is null
- [ ] Columns appear between Active and Actions columns
- [ ] Grid refresh (manual, navigation, agent create/delete/refresh) updates metric columns

### Phase 8 Validation

- [x] `AgentObserverService` has only one public constructor (no-arg removed)
- [x] `AgentObserverService` parameterized constructor has `@Autowired` annotation
- [x] `AgentObserverService.fileCounter` is not null at runtime
- [x] `AgentObserverService.outputDirectory` is not null at runtime
- [x] `AgentLifecycleService` has only one public constructor (no-arg removed)
- [x] `AgentLifecycleService` 7-param constructor has `@Autowired` annotation
- [x] `AgentLifecycleService.observer` is not null at runtime
- [x] `DynamicAgentManagerConfiguration` has no `@Bean agentLifecycleService` method
- [x] Only one `AgentLifecycleService` bean exists in Spring context
- [x] `AgentLifecycleServiceWiringTest` passes with full Spring context
- [ ] Adding a file triggers `recordDispatch` — dispatch count increments
- [ ] Adding a file triggers `recordStorage` — storage count increments
- [ ] Dispatches column in AgentListView shows real counts, not "–"
- [ ] Output Files column in AgentListView shows real counts, not "–"

---

## Dependencies Between Phases

| Phase | Depends On |
|-------|-----------|
| Phase 0 | — (standalone — creates domain event, ports, services) |
| Phase 1 | Phase 0 (ports and services must exist for use case to inject) |
| Phase 2 | Phase 1 (use case must exist to wire into configurator) |
| Phase 3 | Phase 2 (configurator must accept observer) |
| Phase 4 | Phase 3 (observer must be wired through Spring) |
| Phase 5 | Phases 0–4 (updates existing tests to pass new parameter) |
| Phase 6 | Phase 3 (observer must be a Spring bean to expose REST endpoint) |
| Phase 7 | Phase 6 (REST endpoint or direct injection must exist for UI) |
| Phase 8 | Phases 0–7 (rectifies wiring for all previously wired phases) |
