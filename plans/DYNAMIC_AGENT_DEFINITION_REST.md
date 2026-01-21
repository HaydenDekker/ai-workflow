# Dynamic Agent Definition REST Adapter

## Overview

Add a REST interface to dynamically configure prompt pipelines at runtime. The interface allows:
- Creating pipelines from `AgentDefinition` objects
- Listing all active pipelines (both YAML-configured and dynamically created)
- Deleting/removing pipelines

## Current Architecture

### Pipeline Initialization (Startup)

```
PromptPipelineConfiguration
  ↓ (reads YAML configs from classpath*:prompt-chains/**)
PromptPipelineConfigurator.configure(List<AgentDefinition>)
  ↓ (creates Flux<PromptResponse> for each AgentDefinition)
FileSystemRecursiveFileScannerAdapter.flux() (shared, .share())
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `FileSystemRecursiveFileScannerAdapter` | Watches file system, emits `Flux<FileHistory>` |
| `PromptPipelineConfigurator` | Builds and subscribes to pipelines from AgentDefinitions |
| `LLMAdapter` types | MapAgentLLMAdapter, LLMReducerAdapter, SplitterLLMAdapter |
| `AgentDefinition` | Record containing fileInputRegex, title, body, agentType, outputStructure, outputFilenameTemplate |
| `PromptPipelineConfiguration` | Startup configuration that initializes all YAML-configured pipelines |

---

## Design Requirements

1. **Receive single `AgentDefinition`** via REST POST to initialize a pipeline
2. **Hook into existing filesystem event listener** - Use same `FileSystemRecursiveFileScannerAdapter.flux()` as existing pipelines
3. **List all pipelines** - Both YAML-configured and dynamically created
4. **Delete pipelines** - Remove dynamic pipelines; stop YAML pipelines
5. **Manage both pipeline types** - Unified registry for YAML and dynamic pipelines

---

## Proposed Implementation

### Phase 1 - Core REST & In-Memory Management

#### 1. New DTO: `PipelineInfo`

**Location**: `src/main/java/com/hdekker/ai_workflow/rest/dto/PipelineInfo.java`

```java
package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;

public record PipelineInfo(
    String id,
    String title,
    String agentType,
    LocalDateTime createdAt,
    boolean active,
    String source  // "YAML" or "DYNAMIC"
) {}
```

#### 2. Pipeline Management Service

**Location**: `src/main/java/com/hdekker/ai_workflow/pipeline/management/DynamicPipelineManager.java`

**Responsibilities**:
- Maintain registry of active pipelines: `Map<String, PipelineRegistryEntry>`
- Handle pipeline lifecycle (start/stop)
- Generate unique IDs for pipelines (UUID for dynamic, title for YAML)
- Subscribe/unsubscribe from shared file scanner flux

**Internal Class: `PipelineRegistryEntry`**
```java
private record PipelineRegistryEntry(
    String id,
    AgentDefinition agentDefinition,
    Flux<PromptResponse> flux,
    LocalDateTime createdAt,
    String source,  // "YAML" or "DYNAMIC"
    Disposable subscription
) {}
```

**Key Methods**:
- `void initializeFromYAML(List<AgentDefinition> yamlAgents)` - Called on startup to register YAML-configured pipelines
- `PipelineInfo addDynamicPipeline(AgentDefinition def)` - Create dynamic pipeline from REST
- `void removePipeline(String id)` - Cancel subscription, remove from registry
- `List<PipelineInfo> listPipelines()` - Return all active pipelines (both YAML and dynamic)

**Dependencies**:
- `PromptPipelineConfigurator` - For building pipelines from AgentDefinition
- `FileSystemRecursiveFileScannerAdapter` - For shared file input flux (already in PromptPipelineConfigurator)

#### 3. REST Controller

**Location**: `src/main/java/com/hdekker/ai_workflow/rest/PipelineRestController.java`

**Endpoints**:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/pipelines` | Create and start a dynamic pipeline from AgentDefinition |
| GET | `/api/pipelines` | List all active pipelines (both YAML and dynamic) |
| DELETE | `/api/pipelines/{id}` | Stop and remove a pipeline by ID |

**Request/Response**:

```java
// POST /api/pipelines
// Request Body: AgentDefinition (JSON)
// Response: PipelineInfo

// GET /api/pipelines
// Response: List<PipelineInfo>

// DELETE /api/pipelines/{id}
// Response: 204 No Content
```

#### 4. Integration with Existing Components

**Modify**: `PromptPipelineConfiguration`

Inject `DynamicPipelineManager` and initialize YAML-configured pipelines:

```java
@Autowired
DynamicPipelineManager dynamicPipelineManager;

// After existing systemPromptConfiguration.getPromptChains() loading
dynamicPipelineManager.initializeFromYAML(
    systemPromptConfiguration.getPromptChains()
        .stream()
        .flatMap(pc -> pc.chain().stream())
        .toList()
);
```

**Keep**: Existing YAML-based initialization for backward compatibility

---

### Data Flow

#### Creating a Dynamic Pipeline

```
POST /api/pipelines (AgentDefinition)
  ↓
PipelineRestController.addPipeline()
  ↓
DynamicPipelineManager.addDynamicPipeline()
  ↓
PromptPipelineConfigurator.configure(List.of(agentDefinition))
  ↓
PromptPipelineBuilder (uses FileSystemRecursiveFileScannerAdapter.flux())
  ↓
Flux<PromptResponse>.subscribe() -> registered in manager
  ↓
Returns PipelineInfo
```

#### Listing Pipelines

```
GET /api/pipelines
  ↓
DynamicPipelineManager.listPipelines()
  ↓
Returns List<PipelineInfo> (both YAML and DYNAMIC sources)
```

#### Deleting a Pipeline

```
DELETE /api/pipelines/{id}
  ↓
DynamicPipelineManager.removePipeline(id)
  ↓
- Cancel subscription (Disposable.dispose())
- Remove from registry
  ↓
For YAML source: Only stops (cannot recreate without restart)
For DYNAMIC source: Full removal
```

---

## Design Decisions

| Decision | Option | Selection |
|----------|--------|-----------|
| **Pipeline ID** | UUID, Title-based, Auto-increment | UUID for dynamic, title for YAML |
| **Pipeline State** | In-memory only, Database persisted | In-memory only (Phase 1) |
| **Deletion Behavior** | Immediate cancel, Soft-delete | Immediate cancel |
| **Concurrency** | Reactive controller, Blocking controller | Blocking controller (simpler), Reactor internally |
| **Validation** | JSR-303, Custom validator | None yet (as requested) |
| **YAML Pipeline ID** | UUID each startup, Use title field | Use title field (stable) |

---

## File Structure

```
src/main/java/com/hdekker/ai_workflow/
├── rest/
│   ├── PipelineRestController.java          (NEW)
│   └── dto/
│       └── PipelineInfo.java                 (NEW)
├── pipeline/
│   └── management/
│       └── DynamicPipelineManager.java       (NEW)
└── pipeline/
    └── PromptPipelineConfiguration.java      (MODIFY - inject DynamicPipelineManager)

src/test/java/com/hdekker/ai_workflow/
├── rest/
│   ├── PipelineRestControllerTest.java      (NEW)
│   └── DynamicPipelineManagerTest.java       (NEW)
```

---

## Open Questions

1. **Pipeline ID for YAML Configs**: Use the `title` from `AgentDefinition` as ID for YAML-configured pipelines (stable across restarts), or generate UUID each startup?
   - **Current plan**: Use title field for stability

2. **Deleting YAML Pipelines**: When a YAML-configured pipeline is deleted via REST:
   - **Hidden only** (not restartable without app restart)?
   - **Trackable as "stopped"** (allow restart via new POST)?
   - **Not allowed** (return 403/400 for YAML pipelines)?

3. **Batch Operations**: Need bulk endpoints for managing multiple pipelines?

4. **GET Response Detail**: Should `GET /api/pipelines` return full `AgentDefinition` objects or just summary `PipelineInfo`?
   - **Current plan**: Summary only (`PipelineInfo`)

---

## Future Enhancements (Phase 2)

### Database Persistence

**Entity**: `AgentConfigurationEntity`
```java
@Entity
public class AgentConfigurationEntity {
    @Id
    UUID id;
    String title;
    String agentType;
    String body;
    String outputStructure;
    String outputFilenameTemplate;
    String fileInputRegex;
    LocalDateTime createdAt;
    boolean active;
}
```

**Repository**: `AgentConfigurationRepository extends JpaRepository<AgentConfigurationEntity, UUID>`

**Service**: `AgentConfigurationService`
- CRUD operations for AgentDefinition storage
- On startup, load active configs and auto-start pipelines

---

## Testing Strategy

### Unit Tests

1. **`DynamicPipelineManagerTest`**
   - Test adding dynamic pipeline
   - Test initializing from YAML
   - Test listing pipelines (both sources)
   - Test removing pipeline (dynamic)
   - Test stopping pipeline (YAML)

2. **`PipelineRestControllerTest`**
   - Test POST /api/pipelines
   - Test GET /api/pipelines
   - Test DELETE /api/pipelines/{id}
   - Test error handling (duplicate IDs, not found)

### Integration Tests

1. Test full flow: REST POST → pipeline creation → file system event → LLM processing
2. Test coexistence of YAML and dynamic pipelines processing same file

---

## Dependencies

No new Maven dependencies required. Uses existing:
- Spring Boot Web (for @RestController)
- Spring Integration (existing file scanner)
- Spring AI (existing chat client)
- Reactor (existing flux handling)
