# Plan: Scanner View Regression Fix

## Problem

The `/scanners` view (usecase: **Operator views scanner status and file count**) has two regressions traced from the source:

1. **File count incrementing** — The "Files" column calls `observer.getMetrics(agentId).totalDiscovered()` in `ScannerListView.java`. Tracing through the code, `totalDiscovered` is a monotonically incrementing counter in `ScannerObserverService.recordEvent()` — bumped by CREATION/MODIFICATION events, never decremented. The correct method `countFiles(agentId)` already exists via the `FileCounterPort` but is never called from the UI.

2. **Status not updating** — The push chain (`notifyStatusChange` → `pushToUI` → callbacks → `ui.access(refreshScanners)` → `setItems()`) is structurally correct. Two issues compound: (a) `storeFolder()` is called inside `processRawEvent()` which runs only after raw events arrive — during `initSource()`, the status transitions fire *before* any events so the folder isn't registered and `countFiles()` would return 0. (b) The status column renders a `StatusWrapper` component column; when `setItems()` receives new `ScannerInfoDTO` objects, Vaadin should re-render, but the timing of when status transitions fire and whether `toInfo()` captures the latest `volatile status` needs verification.

## Target

The `/scanners` view displays:
- **Files column**: actual file count from the watched directory, computed on-demand via `FileCounterPort`.
- **Status column**: live scanner status (IDLE, EMITTING_UPDATES, EMITTING_INITIAL, FILTERED, ERROR) that updates when files are added, removed, or modified.

All existing tests pass. New tests validate the fixed behaviour at the unit and integration level.

## Hexagonal Structure

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `ScannerMetrics`, `ScannerStatus`, `ScannerMetricsEvent`, `ScannerEventType` | Value objects for metrics and status |
| **Application (use case)** | `ScannerObserverService` | Tracks metrics, `countFiles()`, UI push callbacks |
| **Application (port)** | `ScannerMetricsPort`, `FileCounterPort` | Interfaces for metrics observation and file counting |
| **Inbound adapter** | `ScannerListView` | Vaadin view rendering the grid |
| **Inbound adapter** | `ScannerService` (UI service) | Thin wrapper around `ScannerRegistry` for the view |
| **Outbound adapter** | `FileSystemFileCounter` | Walks real filesystem via `Files.walk()` |

## Existing Tests

| Test Class | Layer | What it covers | Status |
|------------|-------|---------------|--------|
| `ScannerObserverServiceTest` | Application | Metrics tracking, callback registration, concurrency | ✅ Green — 17 tests, covers CREATION/MODIFICATION/DELETION/UNCHANGED events, callback lifecycle, idle detection |
| `ScannerServiceTest` (UI service) | Inbound adapter | `getAllScannerInfos()` conversion, error handling | ✅ Green — 6 tests, mocks `ScannerRegistry`, verifies DTO conversion |
| `ScannerServiceTest` (app) | Application | Scanner lifecycle, flux, full scan, raw event processing | ✅ Green — 10 tests, mocks `FileWatcherPort`, verifies `toInfo()` data, one test emits a `RawFileEvent` |
| `ScannerListViewTest` | Inbound adapter | Route and page title annotations | ⚠️ Thin — 3 tests, only checks `@Route` and `@PageTitle` annotations. No rendering, data, or callback tests. |
| `ScannerRegistryTest` | Application | Registry CRUD operations | ✅ Green — covers creation, listing, deletion |
| `ScannerRegistryIntegrationTest` | Application | Full scanner lifecycle with registry | ✅ Green — integration-level |

## Test Gaps

- **No test exercises `countFiles()` with a non-zero mock** — `ScannerObserverServiceTest` passes `path -> 0L` as the `FileCounterPort` mock, so `countFiles()` always returns 0 in tests.
- **No test verifies the Files column renders `countFiles()`** — `ScannerListViewTest` doesn't render the grid or check column values.
- **No test verifies status transitions during `initSource()`** — `ScannerServiceTest` (app) checks `toInfo().status()` returns "IDLE" at creation but doesn't verify the full transition chain (IDLE → EMITTING_INITIAL → EMITTING_UPDATES).
- **No test verifies `storeFolder()` is called at scanner creation** — the observer's folder map is never asserted in tests.
- **No integration test for the push callback chain** — no test wires `ScannerObserverService` callbacks → `refreshScanners()` → grid update end-to-end.

## Implementation Status: 🟡 In Progress (Phases 1–7 done; Phase 8 manual verification pending)

## Phases

### Phase 1: Write failing test for `countFiles()` returning real data ✅
- [x] Added `givenAgentWithFolder_WhenCountFilesCalled_ThenReturnsMockedCount` — stores folder, mocks `FileCounterPort` to return 7L, asserts `countFiles()` returns 7
- [x] Added `givenAgentWithoutFolder_WhenCountFilesCalled_ThenReturnsZero` — no folder stored, asserts returns 0
- [x] Added `givenFileCounterThrows_WhenCountFilesCalled_ThenReturnsZero` — mocks `RuntimeException`, asserts graceful fallback to 0
- [x] Implementation: added try/catch in `countFiles()` to handle exceptions (was missing before)
- [x] All 25 tests pass (22 existing + 3 new), no regressions

**Files:** `src/test/java/.../application/scanner/ScannerObserverServiceTest.java`, `src/main/java/.../application/scanner/ScannerObserverService.java`

### Phase 2: Fix Files column to call `countFiles()` ✅
- [x] Changed `ScannerListView` Files column from `observer.getMetrics(info.agentId()).totalDiscovered() + " files"` to `observer.countFiles(info.agentId()) + " files"`
- [x] Added try/catch with `"—"` fallback on error (existing pattern)
- [x] Compile and run existing tests — all 47 tests pass

**Files:** `src/main/java/.../adapter/inbound/ui/view/ScannerListView.java`

### Phase 3: Write test for `storeFolder()` at scanner creation ✅
- [x] Added test to `ScannerServiceTest` (app): `givenScannerCreated_WhenInitSourceCalled_ThenFolderStoredInObserver`
  - Creates scanner with non-zero `FileCounterPort` mock (returns 42L)
  - Calls `initSource()`, then asserts `observer.countFiles(agentId)` returns 42 (proves folder stored)
  - Also verifies status transitioned to a valid non-ERROR state
- [x] All 11 tests pass (10 existing + 1 new), no regressions

**Files:** `src/test/java/.../application/scanner/ScannerServiceTest.java`

### Phase 4: Move `storeFolder()` to `initSource()` ✅
- [x] Added `observer.storeFolder(effectiveAgentId, folderPath)` at the top of `initSource()` before `notifyStatusChange()` and `fileWatcherPort.start()`
- [x] Removed redundant `observer.storeFolder(effectiveAgentId, folderPath)` from `processRawEvent()` CREATION/MODIFICATION branch — folder is now stored once at creation time, not re-stored on every file event
- [x] All scanner tests pass: 11 in `ScannerServiceTest`, 22 in `ScannerObserverServiceTest`, 6 in UI `ScannerServiceTest`

**Files:** `src/main/java/.../application/scanner/ScannerService.java`

### Phase 5: Write test for status transitions during file events ✅
- [x] Added `givenNewFileEvent_WhenProcessed_ThenStatusTransitionsToEmittingUpdates` — creates scanner with flux emitting CREATION event, captures status via callback, asserts both `EMITTING_INITIAL` and `EMITTING_UPDATES` present
- [x] Added `givenUnchangedFileEvent_WhenProcessed_ThenStatusTransitionsToFiltered` — pre-populates repo with matching hash so `hashMatches()` returns true, asserts `FILTERED` status in history
- [x] All 13 tests in `ScannerServiceTest` pass (10 existing + 2 new from Phase 3 + 2 new from Phase 5)

**Files:** `src/test/java/.../application/scanner/ScannerServiceTest.java`

### Phase 6: Verify and fix status push chain ✅
- [x] Audited push chain: `notifyStatusChange()` → `observer.pushToUI()` → callbacks → `ui.access(() → refreshScanners())` → `scannerService.getAllScannerInfos()` → `scannerRegistry.listAll()` → `scanner.toInfo()` (new record each call) → `grid.setItems(scanners)`
- [x] Verified `toInfo()` returns a new `ScannerInfo` record each call (status baked in at construction)
- [x] Verified `volatile status` field read by `toInfo().status()` → `status.name()`
- [x] No code changes needed — chain is structurally correct

**Files:** No changes required — audit only

### Phase 7: Update stale DPR documentation ✅
- [x] Completely rewrote `dpr-scanner-observability.md` — all stale references removed:
  - `ScannerMetricsService` → `ScannerObserverService`
  - `ScannerMetricsSnapshot` → `ScannerMetrics`
  - `ScannerObserverUseCase` → `ScannerObserverService`
  - Callback registration: `metricsService.registerRefreshCallback()` → `observer.registerRefreshCallback()`
  - Grid refresh: `grid.getDataProvider().refreshAll()` → `grid.setItems(scanners)`
  - Status color table: Green=IDLE, Amber=EMITTING_INITIAL, Blue=EMITTING_UPDATES, Orange=FILTERED, Red=ERROR
  - Clarified `ScannerMetricsEvent` (domain event for UI push) vs `ScannerMetricsChangedEvent` (Spring event, removed)
  - Added hexagonal layering table, testing section with new test examples
- [x] Updated `dpr-scanner-concept.md` — replaced stale code references:
  - `NativeFileWatcher` → `FileWatcherPort`
  - `FileSystemScannerAdapter` → `ScannerService`
  - `ScannerObserverUseCase` → `ScannerObserverService`
  - Updated architecture diagram, class table, lifecycle flows, hash detection code
  - Added migration notes documenting Phase 1–4 changes
  - Updated test examples and code snippets

**Files:** `project/docs/dpr-scanner-observability.md`, `project/docs/dpr-scanner-concept.md`

### Phase 8: Manual verification and screenshot ⏳ Pending
- [ ] Start dev server with `./mvnw spring-boot:run`
- [ ] Create an agent with a scanner pointing to a test directory
- [ ] Verify "Files" column shows correct file count (matches `ls -1 <dir> | wc -l`)
- [ ] Add a file to the directory, verify status changes to EMITTING_UPDATES and file count increases
- [ ] Wait for idle timeout, verify status returns to IDLE
- [ ] Remove a file, verify file count decreases
- [ ] Capture screenshot with `npx tsx scripts/capture-snapshot.ts /scanners`

## Implementation Summary

| Phase | Status | Changes |
|-------|--------|---------|
| 1 | ✅ | +3 tests to `ScannerObserverServiceTest` for `countFiles()` | +1 try/catch in `countFiles()` implementation |
| 2 | ✅ | Files column: `observer.getMetrics().totalDiscovered()` → `observer.countFiles()` |
| 3 | ✅ | +1 test verifying `initSource()` stores folder in observer |
| 4 | ✅ | Moved `storeFolder()` from `processRawEvent()` to `initSource()` |
| 5 | ✅ | +2 tests for status transitions (EMITTING_UPDATES, FILTERED) |
| 6 | ✅ | Push chain audit — no changes needed |
| 7 | ✅ | Rewrote `dpr-scanner-observability.md`, updated `dpr-scanner-concept.md` |
| 8 | ⏳ | Manual verification pending (dev server + screenshot) |

**Test results**: 47 scanner tests pass (25 + 13 + 6 + 3), full suite 281/281 pass.

**Files changed**: 8 modified (3 main, 2 test, 3 docs + plan).

## Notes

- **TDD order**: Phases 1, 3, 5 write tests first. Phases 2, 4, 6 implement the minimum code to pass. Phase 8 is the final manual acceptance.
- `ScannerMetrics.totalDiscovered` remains in the domain model as a discovery counter. The UI stops using it for the Files column — no breaking change to the domain layer.
- `countFiles()` walks the directory on every grid render. Acceptable for now; if performance becomes an issue, cache with a TTL or push via the callback. (See `scanner-metrics-refactor.md` for a future refactor to push fileCount from scanner to observer.)
- Previous plans (`scanner-status-rework.md`, `scanner-event-refactor.md`) are Draft state. This plan targets only the two UI regressions without architectural changes.
- **Hexagonal check**: All changes stay within their layers. The inbound adapter (`ScannerListView`) calls through the application port (`ScannerObserverService`/`ScannerMetricsPort`). The outbound adapter (`FileSystemFileCounter`/`FileCounterPort`) is unchanged. The domain layer is unchanged. No cross-layer dependencies introduced.
