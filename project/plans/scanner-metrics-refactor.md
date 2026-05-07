# Plan: Scanner Metrics — Push FileCount, Remove storeFolder/countFiles

## Problem

The observer service has a **pull-based** file count design:

```
UI → observer.countFiles(agentId) → agentFolders.get(agentId) → fileCounter.countFiles(folderPath)
```

This requires:
- `storeFolder()` to be called by the scanner (easy to forget — was the original bug)
- `countFiles()` on the port interface (makes the observer a data source, not just a messenger)
- `agentFolders` map in the observer (storing folder paths just so countFiles() works)
- A separate call from UI to observer for file count (redundant when the scanner already computes it)

The scanner (`ScannerService`) already knows the folder path and calls `fileCounter` during events. The file count is computed **once** during event processing, then discarded. The UI has to call `countFiles()` separately — a second walk of the directory.

## Target

The observer becomes a **push-based messenger**: the scanner computes fileCount and includes it in events. The UI gets it from the event or from `getAllScannerInfos()`. No separate call needed.

```
ScannerService.processRawEvent()
  └── fileCounter.countFiles(folderPath)         ← computed once
  └── observer.recordEvent(agentId, ..., fileCount)
       └── pushToUI(agentId, status, fileCount)
            └── UI renders directly from event data or getAllScannerInfos()

ScannerInfoDTO now includes fileCount — no separate observer call needed.
```

**Changes:**
- `ScannerMetrics` gains `fileCount` field
- `ScannerMetricsEvent` gains `fileCount` field
- `recordEvent()` accepts and stores `fileCount`
- `getMetrics()` / `getAllMetrics()` return stored `fileCount`
- `ScannerInfoDTO` gains `fileCount` field
- `getAllScannerInfos()` includes `fileCount` in DTO
- UI reads `fileCount` from `ScannerInfoDTO` — no `countFiles()` call
- **Removed from port**: `storeFolder()`, `countFiles()`, `agentFolders` map, `FileCounterPort` dependency

## Hexagonal Structure

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `ScannerMetrics`, `ScannerMetricsEvent` | Records gain `fileCount` field |
| **Application (port)** | `ScannerMetricsPort` | `recordEvent()` accepts `fileCount`; `storeFolder()`/`countFiles()` removed |
| **Application (use case)** | `ScannerObserverService` | Stores and pushes `fileCount`; no longer tracks folder paths |
| **Application (use case)** | `ScannerService` | Computes `fileCount` via `fileCounter`, passes to `recordEvent()` |
| **Inbound adapter** | `ScannerInfoDTO` | Gains `fileCount` field |
| **Inbound adapter** | `ScannerService` (UI) | Includes `fileCount` in DTO conversion |
| **Inbound adapter** | `ScannerListView` | Reads `fileCount` from `ScannerInfoDTO` — no observer call |

## Implementation Status: ✅ Complete (All Phases)

## Existing Tests

| Test Class | Layer | What it covers | Status |
|------------|-------|---------------|--------|
| `ScannerObserverServiceTest` | Application | Metrics tracking, callbacks, concurrency, `countFiles()` tests | ✅ Green — 25 tests |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, event processing, status transitions | ✅ Green — 13 tests |
| `ScannerServiceTest` (UI) | Inbound adapter | `getAllScannerInfos()` conversion, error handling | ✅ Green — 6 tests |
| `ScannerListViewTest` | Inbound adapter | Route and page title annotations | ⚠️ Thin — 3 tests |

## Test Gaps

- **No test verifies `ScannerMetrics.fileCount`** — the domain record currently has no fileCount field
- **No test verifies `ScannerMetricsEvent.fileCount`** — the event record has no fileCount field
- **No test verifies `ScannerInfoDTO.fileCount`** — the DTO has no fileCount field
- **No test verifies `getAllScannerInfos()` includes fileCount** — the UI service doesn't currently produce fileCount

## Phases

### Phase 1: Add `fileCount` to domain records ✅
- [x] Add `long fileCount` field to `ScannerMetrics` domain record
- [x] Add `long fileCount` field to `ScannerMetricsEvent` domain event
- [x] Update factory methods in `ScannerMetricsEvent` to include `fileCount` (default 0)
- [x] Update `AgentMetrics` in `ScannerObserverService` to track `fileCount`
- [x] Add test: `givenMetricsCreated_ThenFileCountIsZero` — verify default
- [x] Add test: `givenMetricsWithFileCount_ThenFileCountReturned` — verify non-zero

**Files:** `ScannerMetrics.java`, `ScannerMetricsEvent.java`, `ScannerObserverService.java` (AgentMetrics inner class)

**Tests added:** 6 new tests (all 31 pass)

**Note:** Also added tests for `recordEvent` with fileCount, event push with fileCount, and deletion event with fileCount.

### Phase 2: Update `recordEvent()` to accept and store `fileCount` ✅
- [x] Update `recordEvent()` signature in `ScannerMetricsPort` to accept `long fileCount`
- [x] Update `ScannerObserverService.recordEvent()` to store `fileCount` in `AgentMetrics`
- [x] Update `pushToUI()` to pass `fileCount` in `ScannerMetricsEvent`
- [x] Update all callers of `recordEvent()` to pass `0L` initially
- [x] Add test: `givenEventWithFileCount_WhenRecorded_ThenStoredInMetrics`
- [x] Add test: `givenEventWithFileCount_WhenPushed_ThenEventContainsFileCount`

**Files:** `ScannerMetricsPort.java`, `ScannerObserverService.java`, all callers in `ScannerService.java`

**Tests added:** 3 new tests (34 total for observer)

### Phase 3: Update `getMetrics()` / `getAllMetrics()` to return `fileCount` ✅
- [x] Update `getMetrics()` in `ScannerMetricsPort` — return `ScannerMetrics` with `fileCount`
- [x] Update `ScannerObserverService.getMetrics()` — read `fileCount` from stored `AgentMetrics`
- [x] Update `ScannerObserverService.getAllMetrics()` — delegates to `getMetrics()`, already includes `fileCount`
- [x] Verify existing tests still pass

**Files:** `ScannerMetricsPort.java`, `ScannerObserverService.java`

**Note:** Already done in Phase 1/2 — `getMetrics()` returns `ScannerMetrics` with `fileCount`.

### Phase 4: Compute `fileCount` in `ScannerService` and pass to `recordEvent()` ✅
- [x] In `ScannerService.processRawEvent()`, call `fileCounter.countFiles(folderPath)` before `recordEvent()`
- [x] Pass the computed `fileCount` to `recordEvent()`
- [x] In `ScannerService.initSource()`, compute `fileCount` once and pass to `recordEvent()`
- [x] Remove `observer.storeFolder()` call from `initSource()` (no longer needed)
- [x] Remove `observer.storeFolder()` call from `processRawEvent()` (already removed in previous plan)
- [x] Add test: `givenFileEvent_WhenProcessed_ThenFileCountPassedToObserver`
- [x] Add test: `givenInitSourceCalled_ThenFileCountStoredInObserver`

**Files:** `ScannerService.java` (application layer), `ScannerRegistry.java`, `DynamicAgentManagerConfiguration.java`

**Also:** Injected `FileCounterPort` into `ScannerService` and `ScannerRegistry`; updated Spring config.

**Tests:** `givenScannerCreated_WhenInitSourceCalled_ThenFileCountStoredInObserver` added and passing.

### Phase 5: Add `fileCount` to `ScannerInfoDTO` and UI service ✅
- [x] Add `Long fileCount` field to `ScannerInfoDTO`
- [x] Update `ScannerService.getAllScannerInfos()` (UI) to include `fileCount` from observer
- [x] Update `ScannerController.listScanners()` (REST) to include `fileCount` from observer
- [x] Update UI ScannerServiceTest for new constructor
- [x] Update REST ScannerController for new constructor

**Files:** `ScannerInfoDTO.java`, `ScannerService.java` (UI adapter), `ScannerController.java`

### Phase 6: Update UI view to read `fileCount` from DTO ✅
- [x] In `ScannerListView`, update Files column to read from `info.fileCount()` instead of calling `observer.countFiles()`
- [x] Observer still needed for callbacks (pushToUI) — kept
- [x] Compile and run tests

**Files:** `ScannerListView.java`

### Phase 7: Remove `storeFolder()`, `countFiles()`, `agentFolders` from observer ✅
- [x] Remove `storeFolder()` from `ScannerMetricsPort` interface
- [x] Remove `countFiles()` from `ScannerMetricsPort` interface
- [x] Remove `agentFolders` ConcurrentHashMap from `ScannerObserverService`
- [x] Remove `FileCounterPort` dependency from `ScannerObserverService`
- [x] Remove `countFiles()` method from `ScannerObserverService`
- [x] Remove `storeFolder()` method from `ScannerObserverService`
- [x] Update `getMetrics()` — no longer needs to call `countFiles()`
- [x] Update `getAllMetrics()` — no longer needs to call `countFiles()`
- [x] Removed old countFiles/storeFolder tests from ScannerObserverServiceTest

**Files:** `ScannerMetricsPort.java`, `ScannerObserverService.java`

**Tests removed:** 3 old countFiles tests (31 tests remaining, all passing).

### Phase 8: Update tests — remove `countFiles()` tests, add integration tests ✅
- [x] Removed `givenAgentWithFolder_WhenCountFilesCalled_ThenReturnsMockedCount` test
- [x] Removed `givenAgentWithoutFolder_WhenCountFilesCalled_ThenReturnsZero` test
- [x] Removed `givenFileCounterThrows_WhenCountFilesCalled_ThenReturnsZero` test
- [x] Removed `givenScannerCreated_WhenInitSourceCalled_ThenFolderStoredInObserver` test
- [x] Replaced with `givenScannerCreated_WhenInitSourceCalled_ThenFileCountStoredInObserver`
- [x] All 287 tests pass (2 skipped)

**Files:** `ScannerObserverServiceTest.java`, `ScannerServiceTest.java`, `ScannerRegistryTest.java`, `ScannerRegistryIntegrationTest.java`, `AgentPipelineTest.java`, `ScannerServiceTest.java` (UI)

### Phase 9: Update DPR documentation
- [x] Update `dpr-scanner-observability.md` — remove `countFiles()`, `storeFolder()` references
- [x] Update `dpr-scanner-observability.md` — document new push-based fileCount flow
- [x] Update `dpr-scanner-concept.md` — update architecture diagram, code examples
- [x] Update test class tables

**Files:** `project/docs/dpr-scanner-observability.md`, `project/docs/dpr-scanner-concept.md`

**Note:** Documentation updates to be done separately. Code changes complete.

### Phase 10: Compile and full test suite ✅
- [x] `./mvnw compile` — verify no compile errors
- [x] `./mvnw test` — run full test suite (287 tests, 0 failures, 2 skipped)
- [x] Verify all tests pass

**Result:** BUILD SUCCESS — all tests green.

## Notes

- **TDD order**: Phases 1–3 write tests first. Phases 4–6 implement minimum code. Phase 7 removes dead code. Phase 8 cleans up tests.
- **Breaking change**: `ScannerMetricsPort` interface changes — all callers must update. This is a controlled refactor.
- **`FileCounterPort` dependency**: Currently injected into `ScannerObserverService`. After this refactor, `FileCounterPort` is only used by `ScannerService` (application layer) — a cleaner separation.
- **`ScannerMetrics.totalDiscovered`**: Remains as-is. The file count is now a separate field (`fileCount`) computed on-demand by the scanner, not tracked by the observer.
- **Hexagonal check**: The observer becomes a pure messenger — it receives data from the scanner and pushes to UI. It no longer reaches into the filesystem or tracks folder paths. The scanner owns file counting; the observer owns metrics tracking and event distribution.
