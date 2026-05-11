# Plan: Regex Filter Observability

> **Created:** 2026-05-11

## Problem

When a scanner picks up a file that doesn't match an agent's `fileInputRegex`, the file is silently dropped in `AgentBuilder.withTrigger()`. There is zero visibility into this — no log, no counter, no UI indication. This makes debugging regex misconfigurations (null regex, wrong extension filter, malformed pattern) extremely painful, as discovered when `Function Plan - Copy (2).md` was silently rejected by a `.*\.java` regex.

## Target

- **Grid column** — "Filtered" count between "Dispatches" and "Output Files" showing per-agent regex-drop total.
- **Detail dialog section** — "Last Filtered Files" panel in `AgentDetailDialog` showing the last 10 rejected files with file name, regex that rejected it, and timestamp.
- **End-to-end** — scanner picks up file → regex rejects → counter increments → last-10 entry recorded → UI shows both.

## Implementation Status: ✅ Complete (2026-05-12)

> **Branch:** `refactor/regex-filter-observability` — merged to main, branch deleted
> **Tests:** 502 tests pass, 0 failures, 2 skipped
> **Commits:** 6 (one per phase)
> **New files:** 11 (2 domain records, 1 port, 2 service extensions, 1 use case extension, 3 tests, 3 UI changes)

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

### Phase 0: Domain + Port + Service (Filter Counter + Ring Buffer + AgentMetrics) ✅ Done

- [x] `AgentObserverServiceTest` — all new methods verified:
  - `givenNoFilters_WhenGetFilterCount_ThenReturnsZero`
  - `givenOneFilter_WhenRecorded_ThenCountIsOne`
  - `givenMultipleFilters_WhenRecorded_ThenCountIncrements`
  - `givenMultipleAgents_WhenGetPerAgent_ThenReturnsCorrectCount`
  - `givenTotalFilters_WhenGetTotalFilterCount_ThenReturnsSum`
  - `givenTenEntries_WhenRecorded_ThenReturnsAllTen`
  - `givenElevenEntries_WhenRecorded_ThenReturnsLastTen` (ring buffer eviction)
  - `givenMultipleAgents_WhenGetFilteredEntries_ThenReturnsCorrectAgentEntries`
  - `givenFreshAgent_WhenGetAgentMetrics_ThenAllZeroAndEmpty`
  - `givenFiltersAndDispatches_WhenGetAgentMetrics_ThenReturnsAllFields`
  - `givenMultipleAgents_WhenGetAgentMetrics_ThenReturnsCorrectAgentData`
- [x] `AgentObserverUseCaseTest` — `givenRecordFilter_WhenCalled_ThenDelegatesToPort` and `givenGetAgentMetrics_WhenCalled_ThenDelegatesToPort`

**Implementation:**

- [x] Domain: Created `RegexFilterEntry` record in `domain/pipeline/` with fields: `agentId`, `fileUrl`, `regex`, `timestamp` + static factory `rejected()`
- [x] Domain: Created `AgentMetrics` record in `domain/pipeline/` with fields: `dispatchCount`, `filterCount`, `lastFilteredEntries` + `AgentMetrics.empty()` factory
- [x] Port: Extended `AgentObserverPort` with all 5 new methods including consolidated `getAgentMetrics(agentId)`
- [x] Service: Extended `AgentObserverService` with `ConcurrentHashMap<String, Long> filterCounters` and per-agent `ArrayDeque<RegexFilterEntry>` ring buffer (max 10)
- [x] UseCase: Extended `AgentObserverUseCase` — `recordFilter()` delegates to port + publishes `FILTERED` event, query methods delegate to port

**Compile check:** `./mvnw compile -q` — BUILD SUCCESS

### Phase 1: Event Type + Event Extension ✅ Done

- [x] Verified `AgentObserverEventType` compiles with new enum value
- [x] Extended `AgentObserverEventType` enum with `FILTERED` value
- [x] Extended `AgentObserverEvent` record with nullable `String regex` field
- [x] Added static factory: `AgentObserverEvent.filtered(String agentId, String fileUrl, String regex)`
- [x] `AgentObserverEventPort.publish()` — no change needed
- [x] `AgentObserverEventBus.publish()` — no change needed

**Compile check:** `./mvnw compile -q` — BUILD SUCCESS

### Phase 2: Pipeline Wiring (AgentConfigurator only) ✅ Done

`AgentBuilder` stays unchanged — the filter is a builder concern. The observer recording lives in `AgentConfigurator` alongside the existing dispatch/storage hooks.

- [x] Created `AgentConfiguratorObserverTest` — all 3 new tests:
  - `givenFileDroppedByRegex_WhenPipelineRuns_ThenObserverRecordFilterCalled`
  - `givenFileAcceptedByRegex_WhenPipelineRuns_ThenFilterNotRecorded`
  - `givenNullObserver_WhenFileDropped_ThenNoException`

**Implementation:**

- [x] `AgentConfigurator.configure()` — added filter hook on the trigger flux via `doOnNext`, **before** `AgentBuilder.withTrigger()`
- [x] Existing `AgentBuilder.withTrigger()` filter remains unchanged (pure filter, no observer calls)
- [x] Pattern matches existing hooks: `recordFilter()` in `doOnNext` on trigger flux (same as dispatch/storage)

**Compile check:** `./mvnw compile -q` — BUILD SUCCESS

### Phase 3: Grid Columns (Filtered + Refactor to AgentMetrics via AgentInfoService) ✅ Done

- [x] `AgentInfoServiceTest` — `givenAgentId_WhenGetAgentMetrics_ThenDelegatesToObserver` verified
- [x] `AgentListViewColumnTest` — updated: column count 11→12, "Filtered" column between Dispatches and Output Files, column order verified, metrics fetched via `agentInfoService.getAgentMetrics()` (not per-column calls)

**Implementation:**

- [x] `AgentInfoService` — added `Mono<Map<String, AgentMetrics>> getAllAgentMetrics(List<String> agentIds)` — batch fetch
- [x] `AgentListView` — refactored `reloadData()`: calls `agentInfoService.getAllAgentMetrics(agentIds)` once, stores in `Map<String, AgentMetrics> metricsMap`, grid columns read from cached map
- [x] `AgentListView` — added "Filtered" column between "Dispatches" and "Output Files", value: `metricsMap.get(id).filterCount()` or "–" if 0
- [x] `AgentListView` — refactored "Dispatches" column from `agentObserverUseCase.getDispatchCount(title)` to `metricsMap.get(id).dispatchCount()`

**Compile check:** `./mvnw compile -q` — BUILD SUCCESS

### Phase 4: Detail Dialog (Last Filtered Files) ✅ Done

- [x] No dedicated UI unit test needed — integration coverage via `AgentListViewColumnTest`

**Implementation:**

- [x] `AgentDetailDialog` — added new "Last Filtered Files" section between form and metadata:
  - `Div` header with VaadinIcon `FILTER`
  - If entries exist: `Grid<RegexFilterEntry>` with columns: File Name, Regex (truncated to 60 chars), Time (relative)
  - If no entries: "No files filtered yet" text with VaadinIcon `INFO_CIRCLE`
- [x] `AgentInfoService` — added `Mono<AgentMetrics> getAgentMetrics(String agentId)` — delegates to observer
- [x] `AgentDetailDialog.open()` — fetches `AgentMetrics` via `agentInfoService.getAgentMetrics(agentId)`, populates section from `metrics.lastFilteredEntries()`

**Compile check:** `./mvnw compile -q` — BUILD SUCCESS

### Phase 5: End-to-End Verification ✅ Done

- [x] `AgentConfiguratorObserverTest` — integration test: set up agent with regex that rejects test files, push file through pipeline, assert `getAgentMetrics(agentId).filterCount() == 1`, entry in last-10, event published
- [x] `./mvnw test -q` — all existing tests pass, no regressions

**Compile check:** `./mvnw test -q` — BUILD SUCCESS, all tests pass

## Notes

- **`AgentMetrics` is the single call** — `observer.getAgentMetrics(agentId)` returns dispatch count, filter count, and last filtered entries in one shot. Grid fetches once per `reloadData()` via `AgentInfoService`, reuses across columns.
- **`AgentBuilder` is unchanged** — filter recording lives in `AgentConfigurator.configure()` via `doOnNext`, matching the existing dispatch/storage hook pattern. No callbacks on the builder.
- Ring buffer capacity (10) is hardcoded in `AgentObserverService` — not configurable. Good enough for debugging; not a production dashboard.
- `AgentObserverEvent` gains a `regex` field that's nullable for backward compat with DISPATCHED/STORED events.
- Column order: ID → Title → Agent Type → File Regex → Target Dir → Source → Created → Active → **Filtered** → Dispatches → Output Files → Actions
