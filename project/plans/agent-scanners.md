# Plan: Agent Scanners – First-Class Citizens

**Status**: Draft  
**Related ADR**: [`adr-dynamic-scanners.md`](../adrs/adr-dynamic-scanners.md)  
**Created**: 2026-04-25  

---

## 1. Overview

This plan elevates **scanners** from an internal implementation detail to a **first-class domain entity** with explicit DTOs, persistence, REST API, and UI. The change is driven by agent needs: each agent specifies a **target directory** that determines which scanner it uses.

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **One-to-one: agent ↔ scanner** | A scanner watches exactly one folder and serves exactly one agent. Two agents watching the same folder get two separate scanners. This is necessary because: (1) on initial creation the scanner emits **all files** (full scan), (2) after that only **updates** are emitted (incremental), (3) if an agent is modified and refreshed, the scanner must **re-emit all files** again. A shared scanner cannot satisfy these competing emission modes. |
| **Explicit Scanner DTO** | `ScannerInfo` mirrors `AgentInfo` — a lightweight, serialisable view of scanner state (path, status, agentId, createdAt). |
| **Scanner as first-class citizen** | Scanners have their own REST endpoints (GET, DELETE) and UI view (`/scanners`). They are **created implicitly** when an agent is created (via POST `/api/agents` with `targetDirectory`). No standalone scanner creation endpoint. |
| **Agent specifies target directory** | Instead of parsing `folderPattern` from regex, the agent creation form gets an explicit **Target Directory** field. This removes the ADR's regex-parsing requirement from the MVP. |

---

## 2. Current State Analysis

### Existing Code

| Component | Location | Notes |
|-----------|----------|-------|
| `FileScanner` (interface) | `files/FileScanner.java` | Single `flux()` method. |
| `FileSystemRecursiveFileScannerAdapter` | `files/FileSystemRecursiveFileScannerAdapter.java | `@Component`, Spring-managed singleton. Injects config, IntegrationFlowContext, FileMetadataDatabase. **Tied to single path** from `FileSystemScannerConfig`. |
| `FileSystemScannerConfig` | `files/FileSystemScannerConfig.java` | Reads one `scanner.url` from `application.yml`. |
| `DynamicAgentManager` | `app/pipeline/management/DynamicAgentManager.java` | Manages agents via `AgentRegistryEntry`. Passes `fileScanner.flux()` to `AgentConfigurator`. No scanner awareness. |
| `AgentRestController` | `rest/AgentRestController.java` | POST / GET / DELETE / PUT for agents only. |
| `AgentInfo` DTO | `rest/dto/AgentInfo.java` | `record(id, definition, createdAt, active, source)`. |
| `AgentListView` | `ui/views/AgentListView.java` | Grid displaying agents. Route: `/agents`. |
| `AgentCreationDialog` | `ui/components/AgentCreationDialog.java` | Modal form with Title, Regex, Body, Output fields. No scanner/target directory field. |
| `AgentInfoService` | `ui/service/AgentInfoService.java` | Thin wrapper around `DynamicAgentManager`. |
| `AgentEntity` | `database/agent/AgentEntity.java` | JPA entity: id, agentDefinitionJson, title, source, createdAt, active. |
| `AgentPersistenceService` | `database/agent/AgentPersistenceService.java` | JSON-serialises AgentDefinition to DB. |

### Existing Tests

| Test Class | Type | Coverage |
|------------|------|----------|
| `FileSystemRecursiveFilterScannerAdapterTest` | `@SpringBootTest` | Disabled single test. No active scanner tests. |
| `DynamicAgentManagerTest` | Unit (Mockito) | Agent CRUD lifecycle. No scanner interaction. |
| `DynamicAgentManagerPersistenceTest` | Unit (Mockito) | Persistence layer for agents. |
| `AgentRestControllerTest` | `@WebMvcTest` | Agent REST endpoints. |
| `AgentListViewTest` | `BrowserlessTest` | Route annotation, basic existence. |
| `AgentCreationDialogTest` | `BrowserlessTest` | Form fields, validation, dialog behavior. |

**Gap**: No tests exist for scanner creation, lifecycle, or the agent-scanner relationship.

---

## 3. Target Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│ UI Layer                                                                 │
│ ┌──────────────────┐  ┌──────────────────────────┐  ┌────────────────┐ │
│ │  ScannerListView │  │  AgentCreationDialog     │  │ AgentListView  │ │
│ │  Route: /scanners│  │  (Target Directory field)│  │ (Scanner col)  │ │
│ └────────┬─────────┘  └──────────┬───────────────┘  └───────┬────────┘ │
│          │                       │                          │           │
│          └───────────────────────┼──────────────────────────┘           │
│                                  │                                      │
├──────────────────────────────────┼──────────────────────────────────────┤
│ Service Layer                                                      │     │
│ ┌─────────────────────┐   ┌──────────────────────────────┐            │     │
│ │  ScannerService      │   │  AgentInfoService (updated)  │            │     │
│ │  - listAll()         │   │  - getAllAgentInfos()        │            │     │
│ │  - create()          │   │  - createAgent() + assign    │            │     │
│ │  - delete()          │   │    scanner                   │            │     │
│ │  - getScannerByAgent()│   └──────────────────────────────┘            │     │
│ └──────────┬───────────┘                                                  │     │
│            │                                                              │     │
├────────────┼──────────────────────────────────────────────────────────────┤     │
│ REST Layer                                                           │     │
│ ┌───────────────────────────┐    ┌─────────────────────────────────┐   │     │
│ │ ScannerRestController     │    │ AgentRestController (updated)   │   │     │
│ │ GET  /api/scanners        │    │ POST /api/agents (targetDir)    │   │     │
│ │ DELETE /api/scanners/{id} │    │ PUT  /api/agents/{id}/assign    │   │     │
│ └───────────┬───────────────┘    └─────────────────────────────────┘   │     │
│             │                                                            │     │
│             │                                                            │     │
├─────────────┼────────────────────────────────────────────────────────────┤     │
│ Domain / Manager Layer                                                 │     │
│ ┌───────────────────────────┐    ┌─────────────────────────────────┐   │     │
│ │ ScannerRegistry           │    │ DynamicAgentManager (updated)   │   │     │
│ │ - createForAgent(id,path) │    │ - addDynamicAgent(def, dir)     │   │     │
│ │ - deleteById(id)          │    │ - removeAgent(id, scannerId)    │   │     │
│ │ - listAll()               │    │ - listAgents() → include scan   │   │     │
│ └───────────┬───────────────┘    │ - refreshAgent(id) → reset      │   │     │
│             │                    │   scanner emission mode         │   │     │
│             │    ← one-to-one binding (agent ↔ scanner)          │     │
├─────────────┼────────────────────────────────────────────────────────────┤     │
├─────────────┼────────────────────────────────────────────────────────────┤     │
│ Persistence Layer                                                      │     │
│ ┌───────────────────────────┐    ┌─────────────────────────────────┐   │     │
│ │ ScannerEntity             │    │ AgentEntity (updated)           │   │     │
│ │ - id (UUID)               │    │ - scannerId (FK → scanner)      │   │     │
│ │ - targetDirectory         │    └─────────────────────────────────┘   │     │
│ │ - status                  │                                          │     │
│ │ - createdAt               │                                          │     │
│ └───────────┬───────────────┘                                          │     │
│             │                                                          │     │
├─────────────┼────────────────────────────────────────────────────────────┤     │
│ Files Layer                                                            │     │
│ ┌───────────────────────────┐    ┌─────────────────────────────────┐   │     │
│ │ FileSystemScannerAdapter  │    │ FileSystemScannerConfig (updated)│   │     │
│ │ (parameterised)           │    │ - supports multi-path config    │   │     │
│ │ - folderPath              │    └─────────────────────────────────┘   │     │
│ │ - delayBetweenReads       │                                          │     │
│ └───────────────────────────┘                                          │     │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Phase 1 – UI Foundation for Scanners

**Goal**: Add a new `/scanners` view that displays all scanners in a grid. No backend yet — use mock data or wire to the eventual REST endpoints.

### 4.1 Deliverables

| # | Artifact | Type | Description |
|---|----------|------|-------------|
| 1.1 | `ScannerInfo` | DTO (`rest/dto/`) | `record(id, targetDirectory, status, createdAt, agentId, source)` |
| 1.2 | `ScannerService` | Service (`ui/service/`) | Thin wrapper around `ScannerRestController` (or `DynamicAgentManager` once available). Provides `Mono<List<ScannerInfo>>`, `Mono<ScannerInfo>`, `Mono<Void>`. |
| 1.3 | `ScannerListView` | View (`ui/views/`) | Vaadin view at route `/scanners`. Grid columns: ID, Target Directory, Status, Agent, Created. Refresh button. Auto-refresh every 30s (same pattern as `ObservabilityView`). |
| 1.4 | `AgentCreationDialog` (updated) | Component (`ui/components/`) | **New field: Target Directory** (required, absolute path). Scanner is created implicitly when agent is created — no separate scanner creation dialog. |
| 1.5 | `ScannerRestController` | Controller (`rest/`) | `GET /api/scanners`, `DELETE /api/scanners/{id}`. Scanners are created *through* agent creation (POST `/api/agents` triggers scanner creation). |
| 1.6 | `ScannerRepository` | Data (`database/scanner/`) | JPA repository for `ScannerEntity`. |
| 1.7 | `ScannerEntity` | Entity (`database/scanner/`) | JPA entity: id, targetDirectory, status, agentId, createdAt, source. |
| 1.8 | `ScannerPersistenceService` | Service (`database/scanner/`) | CRUD over `ScannerEntity`. |
| 1.9 | Unit tests | `src/test/...` | `ScannerRestControllerTest`, `ScannerServiceTest`, `ScannerEntityTest`. |
| 1.10 | Browserless tests | `src/test/...` | `ScannerListViewTest`, updated `AgentCreationDialogTest`. |

### 4.2 ScannerInfo DTO

```java
// rest/dto/ScannerInfo.java
package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;

public record ScannerInfo(
    String id,
    String agentId,       // owner — never null after creation
    String targetDirectory,
    String status,        // "IDLE", "EMITTING_ALL", "EMITTING_UPDATES", "ERROR"
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt
) {}
```

### 4.3 ScannerEntity

```java
// database/scanner/ScannerEntity.java
package com.hdekker.ai_workflow.database.scanner;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scanner")
public class ScannerEntity {

    @Id
    private String id;

    @Column(name = "target_directory", nullable = false)
    private String targetDirectory;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "IDLE";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_emitted_at")
    private LocalDateTime lastEmittedAt;

    // getters/setters ...
}
// Note: no agentId or source field — scanners are always created through agents (one-to-one).
```

### 4.4 ScannerRestController

Scanners are **created implicitly** when an agent is created (POST `/api/agents` with `targetDirectory` triggers scanner creation). The scanner controller only exposes read and delete.

```java
// rest/ScannerRestController.java
package com.hdekker.ai_workflow.rest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

@RestController
@RequestMapping("/api/scanners")
public class ScannerRestController {

    @Autowired
    private ScannerRegistry scannerRegistry;

    @GetMapping
    public ResponseEntity<List<ScannerInfo>> listScanners() {
        List<ScannerInfo> scanners = scannerRegistry.listAll();
        return ResponseEntity.ok(scanners);
    }

    // POST /api/scanners removed — scanners are created through agent creation
    // POST /api/agents (with targetDirectory) triggers scanner creation

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScanner(@PathVariable String id) {
        scannerRegistry.destroyForAgent(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 4.5 ScannerService (UI layer)

```java
// ui/service/ScannerService.java
package com.hdekker.ai_workflow.ui.service;

import reactor.core.publisher.Mono;
import java.util.List;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

@Service
public class ScannerService {

    private final ScannerRegistry scannerRegistry;

    public Mono<List<ScannerInfo>> getAllScannerInfos() {
        // delegate to ScannerRegistry.listAll()
    }

    // createScanner removed — scanners are created through agent creation

    public Mono<Void> deleteScanner(String id) {
        // delegate to ScannerRegistry.destroyForAgent(id)
    }
}
```

### 4.6 ScannerListView

```java
// ui/views/ScannerListView.java
@Route("scanners")
@PageTitle("Scanners")
public class ScannerListView extends VerticalLayout implements AfterNavigationObserver {

    private final Grid<ScannerInfo> grid;
    private final ScannerService scannerService;
    private ProgressBar loadingIndicator;
    private ScheduledExecutorService refreshScheduler;

    // Same pattern as AgentListView + ObservabilityView
    // Grid columns: Agent (clickable → navigate to agent), Target Directory, Status, Created
    // Button: Refresh all (refreshes all scanner-agent pairs)
    // Auto-refresh every 30s
    // Status indicators: IDLE (green), EMITTING_ALL (amber), EMITTING_UPDATES (blue), ERROR (red)
}
```

### 4.7 AgentCreationDialog (updated — Phase 1)

Add **Target Directory** as a new field in the existing `AgentCreationDialog`. Scanner is created implicitly when the agent is created — no separate scanner creation dialog.

```java
// ui/components/AgentCreationDialog.java — new field
private final TextField targetDirectoryField;

// In constructor, after titleField:
targetDirectoryField = createTextField("Target Directory", "");
addHelperText(targetDirectoryField, "Absolute path to the folder this agent scans");
formLayout.add(titleField, targetDirectoryField, fileInputRegexField, agentTypeCombo,
        bodyField, outputStructureField, outputFilenameTemplateField);
```

Fields order (top → bottom):
1. Title
2. **Target Directory** ← NEW (required, absolute path)
3. File Input Regex
4. Agent Type
5. Body (Prompt)
6. Output Structure
7. Output Filename Template

Validation: `targetDirectory` must be a non-blank absolute path (starts with `/` or drive letter).

Validation: `targetDirectory` must be a non-blank absolute path (starts with `/` or drive letter).

### 4.8 ScannerRegistry (stub for Phase 1)

For Phase 1 only, create a minimal `ScannerRegistry` that tracks scanners in-memory (concurrent map). It will be fully implemented in Phase 2.

```java
// app/pipeline/management/ScannerRegistry.java
// Phase 1 stub: in-memory map, no actual file scanning
// One-to-one: each scanner is owned by exactly one agent
public interface ScannerRegistry {
    ScannerInfo createForAgent(String agentId, String targetDirectory, int delaySeconds);
    void deleteById(String id);
    List<ScannerInfo> listAll();
    Optional<ScannerInfo> getById(String id);
    void refreshAgent(String agentId);  // reset scanner to emit all files
}
```

### 4.9 Database Schema Migration

Add `scanner` table to the database:

```sql
CREATE TABLE scanner (
    id VARCHAR(255) PRIMARY KEY,
    target_directory VARCHAR(1024) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'IDLE',
    created_at TIMESTAMP,
    last_emitted_at TIMESTAMP
);
```

Spring Boot will handle this via JPA auto-ddl (or add a Flyway migration if preferred).

### 4.10 Phase 1 Test Plan

| Test Class | Framework | What it verifies |
|------------|-----------|------------------|
| `ScannerRestControllerTest` | `@WebMvcTest` | GET / POST / DELETE `/api/scanners` |
| `ScannerServiceTest` | Unit (Mockito) | Service delegates correctly |
| `ScannerEntityTest` | Unit | Entity field mapping, defaults |
| `ScannerRepositoryTest` | `@DataJpaTest` | CRUD over scanner table |
| `ScannerPersistenceServiceTest` | Unit (Mockito) | Persistence CRUD |
| `ScannerListViewTest` | `BrowserlessTest` | Route annotation, grid exists, columns present, refresh button, status indicators |
| `AgentCreationDialogTest` (updated) | `BrowserlessTest` | New Target Directory field present, required, validated |

---

## 5. Phase 2 – Scanner Infrastructure (Backend)

**Goal**: Wire up actual file-scanning capability. `ScannerRegistry` manages real `FileSystemScannerAdapter` instances.

### 5.1 Deliverables

| # | Artifact | Description |
|---|----------|-------------|
| 2.1 | `FileSystemScannerAdapter` | Parameterised constructor (not `@Component`). Accepts `folderPath` and `delayBetweenReads`. |
| 2.2 | `ScannerRegistry` (full) | Manages lifecycle: create, subscribe, unsubscribe, destroy. Thread-safe concurrent map. |
| 2.3 | `DynamicAgentManager` (updated) | Accepts `ScannerRegistry`. Resolves agent → scanner mapping. |
| 2.4 | `AgentEntity` (updated) | New column `scanner_id` (FK). |
| 2.5 | `AgentDefinition` (updated) | New field `targetDirectory`. |
| 2.6 | `AgentCreationDialog` (updated) | New **Target Directory** field. |
| 2.7 | `AgentListView` (updated) | New **Scanner** column. |

### 5.2 FileSystemScannerAdapter (Refactored)

Remove `@Component` annotation. Accept constructor parameters:

```java
// files/FileSystemScannerAdapter.java
public class FileSystemScannerAdapter implements FileScanner {

    private final String folderPath;
    private final Duration delayBetweenReads;
    private final IntegrationFlowContext context;
    private final FileMetadataDatabase fileMetadataDatabase;

    // Constructor-only (no @Autowired)
    public FileSystemScannerAdapter(String folderPath,
                                     Duration delayBetweenReads,
                                     IntegrationFlowContext context,
                                     FileMetadataDatabase fileMetadataDatabase) {
        // ... same logic as current FileSystemRecursiveFileScannerAdapter
        // but folderPath and delay are from constructor
    }

    @Override
    public Flux<FileHistory> flux() { ... }
}
```

### 5.3 ScannerRegistry (Full)

```java
// app/pipeline/management/ScannerRegistry.java
public class ScannerRegistry implements DisposableBean {

    // Key: agentId (one-to-one: each scanner owned by exactly one agent)
    private final ConcurrentHashMap<String, ScannerMetadata> scanners = new ConcurrentHashMap<>();

    private final IntegrationFlowContext integrationFlowContext;
    private final ApplicationContext applicationContext;
    private final FileMetadataDatabase fileMetadataDatabase;

    // Create scanner for a specific agent
    public ScannerInfo createForAgent(String agentId, String folderPath, Duration delayBetweenReads) {
        // 1. Validate folder exists
        // 2. Create FileSystemScannerAdapter with folderPath + delay
        // 3. Register IntegrationFlow (unique ID: "scanner-" + agentId)
        // 4. Store in map keyed by agentId
        // 5. Return ScannerInfo
    }

    // Destroy scanner when agent is removed
    public void destroyForAgent(String agentId) {
        ScannerMetadata meta = scanners.remove(agentId);
        if (meta != null) {
            meta.flowRegistration().destroy();
        }
    }

    // Reset scanner to emit all files (for agent refresh)
    public void refreshAgent(String agentId) {
        ScannerMetadata meta = scanners.get(agentId);
        if (meta != null) {
            // Dispose old flow registration
            meta.flowRegistration().destroy();
            // Recreate scanner with fresh state (full scan mode)
            FileSystemScannerAdapter freshScanner = new FileSystemScannerAdapter(
                meta.folderPath(), meta.fluxDelay(), integrationFlowContext, fileMetadataDatabase);
            freshScanner.resetToFullScan();
            // Register new flow
            IntegrationFlowRegistration newRegistration = integrationFlowContext
                .registration(freshScanner.createIntegrationFlow())
                .id("scanner-" + agentId)
                .register();
            // Update metadata
            scanners.put(agentId, meta.withScanner(freshScanner)
                .withFlowRegistration(newRegistration)
                .withStatus("EMITTING_ALL"));
        }
    }

    public List<ScannerInfo> listAll() {
        return scanners.values().stream()
            .map(meta -> new ScannerInfo(
                meta.id(), meta.agentId(), meta.folderPath(),
                meta.status(), meta.createdAt(), meta.lastEmittedAt()))
            .toList();
    }

    @Override
    public void destroy() {
        scanners.forEach((agentId, meta) -> meta.flowRegistration().destroy());
    }
}
```

### 5.4 DynamicAgentManager Integration

```java
// Modified constructor
public DynamicAgentManager(
        ScannerRegistry scannerRegistry,   // NEW: replaces FileScanner
        FileWriter fileWriter,
        Path outputDirectory,
        ChatClient chatClient,
        AgentPersistenceService persistenceService) {
    this.scannerRegistry = scannerRegistry;
    this.agentConfigurator = new AgentConfigurator(chatClient,
            fileWriter.createPersister(outputDirectory));
    this.persistenceService = persistenceService;
}

// Modified addDynamicAgent
public AgentInfo addDynamicAgent(AgentDefinition def, String targetDirectory) {
    String agentId = UUID.randomUUID().toString();
    
    // 1. Create scanner for this agent (one-to-one, immediate)
    ScannerInfo scannerInfo = scannerRegistry.createForAgent(agentId, targetDirectory, 5);
    
    // 2. Get scanner flux (starts in full-scan mode)
    Flux<FileHistory> scannerFlux = scannerRegistry.getScannerFlux(agentId);
    
    // 3. Configure agent with scanner's flux
    Flux<PromptResponse> flux = agentConfigurator.configure(def, scannerFlux);
    Disposable subscription = flux.subscribe();
    
    // 4. Track in registry
    AgentRegistryEntry entry = new AgentRegistryEntry(agentId, def, flux,
        LocalDateTime.now(), "DYNAMIC", subscription, scannerInfo.id());
    agentRegistry.put(agentId, entry);
    
    // 5. Persist agent with scannerId
    if (persistenceService != null) {
        persistenceService.save(agentId, def, "DYNAMIC", scannerInfo.id());
    }
    
    return new AgentInfo(agentId, def, entry.createdAt(), true, "DYNAMIC", scannerInfo.id());
}
```

### 5.5 AgentEntity Updates

```java
// Add to AgentEntity:
@Column(name = "scanner_id")
private String scannerId; // FK to scanner.id

// getter/setter
```

### 5.6 AgentCreationDialog Updates

Add a **Target Directory** text field above the File Input Regex field.

```
Form fields:
1. Title
2. Target Directory  ← NEW (required, absolute path)
3. File Input Regex
4. Agent Type
5. Body (Prompt)
6. Output Structure
7. Output Filename Template
```

### 5.7 AgentListView Updates

Add a **Scanner** column and a **Refresh** action button per row:

```java
// Add Scanner column
grid.addColumn(agent -> agent.scannerId() != null 
        ? agent.scannerId() 
        : "N/A")
    .setHeader("Scanner")
    .setAutoWidth(true);

// Add Refresh button column (triggers POST /api/agents/{id}/refresh)
grid.addComponentColumn(agent -> {
    Button refreshBtn = new Button(new Icon(VaadinIcon.REFRESH));
    refreshBtn.addClassName("agent-refresh-btn");
    refreshBtn.addClickListener(e -> {
        agentInfoService.refreshAgent(agent.id()).subscribe(
            info -> Notification.show("Agent " + agent.id() + " refreshed"),
            err -> Notification.show("Refresh failed: " + err.getMessage())
        );
    });
    return refreshBtn;
}).setHeader("").setAutoWidth(true);
```

### 5.8 Phase 2 Test Plan

| Test Class | Framework | What it verifies |
|------------|-----------|------------------|
| `ScannerRegistryTest` | Unit (Mockito) | Create, delete, list, duplicate prevention |
| `FileSystemScannerAdapterTest` | `@SpringBootTest` | Actual file scanning with temp directories |
| `DynamicAgentManagerTest` (updated) | Unit (Mockito) | Agent creation with scanner assignment |
| `AgentRestControllerTest` (updated) | `@WebMvcTest` | POST with `targetDirectory` field |
| `AgentCreationDialogTest` (updated) | `BrowserlessTest` | New Target Directory field present and required |

---

## 6. Phase 3 – Agent-Scanner Relationship & Lifecycle

**Goal**: Implement the one-to-one relationship. Scanner lifecycle follows agent lifecycle. Add `refreshAgent` to reset scanner emission mode.

### 6.1 Deliverables

| # | Artifact | Description |
|---|----------|-------------|
| 3.1 | `ScannerRegistry.destroyForAgent` | Destroy scanner when agent is removed (one-to-one cleanup) |
| 3.2 | `ScannerRegistry.refreshAgent` | Reset scanner to emit all files (for agent modification) |
| 3.3 | `DynamicAgentManager.removeAgent` | Dispose agent subscription, destroy scanner |
| 3.4 | `DynamicAgentManager.refreshAgent` | Trigger full rescan for modified agent |
| 3.5 | `AgentRestController.refreshAgent` | `POST /api/agents/{id}/refresh` |
| 3.6 | `AgentInfo` (updated) | New field `scannerId` |
| 3.7 | `AgentListView` (updated) | Add Refresh button per agent |
| 3.8 | Integration tests | Full lifecycle: create agent → delete agent → scanner cleanup |

### 6.2 Scanner Metadata

```java
// Internal record for ScannerRegistry
// Key: agentId (one-to-one: each scanner owned by exactly one agent)
private record ScannerMetadata(
    FileSystemScannerAdapter scanner,
    String agentId,
    String folderPath,
    String status,        // IDLE | EMITTING_ALL | EMITTING_UPDATES | ERROR
    IntegrationFlowRegistration flowRegistration,
    LocalDateTime createdAt,
    LocalDateTime lastEmittedAt
) {}
```

### 6.3 Agent Removal → Scanner Cleanup

```java
// DynamicAgentManager.removeAgent
public void removeAgent(String id) {
    AgentRegistryEntry entry = agentRegistry.remove(id);
    if (entry != null) {
        entry.subscription().dispose();
        
        // One-to-one: destroy scanner when agent is removed
        if (entry.scannerId() != null) {
            scannerRegistry.destroyForAgent(entry.scannerId());
        }
    }
    // ... delete from DB
}

// DynamicAgentManager.refreshAgent — for modified agents
public void refreshAgent(String agentId) {
    AgentRegistryEntry entry = agentRegistry.get(agentId);
    if (entry != null) {
        entry.subscription().dispose();
        
        // Reset scanner to emit all files
        ScannerMetadata meta = scannerRegistry.getScannerMetadata(entry.scannerId());
        meta.scanner().resetToFullScan();
        
        // Re-subscribe
        Flux<PromptResponse> flux = agentConfigurator.configure(entry.agentDefinition(), meta.scanner().flux());
        Disposable subscription = flux.subscribe();
        
        agentRegistry.put(agentId, entry.withSubscription(subscription));
    }
}
```

### 6.4 Phase 3 Test Plan

| Test Class | Framework | What it verifies |
|------------|-----------|------------------|
| `ScannerRegistryIntegrationTest` | `@SpringBootTest` | Full lifecycle: create agent → delete agent → scanner destroyed |
| `DynamicAgentManagerIntegrationTest` | `@SpringBootTest` | Agent-scanner binding, refresh resets scanner emission |
| `DynamicAgentManagerRefreshTest` | `@SpringBootTest` | Refresh agent → scanner emits all files again |
| `AgentRestControllerTest` (updated) | `@WebMvcTest` | POST `/api/agents/{id}/refresh` endpoint |

---

## 7. Phase 4 – Migration, Documentation & Polish

### 7.1 Deliverables

| # | Artifact | Description |
|---|----------|-------------|
| 4.1 | YAML agent migration | Existing YAML agents get default scanner at `scanner.url` |
| 4.2 | API documentation | OpenAPI/Swagger annotations on controllers |
| 4.3 | README update | New `/scanners` route, scanner lifecycle |
| 4.4 | E2E tests | Playwright: create agent (with targetDirectory) → verify scanner created → verify full scan → refresh agent → verify full rescan → delete agent → verify scanner destroyed |
| 4.5 | Screenshot capture | `capture-snapshot.ts` for `/scanners` view |

---

## 8. File Index (New & Modified)

### New Files

| # | Path | Description |
|---|------|-------------|
| 1 | `rest/dto/ScannerInfo.java` | Scanner DTO |
| 2 | `rest/ScannerRestController.java` | Scanner REST endpoints (GET, DELETE) |
| 3 | `ui/service/ScannerService.java` | Scanner service for UI |
| 4 | `ui/views/ScannerListView.java` | Scanner dashboard view |
| 5 | `database/scanner/ScannerEntity.java` | Scanner JPA entity |
| 6 | `database/scanner/ScannerRepository.java` | Scanner JPA repository |
| 7 | `database/scanner/ScannerPersistenceService.java` | Scanner persistence service |
| 8 | `app/pipeline/management/ScannerRegistry.java` | Scanner lifecycle manager |

### Modified Files

| # | Path | Description |
|---|------|-------------|
| 1 | `rest/dto/AgentInfo.java` | Add `scannerId` field |
| 2 | `rest/dto/ScannerInfo.java` | Add `agentId` (owner), `status` (IDLE/EMITTING_ALL/EMITTING_UPDATES/ERROR) |
| 2 | `pipeline/domain/AgentDefinition.java` | Add `targetDirectory` field |
| 3 | `database/agent/AgentEntity.java` | Add `scannerId` column |
| 4 | `app/pipeline/management/DynamicAgentManager.java` | Accept `ScannerRegistry`, manage agent-scanner mapping |
| 5 | `ui/service/AgentInfoService.java` | Pass `targetDirectory` to manager |
| 6 | `ui/views/AgentListView.java` | Add Scanner column |
| 7 | `ui/components/AgentCreationDialog.java` | Add Target Directory field |
| 8 | `files/FileSystemRecursiveFileScannerAdapter.java` | Refactor to parameterised constructor (rename to `FileSystemScannerAdapter`) |
| 9 | `files/FileSystemScannerConfig.java` | Optional: retain for backward-compatible default scanner |

### New Test Files

| # | Path | Description |
|---|------|-------------|
| 1 | `rest/ScannerRestControllerTest.java` | REST endpoint tests (GET, DELETE) |
| 2 | `ui/service/ScannerServiceTest.java` | Service layer tests |
| 3 | `database/scanner/ScannerEntityTest.java` | Entity tests |
| 4 | `database/scanner/ScannerRepositoryTest.java` | Repository tests |
| 5 | `database/scanner/ScannerPersistenceServiceTest.java` | Persistence tests |
| 6 | `app/pipeline/management/ScannerRegistryTest.java` | Registry unit tests |
| 7 | `app/pipeline/management/ScannerRegistryIntegrationTest.java` | Full lifecycle integration tests |
| 8 | `ui/views/ScannerListViewTest.java` | Browserless view test |
| 9 | `files/FileSystemScannerAdapterTest.java` | Actual scanner tests (temp dirs) |

### Modified Test Files

| # | Path | Description |
|---|------|-------------|
| 1 | `rest/AgentRestControllerTest.java` | Add `targetDirectory` to test payloads |
| 2 | `app/pipeline/management/DynamicAgentManagerTest.java` | Add scanner assignment tests |
| 3 | `ui/components/AgentCreationDialogTest.java` | Add Target Directory field assertions |

---

## 9. Implementation Order (Recommended)

Follow this sequence to maintain buildable state at each step:

```
Phase 1 (UI Foundation)
  ├─ 1.1  ScannerInfo DTO
  ├─ 1.2  ScannerEntity + ScannerRepository
  ├─ 1.3  ScannerPersistenceService
  ├─ 1.4  ScannerRestController (stub ScannerRegistry)
  ├─ 1.5  ScannerService
  ├─ 1.6  AgentCreationDialog (add Target Directory field)
  ├─ 1.7  ScannerListView
  ├─ 1.8  Tests: REST, Service, Entity, Repository, View, Dialog (updated)
  └─ ✅ ./mvnw verify

Phase 2 (Backend Infrastructure)
  ├─ 2.1  Refactor FileSystemScannerAdapter (parameterised)
  ├─ 2.2  ScannerRegistry (full implementation)
  ├─ 2.3  DynamicAgentManager integration
  ├─ 2.4  AgentEntity/AgentDefinition updates
  ├─ 2.5  AgentCreationDialog + AgentListView updates
  ├─ 2.6  Tests: Registry, Adapter, Manager, Dialog
  └─ ✅ ./mvnw verify

Phase 3 (Relationships & Lifecycle)
  ├─ 3.1  Scanner cleanup on agent removal (destroyForAgent)
  ├─ 3.2  Refresh agent endpoint (POST /api/agents/{id}/refresh)
  ├─ 3.3  Integration tests: lifecycle + refresh resets emission
  └─ ✅ ./mvnw verify

Phase 4 (Polish)
  ├─ 4.1  YAML migration
  ├─ 4.2  API docs
  ├─ 4.3  E2E tests
  └─ ✅ ./mvnw verify
```

---

## 10. Open Questions

| # | Question | Tentative Decision |
|---|----------|-------------------|
| 10.1 | Should a scanner serve **multiple agents** simultaneously? | **No.** One-to-one is the permanent model — shared scanners cannot satisfy competing emission modes (full scan vs incremental vs refresh). |
| 10.2 | Should `targetDirectory` be validated at creation time (folder must exist)? | **Yes** — fail fast with clear error message. |
| 10.3 | How should existing YAML agents be migrated? | During Phase 4, assign them to a default scanner created from `scanner.url`. |
| 10.4 | Should scanner delay be configurable per-scanner or global? | **Per-scanner** via the creation dialog (default 5s). Global default for backward compatibility. |
| 10.5 | Should we keep the existing `FileSystemRecursiveFileScannerAdapter` as a bean? | **Yes** — retain as a backward-compatible `@Bean` for YAML agents. The new `FileSystemScannerAdapter` is created on-demand by `ScannerRegistry`. |
| 10.6 | Database: JPA auto-ddl or Flyway migrations? | JPA auto-ddl for rapid iteration. Switch to Flyway before production. |

---

## 11. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Spring Integration flow leaks | Memory/resource exhaustion | `ScannerRegistry` implements `DisposableBean`; registered `IntegrationFlowRegistration` must be destroyed on scanner removal. |
| Folder access permissions | Scanner creation fails silently | Validate folder existence and readability at creation time. Log clear error. |
| Many agents → many scanners | Resource contention | Rate limiting (`delayElements`) per scanner prevents overload. One-to-one means no shared-scanner complexity. |
| Vaadin Browserless test flakiness | Unreliable UI tests | Use explicit component queries (by label/class), not index-based `$(Component.class).single()`. |
| Database schema migration | Data loss on existing agents | Add `scanner_id` column as nullable. Existing agents remain unassigned. |

---

*This plan is a living document. Update as implementation reveals new requirements or constraints.*
