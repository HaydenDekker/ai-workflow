# Plan: Clean Up Scanner-Agent Relationship

## Problem
The current design reverses the FK for a many-to-one relationship:
- `AgentEntity` has `scannerId` (FK on the "one" side — backwards)
- `ScannerEntity` has no `agentId` (no back-reference)

Since scanner ID == agent ID (stateless runtime constructs), the agent should not reference scanners at all. The scanner just needs `agentId` for management.

## Target
- Remove `scannerId` from `AgentEntity`
- Remove `scannerId` from `AgentInfo` DTO
- Remove `scannerId` from `AgentPersistenceUsecase.save()`
- Remove `scannerId` from `AgentLifecycleUseCase` internal records
- Scanner management uses `agentId` directly (scanner ID == agent ID)

## Implementation Status: ✅ COMPLETE

All changes implemented and verified (287 tests pass, 0 failures).

## Files Changed

### 1. `AgentEntity.java`
- ✅ Removed `scannerId` field, getter, setter, `@Column(name = "scanner_id")`

### 2. `AgentPersistenceUsecase.java`
- ✅ Removed 4-arg `save()` method (with `scannerId` parameter)
- ✅ Kept 3-arg `save(id, definition, source)` method
- ✅ Removed `entity.setScannerId(scannerId)` call

### 3. `AgentInfo.java`
- ✅ Removed `scannerId` from record

### 4. `AgentLifecycleUseCase.java`
- ✅ Removed `scannerId` from `AgentRegistryEntry` and `DormantAgentEntry` records
- ✅ Changed all `persistenceService.save(id, def, source, scannerId)` → `persistenceService.save(id, def, source)`
- ✅ Changed all `scannerRegistry.destroyForAgent(entry.scannerId())` → `scannerRegistry.destroyForAgent(id)`
- ✅ Changed all `scannerRegistry.refreshAgent(scannerId)` → `scannerRegistry.refreshAgent(agentId)`
- ✅ Removed `scannerId` from all `new AgentInfo(...)` calls
- ✅ Updated log messages to remove "(scannerId: ...)" suffixes
- ✅ Updated Javadoc references to scannerId

### 5. UI Components
- ✅ `AgentDetailDialog.java` — Removed `scannerIdField` UI component and related code
- ✅ `AgentListView.java` — Removed "Scanner" column from the agent grid
- ✅ `ScannerListView.java` — Removed "Scanner ID" column — only shows "Agent" (since scanner ID == agent ID)

### 6. Tests
- ✅ `AgentRestControllerTest.java` — removed `scannerId` assertions/expectations
- ✅ `AgentLifecycleUseCasePersistenceTest.java` — removed `scannerId` from helper method and assertions
- ✅ `AgentLifecycleUseCaseScannerRestoreTest.java` — removed `scannerId` references
- ✅ `AgentLifecycleUseCaseTest.java` — removed `scannerId` assertions
- ✅ `ScannerRegistryIntegrationTest.java` — changed `agentInfo.scannerId()` → `agentInfo.id()` (scanner ID == agent ID)
- ✅ `AgentCreationDialogTest.java` — updated `AgentInfo` constructor calls
- ✅ `AgentListViewDeleteTest.java` — updated `AgentInfo` constructor calls

## Verification
- `./mvnw compile` — ✅ PASS
- `./mvnw test` — ✅ PASS (287 tests: 285 run, 2 skipped, 0 failures)
- `./mvnw verify` — ✅ PASS (all integration tests also pass)

## Notes
- No DB migration needed — `scanner_id` column becomes unused and can be dropped in a future cleanup
- Scanner registry already keyed by `agentId`, so runtime behavior is unchanged
- The `ScannerInfo` record still has both `id` and `agentId` — that's a separate cleanup opportunity
- Scanner ID == Agent ID at runtime, so `destroyForAgent()` and `refreshAgent()` now use the agent ID directly
