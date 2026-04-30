# Scanner Refactor Plan

**Goal**: Make `Scanner` the domain concept. Move business rules (FileHash, FileHistory) out of infrastructure. Reduce `ScannerRegistry` to a collection. Clarify adapter naming.

---

## Domain Model (After)

| Concept | Class | Package | Role |
|---------|-------|---------|------|
| **Scanner** | `Scanner` | `usecases/` | Domain object — owns status, idle timer, error handling, metrics publishing, DTO conversion, and the FileHash/FileHistory business rules. Composes `NativeFileWatcherAdapter`. |
| **NativeFileWatcherAdapter** | `NativeFileWatcherAdapter` | `files/` | Infrastructure adapter — NIO WatchService loop. Emits raw file events. No business rules. |
| **ScannerRegistry** | `ScannerRegistry` | `app/pipeline/management/` | Collection + orchestration — stores `Scanner` by agentId, delegates lifecycle to `Scanner`. |
| **FileSystemScannerAdapterFactory** | `FileSystemScannerAdapterFactory` | `files/` | Factory — creates `NativeFileWatcherAdapter` instances with correct dependencies. Injected into `Scanner`. |

---

## What Moves Where

| Concern | Today (location) | After (location) |
|---------|-----------------|-----------------|
| Status state machine (IDLE/EMITTING/ERROR/FILTERED) | `ScannerRegistry` + `FileSystemScannerAdapter` (duplicated) | `Scanner` (single source of truth) |
| Idle detection (scheduler, timeout, transition) | `ScannerRegistry` | `Scanner` |
| Error handling (transitionToError, recover) | `ScannerRegistry` | `Scanner` |
| Emission tracking (lastEmittedAt, idle timer reset) | `ScannerRegistry` + `FileSystemScannerAdapter` | `Scanner` |
| Metrics event publishing (pushMetricsEvent) | `ScannerRegistry` | `Scanner` |
| DTO conversion (toScannerInfo) | `ScannerRegistry` | `Scanner` |
| FileHash check + FileHistory creation (business rules) | `NativeFileWatcher` + `FileComparator` | `Scanner` |
| NIO WatchService loop | `NativeFileWatcher` | `NativeFileWatcherAdapter` |
| Raw file event emission (CREATE/MODIFY/DELETE) | `NativeFileWatcher` | `NativeFileWatcherAdapter` |
| Scanner collection (ConcurrentHashMap) | `ScannerRegistry` | `ScannerRegistry` (stays) |
| Scanner CRUD (create, destroy, refresh, list) | `ScannerRegistry` | `ScannerRegistry` → delegates to `Scanner` |

---

## Phase 1: Rename

**Objective**: Rename `FileSystemScannerAdapter` → `Scanner`, `NativeFileWatcher` → `NativeFileWatcherAdapter`. No logic changes. All existing tests pass.

### Step 1.1: Rename `NativeFileWatcher` → `NativeFileWatcherAdapter`

**File**: `src/main/java/com/hdekker/ai_workflow/files/NativeFileWatcher.java`
**Action**: Rename file and class. Update all references.

**Affected source files** (import + usage):
- `src/main/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapter.java` — imports and instantiates `NativeFileWatcher`

**Affected test files** (import + usage):
- `src/test/java/com/hdekker/ai_workflow/files/NativeFileWatcherMetricsTest.java` — imports and instantiates `NativeFileWatcher`

### Step 1.2: Rename `FileSystemScannerAdapter` → `Scanner`

**File**: `src/main/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapter.java`
**Action**: Rename file and class. Move to `usecases/` package. Update all references.

**New location**: `src/main/java/com/hdekker/ai_workflow/usecases/Scanner.java`

**Affected source files** (import + usage):
- `src/main/java/com/hdekker/ai_workflow/app/pipeline/management/ScannerRegistry.java` — imports, instantiates, stores in map
- `src/main/java/com/hdekker/ai_workflow/pipeline/management/DynamicAgentManagerConfiguration.java` — no direct reference (uses ScannerRegistry)

**Affected test files** (import + usage):
- `src/test/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapterTest.java` → rename to `ScannerTest.java` in `usecases/`
- `src/test/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapterFilteredStatusTest.java` → rename to `ScannerFilteredStatusTest.java` in `usecases/`
- `src/test/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapterMetricsTest.java` → rename to `ScannerMetricsTest.java` in `usecases/`
- `src/test/java/com/hdekker/ai_workflow/app/pipeline/AgentPipelineTest.java` — imports and instantiates `FileSystemScannerAdapter`

### Phase 1 Tests

Run after each rename step. All must pass with **zero test changes** (only import/package updates).

```bash
# After step 1.1 (NativeFileWatcher → NativeFileWatcherAdapter)
./mvnw test -Dtest=NativeFileWatcherMetricsTest -q

# After step 1.2 (FileSystemScannerAdapter → Scanner, moved to usecases/)
./mvnw test -Dtest=ScannerTest,ScannerFilteredStatusTest,ScannerMetricsTest,AgentPipelineTest -q

# Full suite to catch any missed references
./mvnw test -q
```

---

## Phase 2: Move Functions from ScannerRegistry to Scanner

**Objective**: Move status machine, idle detection, error handling, metrics publishing, and DTO conversion from `ScannerRegistry` into `Scanner`. `ScannerRegistry` becomes a thin collection.

### Step 2.1: Move Status Constants → Scanner

**From**: `ScannerRegistry.STATUS_*` constants + `Scanner.STATUS_*` private constants
**To**: `Scanner.STATUS_*` public constants (single source)
**Why**: Status strings are duplicated in both classes.

**Changes**:
- Remove status constants from `ScannerRegistry`
- Promote `Scanner`'s private status constants to `public static final`
- Update `ScannerRegistry` to reference `Scanner.STATUS_*`

### Step 2.2: Move `updateStatus()` → `Scanner.updateStatus()`

**From**: `ScannerRegistry.updateStatus(agentId, status)` — searches map, updates metadata
**To**: `Scanner.updateStatus(status)` — updates own state

**Changes**:
- `Scanner` gains `ScannerStatus status` field + `updateStatus(status)` method
- `ScannerRegistry.updateStatus()` calls `scanner.updateStatus(status)`
- `ScannerRegistry` lookup: `scanners.get(agentId)` (simplifies — key is agentId)

### Step 2.3: Move `transitionToError()` + `recoverFromError()` → Scanner

**From**: `ScannerRegistry.transitionToError(agentId, reason)`, `ScannerRegistry.recoverFromError(agentId)`
**To**: `Scanner.transitionToError(reason)`, `Scanner.recover()`

**Changes**:
- `Scanner` gains `errorMessage` field + `transitionToError(reason)`, `recover()` methods
- `ScannerRegistry.transitionToError()` calls `scanner.transitionToError(reason)`
- `ScannerRegistry.recoverFromError()` calls `scanner.recover()`

### Step 2.4: Move Idle Detection → Scanner

**From**: `ScannerRegistry.idleChecker`, `startIdleChecker()`, `checkAllScannersForIdle()`, `IDLE_TIMEOUT`, `IDLE_CHECK_INTERVAL`
**To**: `Scanner` owns its own idle timer

**Changes**:
- `Scanner` gains `ScheduledExecutorService idleChecker`, `IDLE_TIMEOUT`, starts on construction
- `Scanner` transitions itself to IDLE after `IDLE_TIMEOUT` of no emissions
- Remove scheduler and idle-check loop from `ScannerRegistry`
- Remove `IDLE_TIMEOUT` and `IDLE_CHECK_INTERVAL` from `ScannerRegistry`

### Step 2.5: Move Emission Tracking → `Scanner.recordEmission()`

**From**: `ScannerRegistry.recordEmission(agentId)` — updates `lastEmittedAt` in map
**To**: `Scanner.recordEmission()` — updates own `lastEmittedAt`

**Changes**:
- `Scanner` gains `lastEmittedAt` field + `recordEmission()` method
- Remove `ScannerRegistry.recordEmission()`
- `ScannerRegistry` no longer needs to track emission timing

### Step 2.6: Move Metrics Event Publishing → `Scanner.pushMetricsEvent()`

**From**: `ScannerRegistry.pushMetricsEvent(event)` — called from many methods
**To**: `Scanner.pushMetricsEvent(event)` — called from Scanner's own methods

**Changes**:
- `Scanner` gains `Consumer<ScannerMetricsChangedEvent> metricsEventPublisher` field + `pushMetricsEvent(event)` method
- Remove `ScannerRegistry.pushMetricsEvent()`
- `Scanner.transitionToError()` publishes error event itself
- `Scanner.recover()` publishes recovery event itself
- `Scanner.updateStatus()` publishes status change event itself
- `Scanner` idle checker publishes idle event itself

### Step 2.7: Move DTO Conversion → `Scanner.toInfo()`

**From**: `ScannerRegistry.toScannerInfo(ScannerMetadata)` → `ScannerInfo`
**To**: `Scanner.toInfo()` → `ScannerInfo`

**Changes**:
- `Scanner` gains `toInfo()` method returning `ScannerInfo`
- Remove `ScannerRegistry.toScannerInfo()`
- `ScannerRegistry.listAll()` calls `scanner.toInfo()`
- `ScannerRegistry.getById()` calls `scanner.toInfo()`

### Step 2.8: Simplify ScannerRegistry

**After all moves, ScannerRegistry contains**:

```java
@Component
public class ScannerRegistry implements DisposableBean {
    private final ConcurrentHashMap<String, Scanner> scanners = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    private final FileMetadataDatabase fileMetadataDatabase;
    private final ScannerObserverUseCase observer;
    private final Consumer<ScannerMetricsChangedEvent> metricsEventPublisher;
    private final EmissionDelayConfig emissionDelayConfig;

    // Lifecycle
    public ScannerInfo createForAgent(String agentId, String targetDirectory, int delaySeconds) {
        // 1. Validate directory (TargetDirectoryValidator or inline)
        // 2. Check duplicate → return existing
        // 3. Create Scanner (new Scanner(...))
        // 4. scanners.put(agentId, scanner)
        // 5. scanner.initSource()
        // 6. return scanner.toInfo()
    }

    public void destroyForAgent(String agentId) {
        Scanner scanner = scanners.remove(agentId);
        if (scanner != null) scanner.destroy();
    }

    public void refreshAgent(String agentId) {
        Scanner scanner = scanners.get(agentId);
        if (scanner != null) scanner.resetToFullScan();
    }

    public Flux<FileHistory> getScannerFlux(String agentId) {
        Scanner scanner = scanners.get(agentId);
        return scanner != null ? scanner.flux() : Flux.empty();
    }

    public List<ScannerInfo> listAll() {
        return scanners.values().stream().map(Scanner::toInfo).toList();
    }

    public Optional<ScannerInfo> getById(String agentId) {
        Scanner scanner = scanners.get(agentId);
        return scanner != null ? Optional.of(scanner.toInfo()) : Optional.empty();
    }

    public void deleteById(String agentId) {
        destroyForAgent(agentId);
    }

    @Override
    public void destroy() {
        scanners.values().forEach(Scanner::destroy);
        scanners.clear();
    }
}
```

**Deleted methods from ScannerRegistry**:
- ~~`updateStatus()`~~ → `Scanner.updateStatus()`
- ~~`transitionToError()`~~ → `Scanner.transitionToError()`
- ~~`recoverFromError()`~~ → `Scanner.recover()`
- ~~`recordEmission()`~~ → `Scanner.recordEmission()`
- ~~`getErrorMessage()`~~ → `Scanner.errorMessage()`
- ~~`getMetadata()`~~ → removed (internal)
- ~~`toScannerInfo()`~~ → `Scanner.toInfo()`
- ~~`pushMetricsEvent()`~~ → `Scanner.pushMetricsEvent()`
- ~~`startIdleChecker()`~~ → `Scanner` constructor
- ~~`checkAllScannersForIdle()`~~ → `Scanner` idle checker
- ~~`ScannerMetadata` record~~ → `Scanner` IS the metadata
- ~~8× duplicated lookup pattern~~ → single `scanners.get(agentId)`

### Phase 2 Tests

Run after each step. Tests are **refactored inline** with source changes (package moves, method signature updates). No new test assertions added.

```bash
# After steps 2.1-2.3 (status, error handling)
./mvnw test -Dtest=ScannerRegistryTest,ScannerRegistryIntegrationTest -q

# After steps 2.4-2.5 (idle, emission)
./mvnw test -Dtest=ScannerRegistryTest,ScannerTest,ScannerFilteredStatusTest,ScannerMetricsTest -q

# After steps 2.6-2.7 (metrics publishing, DTO)
./mvnw test -Dtest=ScannerRegistryTest,ScannerRegistryIntegrationTest,ScannerServiceTest -q

# After step 2.8 (simplified registry, full suite)
./mvnw test -q
```

---

## Phase 3: Move Functions from NativeFileWatcherAdapter to Scanner

**Objective**: Move FileHash check and FileHistory event creation from `NativeFileWatcherAdapter` into `Scanner`. These are business rules, not infrastructure concerns.

### Step 3.1: Move FileHash + FileHistory Creation → Scanner

**From**: `NativeFileWatcherAdapter` reads file content, computes hash via `FileHash.hash()`, creates `FileMetadata`, calls `FileComparator.matches()`, creates `FileHistory`
**To**: `Scanner` does the business logic. `NativeFileWatcherAdapter` emits raw file events (path, content).

**Changes**:

- `NativeFileWatcherAdapter.emitFile(path)` currently:
  1. Reads file content
  2. Computes hash
  3. Creates `FileMetadata`
  4. Calls `FileComparator.matches()` → `FileHistory`
  5. Checks `history.hashMatches()`
  6. Emits through sink if changed

- After:
  - `NativeFileWatcherAdapter.emitFile(path)` emits a raw `RawFileEvent(Path, String)` through the sink
  - `Scanner` subscribes to raw events, applies `FileHash` + `FileComparator` + `FileHistory` logic
  - `Scanner` exposes `Flux<FileHistory>` to consumers (pipeline)

**New types**:

```java
// usecases/RawFileEvent.java — raw event from infrastructure
public record RawFileEvent(Path path, String content) {}
```

**Scanner changes**:
- `Scanner` subscribes to `NativeFileWatcherAdapter` raw events
- Applies `FileHash.hash(content)` → hash
- Creates `FileMetadata(relativePath, content, hash)`
- Calls `FileComparator.matches(metadata)` → `FileHistory`
- Checks `history.hashMatches()` → decides whether to emit
- If new/changed: saves to `FileMetadataStore`, emits through `Sinks.Many<FileHistory>`
- If unchanged: records unchanged, sets FILTERED status
- Tracks file count, discovery count, unchanged count

### Step 3.2: Move scanAllFiles() → Scanner

**From**: `NativeFileWatcherAdapter.scanAllFiles()` does FileHash + FileHistory + FileComparator
**To**: `Scanner.initSource()` and `Scanner.resetToFullScan()` do the business logic

**Changes**:
- `NativeFileWatcherAdapter` no longer has `scanAllFiles()`
- `Scanner.initSource()`:
  1. Calls `NativeFileWatcherAdapter.start()` (which does raw initial scan)
  2. Processes raw events through FileHash + FileHistory logic
  3. Sets status to EMITTING_UPDATES if files discovered, else IDLE
- `Scanner.resetToFullScan()`:
  1. Calls `NativeFileWatcherAdapter.rescan()` (raw walk + emit)
  2. Processes raw events through FileHash + FileHistory logic
  3. Sets status to EMITTING_UPDATES

### Step 3.3: Move FileComparator Dependency → Scanner

**From**: `NativeFileWatcherAdapter` holds `FileComparator` (created with `FileMetadataStore`)
**To**: `Scanner` holds `FileComparator`

**Changes**:
- `Scanner` constructor receives `FileMetadataStore`, creates `FileComparator`
- `NativeFileWatcherAdapter` no longer receives `FileMetadataStore` or `FileComparator`

### Step 3.4: Move FileMetadataStore Dependency → Scanner

**From**: `NativeFileWatcherAdapter` saves metadata on discovery
**To**: `Scanner` saves metadata on discovery

**Changes**:
- `Scanner` calls `fileMetadataStore.save(metadata)` after hash check
- `NativeFileWatcherAdapter` no longer touches `FileMetadataStore`

### Step 3.5: Move Callbacks → Scanner

**From**: `NativeFileWatcherAdapter` receives callbacks for discovery, unchanged, fileCount, filtered, emit, error
**To**: `Scanner` handles all callbacks internally

**Changes**:
- `NativeFileWatcherAdapter` constructor simplified — no callbacks
- `Scanner` subscribes to raw events and handles all business logic:
  - `recordDiscovery()` → calls `ScannerObserverUseCase.recordDiscovery()`
  - `recordUnchanged()` → calls `ScannerObserverUseCase.recordUnchanged()`
  - `updateFileCount()` → calls `ScannerObserverUseCase.updateFileCount()`
  - `onFiltered()` → sets FILTERED status
  - `onEmit()` → resets idle timer
  - `onError()` → transitions to ERROR

### Step 3.6: Create FileSystemScannerAdapterFactory

**From**: `ScannerRegistry` constructs `FileSystemScannerAdapter` with 11 constructor arguments
**To**: `FileSystemScannerAdapterFactory` creates `NativeFileWatcherAdapter` with clean dependencies

**New file**: `src/main/java/com/hdekker/ai_workflow/files/FileSystemScannerAdapterFactory.java`

```java
@Component
public class FileSystemScannerAdapterFactory {
    public NativeFileWatcherAdapter create(String folderPath, Duration delayBetweenReads) {
        return new NativeFileWatcherAdapter(Path.of(folderPath), delayBetweenReads);
    }
}
```

**ScannerRegistry changes**:
- Injects `FileSystemScannerAdapterFactory`
- `createForAgent()` calls `factory.create(targetDirectory, delay)` then wraps in `Scanner`

### Phase 3 Tests

Run after each step. Tests are **refactored inline** with source changes.

```bash
# After steps 3.1-3.2 (FileHash + FileHistory move)
./mvnw test -Dtest=ScannerTest,ScannerFilteredStatusTest,ScannerMetricsTest -q

# After steps 3.3-3.5 (dependencies, callbacks move)
./mvnw test -Dtest=ScannerTest,NativeFileWatcherAdapterMetricsTest,AgentPipelineTest -q

# After step 3.6 (factory, full suite)
./mvnw test -q
```

---

## Test Inventory

### Tests that run after **Phase 1** (rename only):

| Test Class | What it tests | Changes needed |
|------------|--------------|----------------|
| `NativeFileWatcherMetricsTest` | NativeFileWatcher callbacks | Import: `NativeFileWatcher` → `NativeFileWatcherAdapter` |
| `ScannerTest` (was `FileSystemScannerAdapterTest`) | Scanner lifecycle, flux, full scan | Move to `usecases/`, import updates |
| `ScannerFilteredStatusTest` (was `FileSystemScannerAdapterFilteredStatusTest`) | FILTERED status on hash rejection | Move to `usecases/`, import updates |
| `ScannerMetricsTest` (was `FileSystemScannerAdapterMetricsTest`) | Metrics via ScannerObserverUseCase | Move to `usecases/`, import updates |
| `AgentPipelineTest` | Full pipeline: scanner → agent → LLM | Import: `FileSystemScannerAdapter` → `Scanner` |
| `ScannerRegistryTest` | Registry CRUD, error, status | Import: `FileSystemScannerAdapter` → `Scanner` |
| `ScannerRegistryIntegrationTest` | Agent-scanner lifecycle | Import: `FileSystemScannerAdapter` → `Scanner` |
| `FileHashTest` | Hash computation | No changes (FileHash stays in `files/`) |
| `FileUpdatedFilterTest` | FileComparator | No changes |

### Tests that run after **Phase 2** (Registry → Scanner):

| Test Class | What it tests | Changes needed |
|------------|--------------|----------------|
| `ScannerRegistryTest` | Registry CRUD, error, status | Refactor: registry no longer has status/error methods; assertions against `Scanner.toInfo()` |
| `ScannerRegistryIntegrationTest` | Agent-scanner lifecycle | Refactor: registry methods delegate to Scanner |
| `ScannerServiceTest` | UI service → registry | No changes (registry API surface stable: listAll, getById, deleteById, refreshAgent) |
| `AgentLifecycleUseCaseTest` | Agent CRUD with mocked registry | No changes (registry is mocked) |
| `AgentLifecycleUseCasePersistenceTest` | Agent enable/disable/dormant | No changes (registry is mocked) |
| `AgentLifecycleUseCaseScannerRestoreTest` | Scanner creation on restore | No changes (registry is mocked) |

### Tests that run after **Phase 3** (NativeFileWatcherAdapter → Scanner):

| Test Class | What it tests | Changes needed |
|------------|--------------|----------------|
| `ScannerTest` | Scanner lifecycle, flux, full scan | Refactor: Scanner now does FileHash + FileHistory internally |
| `ScannerFilteredStatusTest` | FILTERED status | Refactor: hash logic in Scanner, not adapter |
| `ScannerMetricsTest` | Metrics | Refactor: callbacks wired through Scanner |
| `NativeFileWatcherAdapterMetricsTest` | Raw event emission | Refactor: adapter no longer has callbacks; test raw events only |
| `AgentPipelineTest` | Full pipeline | Refactor: Scanner handles business rules |
| `AgentConfiguratorTest` | Agent pipeline | No changes (uses FileHistory directly) |

---

## Files Created

| File | Package | Phase |
|------|---------|-------|
| `usecases/Scanner.java` | (renamed from `files/FileSystemScannerAdapter.java`) | Phase 1 |
| `files/NativeFileWatcherAdapter.java` | (renamed from `files/NativeFileWatcher.java`) | Phase 1 |
| `files/FileSystemScannerAdapterFactory.java` | New | Phase 3 |
| `usecases/RawFileEvent.java` | New | Phase 3 |
| `usecases/ScannerStatus.java` | New — enum replacing string constants | Phase 2 |

## Files Deleted

| File | Phase |
|------|-------|
| `files/FileSystemScannerAdapter.java` → renamed to `usecases/Scanner.java` | Phase 1 |
| `files/NativeFileWatcher.java` → renamed to `files/NativeFileWatcherAdapter.java` | Phase 1 |

## Files Modified

| File | Phase | Changes |
|------|-------|---------|
| `app/pipeline/management/ScannerRegistry.java` | 2, 3 | Drastically reduced — delegates to `Scanner` |
| `files/NativeFileWatcherAdapter.java` | 1, 3 | Renamed (Phase 1), stripped of business rules (Phase 3) |
| `usecases/Scanner.java` | 1, 2, 3 | Renamed from adapter (Phase 1), gains registry methods (Phase 2), gains FileHash/FileHistory (Phase 3) |
| `usecases/ScannerObserverUseCase.java` | 2 | Loses `recordEmission()`, `isIdle()`, `getLastEmissionTimestamp()`, `pushToUI()` |
| `pipeline/management/DynamicAgentManagerConfiguration.java` | 3 | Injects `FileSystemScannerAdapterFactory` |
| `config/ScannerConfig.java` | 3 | May need adjustment for factory bean |

## Files Unchanged (consumers)

| File | Why |
|------|-----|
| `rest/ScannerRestController.java` | Calls `listAll()`, `deleteById()` — same API |
| `ui/service/ScannerService.java` | Calls `listAll()`, `deleteById()`, `refreshAgent()` — same API |
| `rest/dto/ScannerInfo.java` | DTO, no changes |
| `ui/events/ScannerMetricsChangedEvent.java` | Event type, no changes |
| `files/FileHash.java` | Utility, no changes |
| `files/FileComparator.java` | Utility, no changes |
| `files/FileHistory.java` | Domain record, no changes |
| `files/domain/FileMetadata.java` | Domain record, no changes |
| `usecases/AgentLifecycleUseCase.java` | Calls ScannerRegistry methods — same API surface |

---

## Execution Order

```
Phase 1: Rename
  Step 1.1: NativeFileWatcher → NativeFileWatcherAdapter
    Run: ./mvnw test -Dtest=NativeFileWatcherMetricsTest -q
  Step 1.2: FileSystemScannerAdapter → Scanner (move to usecases/)
    Run: ./mvnw test -Dtest=ScannerTest,ScannerFilteredStatusTest,ScannerMetricsTest,AgentPipelineTest -q
  Phase 1 gate: ./mvnw test -q

Phase 2: Registry → Scanner
  Step 2.1: Status constants
    Run: ./mvnw test -Dtest=ScannerRegistryTest -q
  Step 2.2: updateStatus()
    Run: ./mvnw test -Dtest=ScannerRegistryTest,ScannerTest -q
  Step 2.3: transitionToError + recoverFromError
    Run: ./mvnw test -Dtest=ScannerRegistryTest -q
  Step 2.4: Idle detection
    Run: ./mvnw test -Dtest=ScannerRegistryTest,ScannerTest -q
  Step 2.5: Emission tracking
    Run: ./mvnw test -Dtest=ScannerRegistryTest,ScannerTest -q
  Step 2.6: Metrics publishing
    Run: ./mvnw test -Dtest=ScannerRegistryTest,ScannerRegistryIntegrationTest -q
  Step 2.7: DTO conversion
    Run: ./mvnw test -Dtest=ScannerRegistryTest,ScannerServiceTest -q
  Step 2.8: Simplify ScannerRegistry
    Run: ./mvnw test -q

Phase 3: NativeFileWatcherAdapter → Scanner
  Step 3.1: FileHash + FileHistory move
    Run: ./mvnw test -Dtest=ScannerTest,ScannerFilteredStatusTest -q
  Step 3.2: scanAllFiles move
    Run: ./mvnw test -Dtest=ScannerTest,ScannerMetricsTest -q
  Step 3.3: FileComparator dependency move
    Run: ./mvnw test -Dtest=ScannerTest,NativeFileWatcherAdapterMetricsTest -q
  Step 3.4: FileMetadataStore dependency move
    Run: ./mvnw test -Dtest=ScannerTest,AgentPipelineTest -q
  Step 3.5: Callbacks move
    Run: ./mvnw test -Dtest=ScannerTest,ScannerMetricsTest -q
  Step 3.6: FileSystemScannerAdapterFactory
    Run: ./mvnw test -q
```
