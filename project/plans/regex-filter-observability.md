# Plan: Regex Filter Observability

> **Created:** 2026-05-11

## Problem

When a scanner picks up a file that doesn't match an agent's `fileInputRegex`, the file is silently dropped in `AgentBuilder.withTrigger()`. There is zero visibility into this — no log, no counter, no UI indication. This makes debugging regex misconfigurations (null regex, wrong extension filter, malformed pattern) extremely painful, as discovered when `Function Plan - Copy (2).md` was silently rejected by a `.*\.java` regex.

## Target

- **Grid column** — "Filtered" count between "Dispatches" and "Output Files" showing per-agent regex-drop total.
- **Detail dialog section** — "Last Filtered Files" panel in `AgentDetailDialog` showing the last 10 rejected files with file name, regex that rejected it, and timestamp.
- **End-to-end** — scanner picks up file → regex rejects → counter increments → last-10 entry recorded → UI shows both.

## Implementation Status: ⬜ Draft

## Hexagonal Structure

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| **Domain** | `RegexFilterEntry` (new record) | Value object: agentId, fileUrl, regex, timestamp |
| **Domain** | `AgentMetrics` (new record) | Consolidated metrics snapshot: dispatchCount, filterCount, lastFilteredEntries |
| **Application Port** | `AgentObserverPort` (extended) | `recordFilter()`, `getAgentMetrics(agentId)` |
| **Application Service** | `AgentObserverService` (extended) | `ConcurrentHashMap` counters + `ArrayDeque` per-agent ring buffer (capacity 10). `getAgentMetrics()` assembles all counters/entries from its own maps into one record. No external calls — the service *is* the store. |
| **Application Use Case** | `AgentObserverUseCase` (extended) | `recordFilter(...)` delegates to port + publishes `FILTERED` event. `getAgentMetrics(agentId)` delegates to port. |
| **Pipeline Wiring** | `AgentConfigurator` (modified) | `doOnNext` on the trigger flux — same pattern as dispatch/storage hooks. Checks `inputRegexMatches()`, calls `observer.recordFilter()` when rejected. `AgentBuilder` is unchanged. |
| **Inbound Adapter (UI Service)** | `AgentInfoService` (modified) | `getAgentMetrics(agentId)` — single entry point for the UI. Calls `observer.getAgentMetrics()`. |
| **Inbound Adapter** | `AgentListView` (modified) | Calls `agentInfoService.getAgentMetrics(id)` once during `reloadData()`, stores in `Map<String, AgentMetrics>`, reuses across columns. Never calls observer directly. |
| **Inbound Adapter** | `AgentDetailDialog` (modified) | Calls `agentInfoService.getAgentMetrics(id)` in `open()`, displays last filtered entries from the returned record. |

## Existing Tests

| Test Class | What it covers | Status |
|------------|---------------|--------|
| `AgentObserverServiceTest` | Dispatch/storage counters, concurrency, output file count | ✅ Green — counters only, no filter |
| `AgentObserverUseCaseTest` | Dispatch/storage delegation to ports | ✅ Green — no filter |
| `AgentObserverEventBusTest` | Event publishing, callback registration | ✅ Green — unrelated to filter |
| `AgentBuilderTest` | Pipeline building with regex filter | ✅ Green — unchanged, filter stays in builder |
| `AgentConfiguratorTest` | Pipeline wiring, observer hooks | ✅ Green — tests dispatch/storage, no filter |
| `AgentConfiguratorObserverTest` | Observer integration with pipeline | ✅ Green — no filter |
| `AgentListViewColumnTest` | Grid column order, dispatch/output columns | ✅ Green — expects 11 columns, will break when adding 12th |
| `AgentListViewDeleteTest` | Delete flow via grid | ✅ Green — unrelated |
| `AgentListViewTest` | Basic view rendering | ✅ Green — may need column count update |

## Test Gaps

- **No test for filter recording** — `AgentObserverService` has no filter counter/ring buffer yet
- **No test for `AgentMetrics` consolidated fetch** — no record exists yet
- **No test for filter use case delegation** — `AgentObserverUseCase` has no filter methods
- **No test for filter recording in AgentConfigurator** — observer not called on filter
- **No test for "Filtered" column in grid** — column doesn't exist yet
- **No test for "Last Filtered Files" in detail dialog** — section doesn't exist yet
- **No integration test for end-to-end filter flow** — scanner → filter → UI

## Phases

### Phase 0: Domain + Port + Service (Filter Counter + Ring Buffer + AgentMetrics)

**Tests first:**

- [ ] `AgentObserverServiceTest` — new methods:
  - `givenNoFilters_WhenGetFilterCount_ThenReturnsZero`
  - `givenOneFilter_WhenRecorded_ThenCountIsOne`
  - `givenMultipleFilters_WhenRecorded_ThenCountIncrements`
  - `givenMultipleAgents_WhenGetPerAgent_ThenReturnsCorrectCount`
  - `givenTotalFilters_WhenGetTotalFilterCount_ThenReturnsSum`
  - `givenTenEntries_WhenRecorded_ThenReturnsAllTen`
  - `givenElevenEntries_WhenRecorded_ThenReturnsLastTen` (ring buffer eviction)
  - `givenMultipleAgents_WhenGetFilteredEntries_ThenReturnsCorrectAgentEntries`
- [ ] `AgentObserverServiceTest` — **AgentMetrics** tests:
  - `givenFreshAgent_WhenGetAgentMetrics_ThenAllZeroAndEmpty`
  - `givenFiltersAndDispatches_WhenGetAgentMetrics_ThenReturnsAllFields`
  - `givenMultipleAgents_WhenGetAgentMetrics_ThenReturnsCorrectAgentData`
- [ ] `AgentObserverUseCaseTest` — new methods:
  - `givenRecordFilter_WhenCalled_ThenDelegatesToPort`
  - `givenGetAgentMetrics_WhenCalled_ThenDelegatesToPort`

**Implementation:**

- [ ] Domain: Create `RegexFilterEntry` record in `domain/pipeline/`
  - Fields: `String agentId`, `String fileUrl`, `String regex`, `LocalDateTime timestamp`
  - Static factory: `rejected(String agentId, String fileUrl, String regex)`
- [ ] Domain: Create `AgentMetrics` record in `domain/pipeline/`
  - Fields: `long dispatchCount`, `long filterCount`, `List<RegexFilterEntry> lastFilteredEntries`
  - No-args/default factory: `AgentMetrics.empty()` → 0, 0, empty list
- [ ] Port: Extend `AgentObserverPort` interface
  - `void recordFilter(String agentId, String fileUrl, String regex)`
  - `long getFilterCount(String agentId)`
  - `long getTotalFilterCount()`
  - `List<RegexFilterEntry> getLastFilteredEntries(String agentId)`
  - `AgentMetrics getAgentMetrics(String agentId)` — **consolidated fetch**
- [ ] Service: Extend `AgentObserverService`
  - `ConcurrentHashMap<String, Long> filterCounters` (same pattern as dispatch/storage)
  - `ConcurrentHashMap<String, ArrayDeque<RegexFilterEntry>> filterHistory` — per-agent ring buffer, max 10
  - Implement all 5 new port methods
  - `getAgentMetrics(agentId)` assembles: dispatch count + filter count + last filtered entries from existing maps
  - Thread-safe: `ConcurrentHashMap` + `synchronized` or `AtomicReference<ArrayDeque>` for the ring buffer
- [ ] UseCase: Extend `AgentObserverUseCase`
  - `recordFilter(agentId, fileUrl, regex)` → delegates to `metrics` + publishes `FILTERED` event
  - `getFilterCount(agentId)` → delegates to `metrics`
  - `getTotalFilterCount()` → delegates to `metrics`
  - `getLastFilteredEntries(agentId)` → delegates to `metrics`
  - `getAgentMetrics(agentId)` → delegates to `metrics`

**Compile check:** `./mvnw compile -q`

### Phase 1: Event Type + Event Extension

**Tests first:**

- [ ] Verify `AgentObserverEventType` compiles with new enum value

**Implementation:**

- [ ] Extend `AgentObserverEventType` enum: add `FILTERED` value
- [ ] Extend `AgentObserverEvent` record: add `String regex` field (nullable — DISPATCHED/STORED don't have it)
- [ ] Add static factory: `AgentObserverEvent.filtered(String agentId, String fileUrl, String regex)`
- [ ] Update `AgentObserverEventPort.publish()` — no change needed (it's already generic)
- [ ] Update `AgentObserverEventBus.publish()` — no change needed

**Compile check:** `./mvnw compile -q`

### Phase 2: Pipeline Wiring (AgentConfigurator only)

`AgentBuilder` stays unchanged — the filter is a builder concern. The observer recording lives in `AgentConfigurator` alongside the existing dispatch/storage hooks.

**Tests first:**

- [ ] `AgentConfiguratorObserverTest` — new test:
  - `givenFileDroppedByRegex_WhenPipelineRuns_ThenObserverRecordFilterCalled`
  - `givenFileAcceptedByRegex_WhenPipelineRuns_ThenFilterNotRecorded`
  - `givenNullObserver_WhenFileDropped_ThenNoException`

**Implementation:**

- [ ] `AgentConfigurator.configure()` — add filter hook on the trigger flux, **before** `AgentBuilder.withTrigger()`:
  ```java
  Flux<FileHistory> withFilterLogging = fileInputFlux.doOnNext(fh -> {
      PromptRequest pr = fh.to();
      if (!agentDefinition.inputRegexMatches(pr.fileURL())) {
          RegexFilterEntry entry = RegexFilterEntry.rejected(
                  agentDefinition.title(), pr.fileURL(),
                  agentDefinition.fileInputRegex());
          if (observer != null) {
              observer.recordFilter(agentDefinition.title(),
                      entry.fileUrl(), entry.regex());
          }
      }
  });
  // Pass withFilterLogging to AgentBuilder.withTrigger(withFilterLogging)
  // instead of raw fileInputFlux
  ```
- [ ] Existing `AgentBuilder.withTrigger()` filter remains unchanged (pure filter, no observer calls)
- [ ] Pattern matches existing hooks:
  - `recordDispatch()` — in `doOnNext` after LLM returns
  - `recordStorage()` — in `doOnNext` during persist
  - `recordFilter()` — in `doOnNext` on trigger flux, same place

**Compile check:** `./mvnw compile -q`

### Phase 3: Grid Columns (Filtered + Refactor to AgentMetrics via AgentInfoService)

**Call chain:**
```
AgentListView.reloadData()
  → agentInfoService.getAgentMetrics(id)          // UI service — one call per agent
    → observerUseCase.getAgentMetrics(id)          // use case — delegates
      → observerService.getAgentMetrics(id)        // metrics store — reads from its own ConcurrentHashMap maps
        → new AgentMetrics(dispatchCount, filterCount, lastFilteredEntries)
  → stores result in Map<String, AgentMetrics>
  → grid columns read from the cached map (no further calls)
```

**Tests first:**

- [ ] `AgentInfoServiceTest` — new test:
  - `givenAgentId_WhenGetAgentMetrics_ThenDelegatesToObserver`
- [ ] `AgentListViewColumnTest` — update existing tests:
  - `columnOrder_correctSequence`: change expected column count from 11 to 12
  - Add assertion for "Filtered" column position (between Dispatches and Output Files)
  - New test: `filteredColumn_exists` — verifies column header
  - New test: `filteredColumn_displaysCountAfterFiltering` — records filter via observer, reloads grid, asserts count
  - New test: `columnsUseAgentMetrics_WhenReloaded_ThenSingleCallPerRow` — verifies metrics are fetched via `agentInfoService.getAgentMetrics()`, not per-column calls

**Implementation:**

- [ ] `AgentInfoService` — add method:
  - `Mono<Map<String, AgentMetrics>> getAllAgentMetrics(List<String> agentIds)` — batch fetch for all agents
  - Delegates to `observer.getAgentMetrics(id)` for each agent, collects into map
- [ ] `AgentListView` — refactor `reloadData()`:
  - After loading agents, call `agentInfoService.getAllAgentMetrics(agentIds)` once
  - Store result in a field: `Map<String, AgentMetrics> metricsMap`
  - Grid columns read from `metricsMap.get(id)` instead of calling observer directly
- [ ] `AgentListView` — add "Filtered" column:
  - Between "Dispatches" and "Output Files"
  - Value: `metricsMap.get(id).filterCount()` or "–" if 0
- [ ] `AgentListView` — refactor "Dispatches" column:
  - Change from `agentObserverUseCase.getDispatchCount(title)` to `metricsMap.get(id).dispatchCount()`

**Compile check:** `./mvnw compile -q`

### Phase 4: Detail Dialog (Last Filtered Files)

**Tests first:**

- [ ] No dedicated UI unit test needed for dialog internals (dialog is complex and already untested for new sections)
- [ ] Integration coverage via `AgentListViewColumnTest` or manual verification is sufficient

**Implementation:**

- [ ] `AgentDetailDialog` — add new section between form and metadata:
  - `Div` header: "Last Filtered Files" with VaadinIcon `FILTER`
  - If entries exist: `Grid<RegexFilterEntry>` or `VerticalLayout` with small text rows
  - Columns: File Name, Regex (truncated to 60 chars), Time (relative: "2 min ago")
  - If no entries: "No files filtered yet" text with `VaadinIcon.INFO_CIRCLE`
- [ ] `AgentInfoService` — add method (alongside Phase 3 batch fetch):
  - `Mono<AgentMetrics> getAgentMetrics(String agentId)`
  - Delegates to `observer.getAgentMetrics(agentId)`
- [ ] `AgentDetailDialog.open()` — fetch `AgentMetrics` via `agentInfoService.getAgentMetrics(agentId)` and populate the section from `metrics.lastFilteredEntries()`

**Compile check:** `./mvnw compile -q`

### Phase 5: End-to-End Verification

**Tests first:**

- [ ] `AgentConfiguratorObserverTest` — integration test:
  - Set up agent with regex that rejects test files
  - Push file through pipeline
  - Assert: `getAgentMetrics(agentId).filterCount() == 1`, entry in last-10, event published
- [ ] Compile and run existing tests: `./mvnw test -q`

**Implementation:**

- [ ] Run `./mvnw test -q` — all existing tests pass
- [ ] Manual smoke test:
  - Create agent with regex `.*\.java`
  - Drop `.txt` file in target directory
  - Verify: "Filtered" column shows 1, detail dialog shows the filtered entry
  - Drop 12 more files, verify "Filtered" shows 13 but detail shows last 10

**Compile check:** `./mvnw test -q`

## Notes

- **`AgentMetrics` is the single call** — `observer.getAgentMetrics(agentId)` returns dispatch count, filter count, and last filtered entries in one shot. Grid fetches once per `reloadData()` via `AgentInfoService`, reuses across columns.
- **`AgentBuilder` is unchanged** — filter recording lives in `AgentConfigurator.configure()` via `doOnNext`, matching the existing dispatch/storage hook pattern. No callbacks on the builder.
- Ring buffer capacity (10) is hardcoded in `AgentObserverService` — not configurable. Good enough for debugging; not a production dashboard.
- `AgentObserverEvent` gains a `regex` field that's nullable for backward compat with DISPATCHED/STORED events.
- Column order: ID → Title → Agent Type → File Regex → Target Dir → Source → Created → Active → **Filtered** → Dispatches → Output Files → Actions
