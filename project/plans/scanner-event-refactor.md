# Scanner Event Refactor

## Goal

Simplify `ScannerObserverUseCase` to a single input method `recordScannerEvent(ScannerMetricsChangedEvent)` and have `ScannerMetricsChangedEvent` carry both `ScannerStatus` and `ScannerEventType` as strongly-typed fields.

## Design

`ScannerMetricsChangedEvent` carries both `ScannerStatus` (for UI state) and `ScannerEventType` (for metrics logic). The `ScannerEventType` is **nullable** — populated for file events (CREATION, MODIFICATION, DELETION, UNCHANGED), null for lifecycle events (emission, error, recovery).

**Observer dispatch logic** on the single `recordScannerEvent(event)`:
- `eventType == CREATION || MODIFICATION` → increment discovered
- `eventType == DELETION || UNCHANGED` → no discovered increment
- `eventType == null` (emission, error, recovery) → no discovered increment, but still update timestamp / push to UI

### Scanner → (ScannerStatus, ScannerEventType) mapping

| Scanner call site | Status | EventType |
|---|---|---|
| `recordScannerEvent(CREATION)` | `EMITTING_UPDATES` | `CREATION` |
| `recordScannerEvent(MODIFICATION)` | `EMITTING_UPDATES` | `MODIFICATION` |
| `recordScannerEvent(DELETION)` | `EMITTING_UPDATES` | `DELETION` |
| `recordScannerEvent(UNCHANGED)` | `FILTERED` | `UNCHANGED` |
| `recordEmission()` | `EMITTING_UPDATES` | `null` |
| `recordError()` | `ERROR` | `null` |
| `recordRecovery()` | `IDLE` | `null` |
| `notifyStatusChange()` | *(from context)* | `null` — pushes directly via `pushToUI`, bypassing metrics |

---

## Step 1 — Restructure `ScannerMetricsChangedEvent`

**File:** `ui/events/ScannerMetricsChangedEvent.java`

- Add `private final ScannerStatus status` (required)
- Add `private final ScannerEventType eventType` (nullable — present for file events)
- Add `private final String folderPath` (nullable — present for file events)
- Keep `agentId` and `errorMessage`
- Keep `getType()` for backward compat (delegates to `eventType != null ? eventType.name().toLowerCase() : status.name().toLowerCase()`)
- Add `getStatus()` returning `ScannerStatus`
- Add `getEventType()` returning `ScannerEventType`

**Remove all factory methods:** `fileDiscovered`, `fileUnchanged`, `fileCountUpdated`, `fileDeleted`, `fileEmitted`, `errorOccurred`, `recoveredFromError`, `idleReached`, `statusChanged`, `scannerMetricsChanged`, `scannerEvent(ScannerEventType, String)`

**Replace constructors** with a single constructor:

```java
public ScannerMetricsChangedEvent(String agentId, ScannerStatus status, ScannerEventType eventType, String folderPath, String errorMessage)
```

Callers construct events directly, e.g.:
```java
new ScannerMetricsChangedEvent(agentId, ScannerStatus.EMITTING_UPDATES, ScannerEventType.CREATION, folderPath, null)
```

---

## Step 2 — Collapse `ScannerObserverUseCase`

**File:** `usecases/ScannerObserverUseCase.java`

Replace 6 public `record*` methods with one:

```java
public void recordScannerEvent(ScannerMetricsChangedEvent event)
```

**Internal dispatch on `event.getEventType()`:**
- `CREATION` / `MODIFICATION` → store folder, increment discovered, update emission timestamp, push to UI
- `DELETION` / `UNCHANGED` → store folder, push to UI
- `null` (emission, error, recovery) → update emission timestamp if `status == EMITTING_UPDATES`, push to UI

**Remove:** `recordEmission`, `recordStatusChange`, `recordError`, `recordRecovery`, `recordIdle`

**Keep** `pushToUI` public (used by `notifyStatusChange` for direct status push).

**Keep all query methods unchanged:** `getMetrics`, `getAllMetrics`, `isIdle`, `countFiles`, `getLastEmissionTimestamp`, callback registration.

---

## Step 3 — Update `Scanner`

**File:** `usecases/Scanner.java`

Replace all 13 observer call sites:

| Current | Replacement |
|---|---|
| `observer.recordScannerEvent(CREATION, id, folder)` | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, CREATION, folder, null))` |
| `observer.recordScannerEvent(MODIFICATION, id, folder)` | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, MODIFICATION, folder, null))` |
| `observer.recordScannerEvent(DELETION, id, folder)` | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, DELETION, folder, null))` |
| `observer.recordScannerEvent(UNCHANGED, id, folder)` | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, FILTERED, UNCHANGED, folder, null))` |
| `observer.recordEmission(id)` (in `onEmitCallback`) | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, null, null, null))` |
| `observer.recordError(id, msg)` (6 sites) | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, ERROR, null, null, msg))` |
| `observer.recordRecovery(id)` | `observer.recordScannerEvent(new ScannerMetricsChangedEvent(id, IDLE, null, null, null))` |
| `observer.recordStatusChange(id, newStatus)` (in `notifyStatusChange`) | `observer.pushToUI(new ScannerMetricsChangedEvent(id, newStatus, null, null, null))` |

Update `Scanner.recordEmission()` (public, line 385) to call `onEmitCallback()` or emit the same event.

Remove the local `ScannerEventType eventType` variable in `processRawEvent` — pass enum directly.

---

## Step 4 — Update `ScannerRegistry`

**File:** `app/pipeline/management/ScannerRegistry.java`

Line 245: replace `observer.recordStatusChange(scannerId, status)` with `observer.pushToUI(ScannerMetricsChangedEvent.statusChanged(scannerId, status))`

---

## Step 5 — Keep `ScannerEventType.java`

**File:** `usecases/ScannerEventType.java`

No changes — reused as-is.

---

## Step 6 — Update tests

### `ScannerObserverUseCaseTest.java` (~25 tests)

Rewrite to use constructor directly:
- CREATION/MODIFICATION → `new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, CREATION, folder, null)` → assert discovered increments
- DELETION → `new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, DELETION, folder, null)` → assert discovered stays 0
- UNCHANGED → `new ScannerMetricsChangedEvent(id, FILTERED, UNCHANGED, folder, null)` → assert discovered stays 0
- Emission → `new ScannerMetricsChangedEvent(id, EMITTING_UPDATES, null, null, null)` → assert emission timestamp updates, discovered unchanged
- `getType()` assertions → adapt to new return values (event type name when present, status name when null)
- Callback tests → simply verify the observer callback is triggered; do not count events (that validation is handled via the `getMetrics` interface)
- Concurrency, idle tests → adapt to constructor

### `ScannerMetricsTest.java`

Integration test — minimal changes. Behavior: DELETION now emits an event (but still doesn't increment discovered, consistent with before). Emission now goes through `recordScannerEvent` (no discovered increment since eventType is null, consistent with before).

---

## Files Touched

| File | Action |
|---|---|
| `ui/events/ScannerMetricsChangedEvent.java` | Add `status`, `eventType`, `folderPath` fields; single constructor; remove all factories; keep `getType()` compat |
| `usecases/ScannerObserverUseCase.java` | 6 methods → 1 `recordScannerEvent(event)` |
| `usecases/Scanner.java` | Replace all 13 observer calls |
| `usecases/ScannerEventType.java` | **No change** |
| `app/pipeline/management/ScannerRegistry.java` | Replace `recordStatusChange` with `pushToUI` |
| `test/.../ScannerObserverUseCaseTest.java` | Rewrite for new API |
| `test/.../ScannerMetricsTest.java` | Minor adjustments |

---

## Execution Order

1. Restructure `ScannerMetricsChangedEvent` (Step 1) — build should pass (old factories still used, new factories available)
2. Collapse `ScannerObserverUseCase` (Step 2) — build will fail until callers are updated
3. Update `Scanner` (Step 3) and `ScannerRegistry` (Step 4) together — build should pass
4. Update tests (Step 6) — run `./mvnw test` to verify
