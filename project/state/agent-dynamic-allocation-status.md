# Agent Dynamic Allocation — Status Report

**Date**: 2026-04-21  
**Reference**: ADR `adr-dynamic-scanners.md` ("Dynamic Multi-Scanner Architecture with Agent Subscriptions")

---

## Executive Summary

The **Agent Dynamic Allocation** use case is **partially implemented**. The foundational `DynamicAgentManager` and its REST API are in place, allowing agents to be created, listed, and removed at runtime. However, the **multi-scanner architecture** described in the ADR — which enables agents to subscribe to independent file-system scanners dynamically — has **not yet been implemented**. The system currently uses a **single shared `FileScanner`** for all agents.

---

## What Is Implemented ✅

### 1. Core Agent Lifecycle Manager

| Component | Status | Description |
|-----------|--------|-------------|
| `DynamicAgentManager` | ✅ Complete | In-memory registry (`ConcurrentHashMap`) tracking agents by ID. Supports `initializeFromYAML()`, `addDynamicAgent()`, `removeAgent()`, `listAgents()`. |
| `DynamicAgentManagerConfiguration` | ✅ Complete | Spring `@Bean` wiring. Instantiates `DynamicAgentManager` with `FileScanner`, `FileWriter`, `ChatClient`. |
| `AgentRegistryEntry` (private record) | ✅ Complete | Holds agent id, definition, Flux, createdAt, source ("YAML"/"DYNAMIC"), and `Disposable` subscription. |

### 2. REST API

| Endpoint | Status | Description |
|----------|--------|-------------|
| `POST /api/agents` | ✅ Complete | Creates a dynamic agent from `AgentDefinition` JSON body. Returns `AgentInfo`. |
| `GET /api/agents` | ✅ Complete | Lists all agents (YAML + dynamic) as `AgentInfo[]`. |
| `DELETE /api/agents/{id}` | ✅ Complete | Removes an agent by ID. Disposes subscription. |

### 3. Agent Definition & Pipeline

| Component | Status | Description |
|-----------|--------|-------------|
| `AgentDefinition` (record) | ✅ Complete | Fields: `fileInputRegex`, `title`, `body`, `agentType`, `outputStructure`, `outputFilenameTemplate`. |
| `AgentConfigurator` | ✅ Complete | Wraps `AgentBuilder` + `LLMAdapterFactory`. Configures a `Flux<PromptResponse>` pipeline from an `AgentDefinition`. |
| `AgentBuilder` (fluent) | ✅ Complete | Builder pattern with stages: `withDefinition` → `withTrigger` → `prompting` → `persist` → `split` → `build`. |
| `LLMAdapterFactory` | ✅ Complete | Creates `MapAgentLLMAdapter`, `LLMReducerAdapter`, or `SplitterLLMAdapter` based on `agentType`. |
| `AgentWorkflow` / YAML loading | ✅ Complete | `SystemPromptConfiguration` reads `agent-workflows/**/*.yml` from classpath, copies to local dir, parses. |
| `AgentConfiguration` (Spring `@Configuration`) | ✅ Complete | Loads YAML agents at startup and passes them to `DynamicAgentManager.initializeFromYAML()`. |

### 4. UI Layer

| Component | Status | Description |
|-----------|--------|-------------|
| `AgentListView` | ✅ Complete | Vaadin view at `/agents`. Displays grid with ID, Title, Type, File Regex, Source, Created, Active columns. Has Refresh and "New Agent" buttons. Auto-refreshes LLM status badge. |
| `AgentInfoService` | ✅ Complete | Service layer wrapping `DynamicAgentManager` for Vaadin integration. Returns `Mono<List<AgentInfo>>`. |
| `LlmStatusBadge` | ✅ Complete | Compact badge showing LLM health status. |

### 5. Tests

| Test Class | Status | Coverage |
|------------|--------|----------|
| `DynamicAgentManagerTest` | ✅ 7 tests | Empty YAML, YAML agents, dynamic agents, remove, non-existent remove, multiple agents, mixed YAML+dynamic. |
| `AgentRestControllerTest` | ✅ 3 tests | POST create, GET list, DELETE remove (MockMvc). |
| `AgentListViewTest` | ✅ 2 tests | Class existence, `@Route` annotation. |
| E2E (`agents.spec.ts`) | ✅ 4 tests | Page load, content render, refresh button, notification. |
| `AgentConfiguratorTest` | ✅ | Pipeline configuration tests. |
| `AgentBuilderTest` | ✅ | Builder pattern tests. |

### 6. DTOs

| Component | Status | Description |
|-----------|--------|-------------|
| `AgentInfo` | ✅ Complete | Record: `id`, `definition`, `createdAt`, `active`, `source`. |
| `AdapterStatus` / `LLMStatus` | ✅ Complete | Supporting DTOs for LLM health. |

---

## What Is NOT Implemented ❌

These are the items from **ADR: adr-dynamic-scanners.md** that have not yet been built:

### Phase 1: Core Infrastructure (NOT DONE)

| Component | Status | Description |
|-----------|--------|-------------|
| `ScannerRegistry` | ❌ **Not created** | In-memory registry mapping folder paths to `ScannerMetadata` (FileScanner instance, subscription set, Disposable, shared Flux). Should support `getOrCreateScanner()`, `subscribeAgent()`, `unsubscribeAgent()`, `listScanners()`, `shutdown()`. |
| `ScannerFactory` | ❌ **Not created** | Factory to create/destroy `FileSystemRecursiveFileScannerAdapter` instances dynamically. Must manage Spring Integration flow registration with unique IDs and apply rate limiting via `delayElements()`. |
| `RegexParser` | ❌ **Not created** | Utility to extract `folderPattern` named group from agent's `fileInputRegex`. Must support `hasFolderPattern()`, `extractFolderPaths()`, `validateFolderPattern()`. |
| `FileSystemRecursiveFileScannerAdapter` refactoring | ❌ **Not done** | Currently an `@Component` with `@Autowired` fields. Must be refactored to accept constructor parameters (non-`@Component`) so multiple instances can be created programmatically by `ScannerFactory`. |

### Phase 2: Integration (NOT DONE)

| Component | Status | Description |
|-----------|--------|-------------|
| `DynamicAgentManager` multi-scanner integration | ❌ **Not done** | Currently receives a **single** `FileScanner` via constructor. Must be modified to accept `ScannerRegistry` and subscribe agents to appropriate scanners based on `folderPattern` extraction. |
| `AgentConfigurator` scanner-specific Flux | ❌ **Not done** | Currently takes a single `Flux<FileHistory>`. Must accept scanner-specific Flux per agent. |
| `AgentRegistryEntry` scanner path tracking | ❌ **Not done** | Current record has no `Set<String> scannerPaths`. Must track which scanners each agent subscribes to for proper cleanup. |
| Graceful shutdown hook | ❌ **Not done** | `@PreDestroy` hook to dispose all scanners and subscriptions before application shutdown. |

### Phase 3: REST API & Testing (NOT DONE)

| Component | Status | Description |
|-----------|--------|-------------|
| `ScannerRestController` | ❌ **Not created** | New REST endpoints for scanner management: `GET /api/scanners`, `DELETE /api/scanners/{id}`. |
| `AgentInfo.scannerPaths` field | ❌ **Not done** | `AgentInfo` DTO needs a new field to expose which scanners an agent subscribes to. |
| Scanner lifecycle integration tests | ❌ **Not done** | No tests for scanner creation/destruction, subscription tracking, or multi-scanner scenarios. |
| `folderPattern` validation | ❌ **Not done** | No validation that agent creation requests include a valid `folderPattern` named group in `fileInputRegex`. |

### Phase 4: Migration & Documentation (NOT DONE)

| Component | Status | Description |
|-----------|--------|-------------|
| YAML agent regex migration | ❌ **Not done** | Existing YAML agents (`function-anlaysis/agents.yml`, `solid-priority/agents.yml`) use simple regex **without** `folderPattern`. Must be updated or fallback mechanism added. |
| Fallback for legacy agents | ❌ **Not done** | No mechanism to handle agents without `folderPattern` (e.g., use default scanner or fall back to existing single-scanner behavior). |
| API documentation | ❌ **Not done** | No Swagger/OpenAPI docs for the new scanner endpoints. |
| Operational runbook | ❌ **Not done** | No documentation for troubleshooting scanner issues. |

---

## Current Architecture vs. Target Architecture

### Current (Simplified)

```
┌─────────────────────────────────────────────┐
│  FileSystemRecursiveFileScannerAdapter      │  ← Single scanner (@Component)
│  (single shared Flux<FileHistory>)          │
└──────────────────┬──────────────────────────┘
                   │ shared Flux
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   Agent-1     Agent-2    Agent-3
   (filters)   (filters)  (filters)
```

**Problem**: All agents receive ALL file events; filtering happens downstream. Only one watch root. No dynamic scanner lifecycle.

### Target (from ADR)

```
┌─────────────────────────────────────────────┐
│  ScannerRegistry                            │  ← NEW
│  Map<path → ScannerMetadata>                │
└──────────────┬──────────────────────────────┘
               │
     ┌─────────┼─────────┐
     ▼         ▼         ▼
  Scanner-1  Scanner-2  Scanner-3
  (path A)   (path B)   (path C)
     │         │         │
     ▼         ▼         ▼
  Agent-1   Agent-2   Agent-1 & Agent-3
  (path A)  (path B)  (multi-scanner)
```

**Benefits**: True multi-project support, resource efficiency, isolation, dynamic lifecycle.

---

## Recommended Next Steps (Prioritized)

### Step 1: Refactor `FileSystemRecursiveFileScannerAdapter` (Prerequisite)
- Remove `@Component` and `@Autowired` annotations
- Accept `FileSystemScannerConfig`, `IntegrationFlowContext`, `ApplicationContext`, `FileMetadataDatabase` via constructor
- Make instantiation programmable (no Spring bean lifecycle dependency)
- **Tests needed**: Unit tests for new constructor-based instantiation

### Step 2: Create `RegexParser`
- Extract `folderPattern` named group from agent `fileInputRegex`
- Return `Set<String>` of concrete folder paths
- Validate folder pattern presence and path validity
- **Tests needed**: Unit tests for regex extraction, validation

### Step 3: Create `ScannerFactory`
- Create `FileSystemRecursiveFileScannerAdapter` instances programmatically
- Register Spring Integration flows with unique IDs
- Apply `delayElements()` rate limiting
- Dispose flows on destruction
- **Tests needed**: Unit tests for creation/destruction

### Step 4: Create `ScannerRegistry`
- In-memory map: `String folderPath → ScannerMetadata`
- `getOrCreateScanner(path)` — lazy creation via `ScannerFactory`
- `subscribeAgent(path, agentId)` / `unsubscribeAgent(path, agentId)`
- Track subscription counts; destroy scanner when count reaches 0
- `shutdown()` — dispose all scanners
- **Tests needed**: Unit tests for lifecycle, subscription tracking

### Step 5: Modify `DynamicAgentManager`
- Replace single `FileScanner` dependency with `ScannerRegistry`
- Extract `folderPattern` from `AgentDefinition.fileInputRegex`
- Subscribe to appropriate scanners for each agent
- Track `Set<String> scannerPaths` in `AgentRegistryEntry`
- On agent removal: unsubscribe from scanners, destroy empty scanners
- **Tests needed**: Integration tests for multi-scanner agent lifecycle

### Step 6: Modify `AgentConfigurator`
- Accept scanner-specific `Flux<FileHistory>` instead of global flux
- Each agent gets its own filtered flux from its assigned scanner(s)
- **Tests needed**: Update existing tests

### Step 7: Extend `AgentInfo` DTO
- Add `Set<String> scannerPaths` field
- Update `AgentRestController` and `AgentInfoService` to populate it
- Update UI (`AgentListView`) to display scanner paths
- **Tests needed**: Update REST and UI tests

### Step 8: Add `folderPattern` Validation
- Validate POST `/api/agents` body has valid `folderPattern` in `fileInputRegex`
- Return 400 Bad Request with descriptive error message
- **Tests needed**: REST controller validation tests

### Step 9: Create `ScannerRestController`
- `GET /api/scanners` — list active scanners
- `DELETE /api/scanners/{id}` — force destroy a scanner
- **Tests needed**: REST tests

### Step 10: Add Graceful Shutdown Hook
- `@PreDestroy` on `ScannerRegistry` and `DynamicAgentManager`
- Dispose all subscriptions and scanner flows
- **Tests needed**: Integration tests for shutdown

### Step 11: YAML Agent Migration / Fallback
- Option A: Update all YAML agent `fileInputRegex` to include `folderPattern`
- Option B: Add fallback — if no `folderPattern`, use the `scanner.url` config value as the default scanner path
- **Tests needed**: Migration tests

### Step 12: Observability Integration (from observability-plan.md)
- Add metrics: `agent.created`, `agent.removed`, `agent.active.count`, `scanner.created`, `scanner.removed`
- Add tracing spans for scanner lifecycle operations
- Add structured logging with correlation IDs
- **Tests needed**: Metric verification tests

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `FileSystemRecursiveFileScannerAdapter` refactoring breaks existing YAML agent startup | Medium | High | Keep existing Spring bean; add new programmatic constructor |
| Scanner creation during agent POST causes slow response times | Medium | Medium | Use async scanner creation; return 202 Accepted for slow cases |
| Wildcard `folderPattern` (e.g., `*/src`) causes unexpected scanner proliferation | Low | High | Reject wildcards initially; require explicit absolute paths |
| YAML agent regex migration breaks existing workflows | High | High | Implement fallback mechanism (Step 11) before requiring migration |
| Memory leak from undisposed `Disposable` subscriptions | Medium | High | Add `@PreDestroy` hook; add tests for subscription disposal |

---

## Files to Create (New)

1. `src/main/java/.../files/RegexParser.java`
2. `src/main/java/.../files/ScannerFactory.java`
3. `src/main/java/.../files/ScannerRegistry.java`
4. `src/main/java/.../files/ScannerMetadata.java` (record)
5. `src/main/java/.../rest/ScannerRestController.java`
6. `src/main/java/.../rest/dto/ScannerInfo.java` (new DTO)

## Files to Modify (Existing)

1. `FileSystemRecursiveFileScannerAdapter.java` — remove `@Component`, add constructor
2. `DynamicAgentManager.java` — replace `FileScanner` with `ScannerRegistry`
3. `DynamicAgentManagerConfiguration.java` — wire `ScannerRegistry` + `ScannerFactory`
4. `AgentConfigurator.java` — accept scanner-specific flux
5. `AgentInfo.java` — add `scannerPaths` field
6. `AgentRestController.java` — add validation, update response
7. `AgentInfoService.java` — update for new fields
8. `AgentListView.java` — display scanner paths
9. `AgentConfiguration.java` — handle `folderPattern` in YAML agents
10. `FileSystemScannerConfig.java` — support multiple scanner URLs (optional)

---

## Estimated Effort

| Phase | Effort | Dependencies |
|-------|--------|-------------|
| Step 1: Refactor Scanner Adapter | 1-2 days | None |
| Step 2: RegexParser | 0.5-1 day | None |
| Step 3: ScannerFactory | 2-3 days | Step 1 |
| Step 4: ScannerRegistry | 2-3 days | Steps 2, 3 |
| Step 5: DynamicAgentManager integration | 2-3 days | Step 4 |
| Step 6-8: Configurator, DTO, Validation | 1-2 days | Step 5 |
| Step 9-12: REST, Shutdown, Migration, Observability | 2-3 days | Steps 5-8 |
| **Total** | **~10-17 days** | Sequential dependencies |

---

*This document should be reviewed and updated as implementation progresses.*
