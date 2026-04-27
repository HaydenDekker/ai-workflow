# Use-Case Refactor Plan

## Objective

Update the domain layer terminology from "services" and "managers" to "usecases" to better reflect that these classes represent business workflows rather than infrastructure or UI concerns.

---

## Scope

### ✅ Domain Layer — Rename to UseCases

| Current Class | Proposed Class | Package Move |
|---------------|----------------|--------------|
| `LLMStatusService` | `AgentStatusUsecase` | `service/` → `usecases/` |
| `DynamicAgentManager` | `AgentLifecycleUseCase` | `app/pipeline/management/` → `usecases/` |
| `AgentPersistenceService` | `AgentPersistenceUsecase` | Keep in `database/agent/` |
| `ScannerPersistenceService` | `ScannerPersistenceUsecase` | Keep in `database/scanner/` |

### ❌ Keep as-is

| Class | Package | Rationale |
|-------|---------|-----------|
| `ScannerRegistry` | `app/pipeline/management/` | Manages scanner adapter lifecycle — infrastructure concern |
| `AgentInfoService` | `ui/service/` | Thin UI wrapper, not a domain usecase |
| `ScannerService` | `ui/service/` | Thin UI wrapper, not a domain usecase |

---

## Files to Change (13 total)

### Source (7 files)

1. `src/main/java/com/hdekker/ai_workflow/service/LLMStatusService.java`
   - Rename file → `AgentStatusUsecase.java`
   - Move to `src/main/java/com/hdekker/ai_workflow/usecases/`
   - Rename class → `AgentStatusUsecase`
   - Update `@Service` Javadoc to reference new name

2. `src/main/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManager.java`
   - Rename file → `AgentLifecycleUseCase.java`
   - Move to `src/main/java/com/hdekker/ai_workflow/usecases/`
   - Rename class → `AgentLifecycleUseCase`
   - Update Javadoc: "Manages" → "Orchestrates"
   - Update constructor parameter names if needed

3. `src/main/java/com/hdekker/ai_workflow/database/agent/AgentPersistenceService.java`
   - Rename class → `AgentPersistenceUsecase`
   - Update `@Service` Javadoc
   - Keep in `database/agent/` package

4. `src/main/java/com/hdekker/ai_workflow/database/scanner/ScannerPersistenceService.java`
   - Rename class → `ScannerPersistenceUsecase`
   - Update `@Service` Javadoc
   - Keep in `database/scanner/` package

5. `src/main/java/com/hdekker/ai_workflow/pipeline/management/DynamicAgentManagerConfiguration.java`
   - Update import and bean type references

6. `src/main/java/com/hdekker/ai_workflow/rest/AgentRestController.java`
   - Update import and field type references

7. `src/main/java/com/hdekker/ai_workflow/rest/ObservabilityRestController.java`
   - Update import and field type references

### Tests (6 files)

8. `src/test/java/com/hdekker/ai_workflow/service/LLMStatusServiceTest.java`
   - Rename file → `AgentStatusUsecaseTest.java`
   - Move to `src/test/java/com/hdekker/ai_workflow/usecases/`
   - Update class references

9. `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManagerTest.java`
   - Rename file → `AgentLifecycleUseCaseTest.java`
   - Move to `src/test/java/com/hdekker/ai_workflow/usecases/`
   - Update class and field references

10. `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManagerPersistenceTest.java`
    - Rename file → `AgentLifecycleUseCasePersistenceTest.java`
    - Move to `src/test/java/com/hdekker/ai_workflow/usecases/`
    - Update class references

11. `src/test/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManagerScannerRestoreTest.java`
    - Rename file → `AgentLifecycleUseCaseScannerRestoreTest.java`
    - Move to `src/test/java/com/hdekker/ai_workflow/usecases/`
    - Update class references

12. `src/test/java/com/hdekker/ai_workflow/database/agent/AgentPersistenceServiceTest.java`
    - Update class references

13. `src/test/java/com/hdekker/ai_workflow/ui/service/ScannerServiceTest.java`
    - Update references if it depends on ScannerPersistenceService

---

## Implementation Steps

Execute incrementally with `./mvnw verify -q` after each step.

### Step 1: `AgentPersistenceUsecase` (lowest risk)
- Rename class in `AgentPersistenceService.java`
- Update test `AgentPersistenceServiceTest.java`
- Run `./mvnw verify -q`

### Step 2: `ScannerPersistenceUsecase` (low risk)
- Rename class in `ScannerPersistenceService.java`
- Update any references (ScannerRegistry, ScannerService)
- Run `./mvnw verify -q`

### Step 3: `AgentStatusUsecase` (medium risk)
- Rename file and class, move to `usecases/`
- Update `ObservabilityRestController` references
- Rename/move test file
- Run `./mvnw verify -q`

### Step 4: `AgentLifecycleUseCase` (highest risk)
- Rename file and class, move to `usecases/`
- Update `DynamicAgentManagerConfiguration.java`
- Update `AgentRestController.java`
- Update `AgentInfoService.java` (UI service)
- Rename/move all 3 test files
- Run `./mvnw verify -q`

---

## Risks

| Risk | Mitigation |
|------|------------|
| ScannerRegistry references old class names | Update all imports systematically |
| Spring bean injection fails | Keep `@Service` / `@Component` annotations, only rename classes |
| Test failures from stale references | Verify each test file's imports after move |
| Vaadin view compilation | `AgentInfoService` injects by type — renaming should be transparent |

---

## Verification

After all steps complete:

```bash
./mvnw verify -q
npm run test:e2e
```

Confirm:
- All unit tests pass
- All integration tests pass
- E2E tests pass
- No remaining `Service` or `Manager` in domain-layer class names
