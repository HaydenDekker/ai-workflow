# Scanner Status Rework Plan

> **Date:** 2026-04-29
> **Status:** Draft
> **Scope:** Scanner status lifecycle, emission timing, ERROR state

---

## 1. Problem Statement

The current scanner status system is static and never reflects the live state of the file watcher:

- **IDLE** — never set by any code path
- **EMITTING_ALL** — set on creation, never transitions unless `refreshAgent()` is called
- **EMITTING_UPDATES** — only set when an agent subscribes to the scanner's flux (not when events actually occur)
- **ERROR** — documented but never set

The status is a pull-only field that captures lifecycle events, not runtime behaviour. There is no mechanism to detect idle periods or to surface errors from the file watcher.

---

## 2. Target Status Lifecycle

```
                    ┌─────────────────────┐
                    │    EMITTING_INITIAL │  Scanner starts, scanning existing files
                    └────────┬────────────┘
                             │ all files emitted
                             ▼
              ┌──────────────────────────┐
              │   EMITTING_UPDATES       │  Event detected: CREATE, MODIFY, or DELETE
              └────────┬─────────────────┘
                       │ no event for 30s
                       ▼
              ┌──────────────────────────┐
              │         IDLE             │  Watching, waiting for events
              └────────┬─────────────────┘
                       │ event detected
                       └──────────────────┘ (back to EMITTING_UPDATES)

  Any state ──► ERROR (on watcher failure) ──► recovery or manual reset
```

### 2.1 Status Definitions

| Status | Trigger | Timeout |
|--------|---------|---------|
| **EMITTING_INITIAL** | Scanner created, initial full scan of existing files in progress | Transitions to EMITTING_UPDATES when full scan completes |
| **EMITTING_UPDATES** | File system event detected (CREATE, MODIFY, DELETE) | Transitions to IDLE after 30s of no events |
| **IDLE** | No file system event for 30 seconds | Transitions to EMITTING_UPDATES on next event |
| **ERROR** | WatchService throws an exception, directory becomes inaccessible, or scanner throws | Manual recovery required (refresh or recreate) |

---

## 3. Emission Delay Configuration

### 3.1 Problem

`NativeFileWatcher` emits files synchronously in a tight loop during full scans. During incremental watching, events are emitted immediately (with a 100ms write-delay). There is no throttle between emissions, meaning the LLM pipeline could be flooded with file events.

### 3.2 Solution

Introduce a configurable **emission delay** — a minimum interval between consecutive file emissions. This gives the downstream LLM pipeline time to process each file before the next one arrives.

- **Config property:** `ai-scanner.emission-delay-seconds`
- **Default:** `20`
- **Source:** `application.yml` → bound to a `@ConfigurationProperties` class
- **Applied in:** `FileSystemScannerAdapter` constructor, passed through to `NativeFileWatcher`

### 3.3 Implementation

Add a `Duration` parameter to `NativeFileWatcher` (or a new `EmissionThrottle` class) that tracks the last emission time and sleeps/buffers if the delay has not elapsed. Events that arrive during the delay window are coalesced — the next emission uses the most recent file state.

```
Event arrives → record as latest → if delay elapsed since last emission → emit → record time
                                    │
                                    └── delay not elapsed → wait until delay → emit
```

---

## 4. Implementation Plan

### 4.1 Files to Modify

| File | Change |
|------|--------|
| `ScannerInfo.java` (DTO) | Rename `EMITTING_ALL` → `EMITTING_INITIAL` in Javadoc |
| `ScannerRegistry.java` | Replace all `"EMITTING_ALL"` literals with `"EMITTING_INITIAL"`; add idle timer management; add ERROR state handling |
| `NativeFileWatcher.java` | Add emission delay/throttle; add ERROR reporting callback; track last emission time |
| `FileSystemScannerAdapter.java` | Pass emission delay to `NativeFileWatcher`; propagate ERROR state back to `ScannerRegistry` |
| `ScannerObserverUseCase.java` | Add `lastEmissionTimestamp` per agent; add idle detection method |
| `application.yml` | Add `ai-scanner.emission-delay-seconds` config |
| New: `EmissionDelayConfig.java` | `@ConfigurationProperties` for scanner config |
| New: `EmissionThrottle.java` (optional) | Stateless utility to enforce min interval between emissions |

### 4.2 Step-by-Step Changes

#### Step 1: Add configuration properties

Create `EmissionDelayConfig.java`:

```java
@ConfigurationProperties(prefix = "ai-scanner")
public record EmissionDelayConfig(int emissionDelaySeconds) {
    public static final int DEFAULT_DELAY_SECONDS = 20;
    public EmissionDelayConfig {
        if (emissionDelaySeconds <= 0) emissionDelaySeconds = DEFAULT_DELAY_SECONDS;
    }
}
```

Register in `application.yml`:

```yaml
ai-scanner:
  emission-delay-seconds: 20
```

#### Step 2: Update `ScannerInfo` Javadoc

Rename `EMITTING_ALL` to `EMITTING_INITIAL` in the Javadoc. No DTO structural change — the string values are user-facing but only rendered in the UI.

#### Step 3: Update `ScannerRegistry`

- Replace `"EMITTING_ALL"` → `"EMITTING_INITIAL"` in `createForAgent()` and `refreshAgent()`.
- Add an `IdleTimer` inner class (or use `ScheduledExecutorService` per scanner) that:
  - On creation of a `ScannerMetadata`, starts a 30s countdown.
  - Resets the countdown on every `recordDiscovery()`, `recordUnchanged()`, or `updateFileCount()` callback.
  - When countdown reaches zero, calls `updateStatus(agentId, "IDLE")`.
- Add `transitionToError(String agentId, String reason)` method:
  - Sets status to `"ERROR"`.
  - Logs the reason.
  - Stores the error message in `ScannerMetadata`.
- Add `recoverFromError(String agentId)` method:
  - Resets status to `"EMITTING_INITIAL"`.
  - Calls `scanner.resetToFullScan()`.
  - Restarts the idle timer.

#### Step 4: Update `NativeFileWatcher`

- Add `Duration emissionDelay` field.
- Add `volatile LocalDateTime lastEmissionTime` field.
- In `emitFile()` and `scanAllFiles()`, before `sink.tryEmitNext(history)`:
  ```java
  if (Duration.between(lastEmissionTime, LocalDateTime.now()).toSeconds() < emissionDelay.toSeconds()) {
      // Throttle: buffer the latest history, wait or skip
      latestBufferedHistory = history;  // coalesce
      return;
  }
  sink.tryEmitNext(history);
  lastEmissionTime = LocalDateTime.now();
  ```
- Add `Consumer<String> onError` callback (takes agentId or directory path + error message).
- Wrap `processEvent()` and `scanAllFiles()` in try-catch, calling `onError` on exception.

#### Step 5: Update `FileSystemScannerAdapter`

- Accept `EmissionDelayConfig` as constructor parameter.
- Pass `emissionDelay` to `NativeFileWatcher` constructor.
- Register an error handler that calls `ScannerRegistry.transitionToError(agentId, message)`.

#### Step 6: Update `ScannerObserverUseCase`

- Add `volatile LocalDateTime lastEmissionTime` per agent (in `AgentMetrics` record or as a separate map).
- Update `recordDiscovery()` and `recordUnchanged()` to set `lastEmissionTime = now()`.
- Add `isIdle(String agentId)` method:
  ```java
  public boolean isIdle(String agentId) {
      LocalDateTime last = getLastEmissionTime(agentId);
      if (last == null) return true;
      return Duration.between(last, LocalDateTime.now()).toSeconds() >= 30;
  }
  ```

#### Step 7: Wire everything together

- `ScannerRegistry` constructor accepts `EmissionDelayConfig`.
- `ScannerRegistry` creates `FileSystemScannerAdapter` with the delay config.
- `FileSystemScannerAdapter` passes delay to `NativeFileWatcher`.
- Error callbacks flow: `NativeFileWatcher` → `FileSystemScannerAdapter` → `ScannerRegistry.transitionToError()`.
- Idle timer: `ScannerRegistry` starts a `ScheduledExecutorService` that checks `ScannerObserverUseCase.isIdle()` every 10 seconds for each scanner. If idle and status is `EMITTING_UPDATES`, transition to `IDLE`.

#### Step 8: Add ERROR state to `ScannerListView`

- The UI already handles `"ERROR"` status with a red dot (switch case already exists).
- Optionally display the error message in a tooltip on the status indicator.

---

## 5. Testing Plan

### 5.1 Unit Tests

| Test | Class |
|------|-------|
| Emission delay throttles rapid events | `NativeFileWatcherEmissionDelayTest` (new) |
| Idle timer transitions EMITTING_UPDATES → IDLE after 30s | `ScannerRegistryIdleTest` (new) |
| Scanner transitions to ERROR on file system exception | `ScannerRegistryErrorTest` (new) |
| Scanner recovers from ERROR on refresh | `ScannerRegistryErrorRecoveryTest` (new) |
| Coalescing: late events don't produce duplicate emissions | `EmissionThrottleTest` (new) |
| Existing: `ScannerRegistryTest` — update expected status from `EMITTING_ALL` to `EMITTING_INITIAL` |
| Existing: `ScannerRegistryIntegrationTest` — same status update |

### 5.2 Integration Tests

- Verify end-to-end: create agent → status goes EMITTING_INITIAL → EMITTING_UPDATES (file created) → IDLE (wait 30s) → EMITTING_UPDATES (file created again).
- Verify: delete file from watched directory → status EMITTING_UPDATES → IDLE.
- Verify: make directory unreadable → status ERROR → refresh → status EMITTING_INITIAL.

### 5.3 UI Tests

- `ScannerListViewTest` — verify status colors for all four states.
- E2E test: create agent, wait, verify grid shows correct status transitions.

---

## 6. Backwards Compatibility

- **DTO change:** `EMITTING_ALL` → `EMITTING_INITIAL` is a string value change. Existing database entries (if any persist scanner status) would show the old value. Since scanner status is in-memory (`ConcurrentHashMap`), this is not a persistence concern.
- **Config change:** New `ai-scanner.emission-delay-seconds` property with default `20`. No existing config references this prefix.
- **API change:** None. REST endpoints return the same `ScannerInfo` DTO shape.

---

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Emission delay slows down bulk file ingestion | Configurable per-scanner delay; can be set to `0` for fast-scanning agents |
| Idle timer adds overhead per scanner | Single shared `ScheduledExecutorService` checks all scanners every 10s (not one per scanner) |
| ERROR state recovery could re-emit files | `resetToFullScan()` already handles this — files are hash-computed, duplicates are skipped |
| UI shows stale status during transition | Auto-refresh (30s) + real-time push events cover both fast and slow UI updates |

---

## 8. Implementation Order

1. **Config** — `EmissionDelayConfig` + `application.yml` entry (non-breaking, no behaviour change)
2. **Emission throttle** — `NativeFileWatcher` delay enforcement (no status change, isolated unit)
3. **Status string rename** — `EMITTING_ALL` → `EMITTING_INITIAL` across all files
4. **Idle timer** — `ScannerRegistry` scheduled check + `ScannerObserverUseCase.isIdle()`
5. **ERROR state** — `ScannerRegistry.transitionToError()` + `NativeFileWatcher` error callback
6. **UI updates** — ERROR tooltip, verify all four status colors
7. **Tests** — new + updated existing tests
8. **Documentation** — update AGENTS.md, any API docs
