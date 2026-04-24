# Plan: Agent Persistence to SQLite

## Overview

Currently, `DynamicAgentManager` stores all agent state in a `ConcurrentHashMap<String, AgentRegistryEntry>` — purely in-memory. On application restart, all dynamically created agents are lost. YAML agents are re-initialized from files, but their runtime state (created timestamps, subscription status) is not persisted.

This plan migrates agent state to the existing SQLite database (already configured via `DatabaseConfig.java` with `@EnableJpaRepositories("com.hdekker.ai_workflow.database")`).

---

## Current Architecture

### What's In-Memory Today

```
DynamicAgentManager
├── agentRegistry: ConcurrentHashMap<String, AgentRegistryEntry>
│   ├── id: String  
│   ├── agentDefinition: AgentDefinition (record)
│   ├── flux: Flux<PromptResponse> (reactive stream — not serializable)
│   ├── createdAt: LocalDateTime
│   ├── source: String ("YAML" or "DYNAMIC")
│   └── subscription: Disposable (reactive — not serializable)
```

**Gap:** `AgentInfo.active` is always `true` — no enable/disable mechanism exists today. YAML agents are always subscribed and running at startup.

### What Needs to Persist

| Field | Reason |
|-------|--------|
| `id` | Unique agent identifier |
| `agentDefinition` (as JSON) | Full agent config — regex, title, body, agentType, outputStructure, outputFilenameTemplate |
| `createdAt` | When the agent was created |
| `source` | "YAML" or "DYNAMIC" |
| `active` | Whether the agent should be running (enabled/disabled state) |

### What Must Stay In-Memory

| Field | Reason |
|-------|--------|
| `flux` | Reactive `Flux<PromptResponse>` stream — not serializable, runtime-only |
| `subscription` | Reactive `Disposable` — runtime-only |

### Enable/Disable Requirements

| Scenario | Behavior |
|----------|----------|
| User disables agent (UI or REST) | Dispose subscription, set `active=false` in DB, remove from registry |
| User enables agent (UI or REST) | Re-subscribe flux to new subscription, set `active=true` in DB, add to registry |
| App restart, `active=false` agent in DB | Load metadata but **do not** create flux/subscription — agent stays in registry as dormant |
| App restart, `active=true` agent in DB | Load metadata, create flux, subscribe — agent runs normally |
| YAML agent, never explicitly disabled | Persisted as `active=true` on first startup; if user disables, stays `false` across restarts |
| YAML agent, explicitly disabled | Same as dynamic — `active=false` persists |

---

## Design Decisions

---

## Design Decisions

1. **AgentDefinition stored as JSON TEXT** — `AgentDefinition` is a record with 6 fields. Storing as a JSON string avoids creating a separate joined table and keeps the schema flat. JPA `@Column(columnDefinition = "TEXT")` handles this.

2. **Separate from YAML files** — YAML agents are still loaded from `agent-workflows/` directory at startup, but their runtime state is now persisted so they appear in the database alongside dynamically created agents.

3. **Agent lifecycle managed in DB** — When an agent is removed via `removeAgent()`, it's deleted from the database (not just the map). This means agent state survives restarts.

4. **Re-attach on startup** — On application startup, agents are loaded from the database. Their `flux` and `subscription` are recreated via `AgentConfigurator.configure()` (the reactive streams are always runtime-only).

5. **Follow existing JPA patterns** — The project already has `LLMStatusEntity` / `LLMStatusRepository` and `FileMetadataEntity` / `FileMetaRepository` in the `com.hdekker.ai_workflow.database` package. New entities go there.

---

## Implementation Steps

### Step 1: Create `AgentEntity`

**Path:** `src/main/java/com/hdekker/ai_workflow/database/agent/AgentEntity.java`

```java
@Entity
@Table(name = "agent")
public class AgentEntity {
    @Id
    private String id;
    
    @Column(columnDefinition = "TEXT")
    private String agentDefinitionJson;  // Serialized AgentDefinition
    
    private String title;                // Denormalized for quick queries
    private String source;               // "YAML" or "DYNAMIC"
    
    private LocalDateTime createdAt;
    
    private LocalDateTime lastStartedAt; // When last enabled/started
    
    private boolean active = true;       // true = enabled (should run), false = disabled (dormant)
    
    // Default constructor required by JPA
    public AgentEntity() {}
    
    // Getters and setters
}
```

**Notes:**
- `agentDefinitionJson` stores the full `AgentDefinition` as a JSON string (use Jackson `ObjectMapper.writeValueAsString()`)
- `title` is denormalized from the JSON for UI grid display without deserializing
- `active` = **enabled/disabled state** — NOT whether the agent is currently subscribed
  - `true` → agent is enabled, will be subscribed on startup, can be toggled off by user
  - `false` → agent is disabled, stays dormant across restarts, user can toggle on to re-enable
- `lastStartedAt` tracks when the agent was last enabled (useful for UI display and audit trail)

### Step 2: Create `AgentRepository`

**Path:** `src/main/java/com/hdekker/ai_workflow/database/agent/AgentRepository.java`

```java
@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, String> {
    
    List<AgentEntity> findAllByOrderByCreatedAtDesc();
    
    List<AgentEntity> findByActiveTrueOrderByCreatedAtDesc();
    
    List<AgentEntity> findByActiveFalseOrderByCreatedAtDesc();
    
    Optional<AgentEntity> findById(String id);
    
    long countByActiveTrue();
    
    long countByActiveFalse();
}
```

### Step 3: Create `AgentPersistenceService`

**Path:** `src/main/java/com/hdekker/ai_workflow/database/agent/AgentPersistenceService.java`

Service layer to handle entity↔in-memory mapping:

```java
@Service
public class AgentPersistenceService {
    
    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    
    public AgentPersistenceService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
        this.objectMapper = new ObjectMapper();
    }
    
    // Save an agent (create or update)
    public AgentEntity save(String id, AgentDefinition definition, String source) {
        AgentEntity entity = new AgentEntity();
        entity.setId(id);
        entity.setAgentDefinitionJson(objectMapper.writeValueAsString(definition));
        entity.setTitle(definition.title());
        entity.setSource(source);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setActive(true);
        return agentRepository.save(entity);
    }
    
    // Load agent definition from DB
    public Optional<AgentDefinition> getDefinition(String id) {
        return agentRepository.findById(id)
            .map(entity -> {
                try {
                    return objectMapper.readValue(entity.getAgentDefinitionJson(), AgentDefinition.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
    }
    
    // List all agents
    public List<AgentEntity> listAll() {
        return agentRepository.findAllByOrderByCreatedAtDesc();
    }
    
    // Delete an agent
    public void deleteById(String id) {
        agentRepository.deleteById(id);
    }
    
    // Toggle agent on/off
    public void toggle(String id, boolean enable) {
        agentRepository.findById(id).ifPresent(entity -> {
            entity.setActive(enable);
            if (enable) {
                entity.setLastStartedAt(LocalDateTime.now());
            }
            agentRepository.save(entity);
        });
    }
    
    // Enable agent
    public void enable(String id) {
        toggle(id, true);
    }
    
    // Disable agent
    public void disable(String id) {
        toggle(id, false);
    }
    
    // Restore all enabled agents from DB (for startup)
    public List<AgentEntity> findAllActive() {
        return agentRepository.findByActiveTrueOrderByCreatedAtDesc();
    }
    
    // Get all agents (for UI listing — shows enabled and disabled)
    public List<AgentEntity> findAllOrdered() {
        return agentRepository.findAllByOrderByCreatedAtDesc();
    }
}
```

### Step 4: Refactor `DynamicAgentManager`

**Path:** `src/main/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicAgentManager.java`

**Changes:**
1. Inject `AgentPersistenceService`
2. Add dormant registry for disabled agents: `Map<String, DormantAgentEntry> dormantAgents`
3. In `addDynamicAgent()`: persist to DB before adding to in-memory registry
4. In `removeAgent()`: delete from DB after removing from in-memory registry
5. Add `enableAgent()` / `disableAgent()` methods
6. Update `listAgents()` to merge active + dormant agents with correct `active` flag
7. Add `restoreFromDatabase()` method called at startup to re-register agents from DB
8. Update `AgentRegistryEntry` record to track active state

**New record types:**
```java
// Active agent (has flux + subscription)
private record AgentRegistryEntry(
    String id,
    AgentDefinition agentDefinition,
    Flux<PromptResponse> flux,
    LocalDateTime createdAt,
    String source,   // "YAML" or "DYNAMIC"
    Disposable subscription
) {}

// Dormant agent (no flux, no subscription — disabled)
private record DormantAgentEntry(
    String id,
    AgentDefinition agentDefinition,
    LocalDateTime createdAt,
    String source
) {}
```

**Key behavior:**
```
addDynamicAgent():
  1. Generate UUID
  2. Call AgentPersistenceService.save() → DB (active=true)
  3. Configure reactive pipeline (AgentConfigurator.configure())
  4. Subscribe to flux
  5. Add to in-memory ConcurrentHashMap

removeAgent():
  1. Remove from ConcurrentHashMap
  2. Dispose subscription
  3. Call AgentPersistenceService.deleteById() → DB
  4. Log

enableAgent(String id):
  1. Call AgentPersistenceService.enable(id) → DB (active=true, lastStartedAt=now)
  2. Load AgentDefinition from DB
  3. Configure reactive pipeline (AgentConfigurator.configure())
  4. Subscribe to flux
  5. Add to in-memory ConcurrentHashMap
  6. Log

disableAgent(String id):
  1. Get entry from ConcurrentHashMap
  2. If found: dispose subscription, remove from registry
  3. Call AgentPersistenceService.disable(id) → DB (active=false)
  4. Log

restoreFromDatabase():
  1. Load all active (enabled) agents from DB
  2. For each:
     - Configure reactive pipeline (AgentConfigurator.configure())
     - Subscribe to flux
     - Add to in-memory ConcurrentHashMap
     - Log
  3. Load disabled agents from DB:
     - Store metadata only (no flux, no subscription)
     - Add to a separate dormant registry or mark in existing map
     - Log
  4. Log restored enabled/disabled counts

listAgents():
  1. Stream from ConcurrentHashMap (for live subscription state)
  2. Cross-reference with DB for accurate active state
  3. Return AgentInfo with correct active flag
```

### Step 5: Update `AgentConfiguration` (YAML loading)

**Path:** `src/main/java/com/hdekker/ai_workflow/pipeline/AgentConfiguration.java`

Update the YAML agent initialization to persist to DB:

```java
// In constructor, after loading YAML agents:
dynamicAgentManager.initializeFromYAML(yamlAgents);  // This now persists each to DB
```

**Key behavior change:**
- First startup: YAML agents saved to DB with `active=true`
- If user disabled a YAML agent, its `active=false` persists in DB
- On next startup: `restoreFromDatabase()` loads YAML agents — only `active=true` ones get flux/subscription
- YAML agents that were disabled stay dormant across restarts

No structural change to `AgentConfiguration` constructor needed — it just calls `initializeFromYAML()` which now delegates to `DynamicAgentManager.restoreFromDatabase()` internally.

### Step 6: Update REST API

**Path:** `src/main/java/com/hdekker/ai_workflow/rest/AgentRestController.java`

Add enable/disable endpoints:

```java
// Enable an agent
@PutMapping("/{id}/enable")
public ResponseEntity<Void> enableAgent(@PathVariable String id) {
    dynamicAgentManager.enableAgent(id);
    return ResponseEntity.ok().build();
}

// Disable an agent
@PutMapping("/{id}/disable")
public ResponseEntity<Void> disableAgent(@PathVariable String id) {
    dynamicAgentManager.disableAgent(id);
    return ResponseEntity.ok().build();
}
```

The existing REST API delegates to `DynamicAgentManager`, which now handles both in-memory and DB operations.

### Step 7: Update Tests

**Tests to update/create:**

| Test | Action |
|------|--------|
| `DynamicAgentManagerTest` | Update to use test SQLite DB or mock `AgentPersistenceService` |
| `AgentEntityTest` | **New** — unit test for entity fields and JSON serialization |
| `AgentRepositoryTest` | **New** — `@DataJpaTest` with H2 for repository queries |
| `AgentPersistenceServiceTest` | **New** — unit test for entity↔JSON mapping |

**Test database strategy:**
- Use H2 in-memory for `@DataJpaTest` (fast, auto-cleanup)
- Use SQLite for integration tests (`@SpringBootTest`) with `@Tag("integration")`

---

## File Structure

```
src/main/java/com/hdekker/ai_workflow/database/
├── agent/
│   ├── AgentEntity.java          # NEW — JPA entity
│   └── AgentRepository.java      # NEW — Spring Data JPA repository
├── filemetadata/
│   ├── FileMetadataEntity.java
│   └── FileMetaRepository.java
└── llmstatus/
    ├── LLMStatusEntity.java
    └── LLMStatusRepository.java

src/main/java/com/hdekker/ai_workflow/app/pipeline/management/
└── DynamicAgentManager.java      # MODIFIED — adds persistence
```

---

## Migration Path (Zero Downtime)

1. **Phase 1**: Add persistence layer, keep in-memory as primary (dual-write on create/delete)
2. **Phase 2**: On startup, load from DB (if DB exists) then fall back to YAML files
3. **Phase 3**: Remove in-memory-only code paths, DB is the source of truth
4. **Phase 4**: Add agent execution state tracking (prompt response counts, last run time)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `AgentDefinition.body` is large (prompt text) | SQLite TEXT supports up to 2GB — plenty for prompt content |
| Reactive streams (`Flux`, `Disposable`) not serializable | Only persist metadata, never the streams themselves |
| YAML agents need DB entry on startup | `initializeFromYAML()` calls `AgentPersistenceService.save()` |
| Concurrent modification during startup | Use `ConcurrentHashMap` (already in place) + synchronized DB reads |
| H2 vs SQLite dialect differences in tests | `@DataJpaTest` uses H2; integration tests use SQLite — both work with simple queries |
| Disabled agent re-enabled after restart | `enableAgent()` calls `AgentConfigurator.configure()` + subscribes — same as dynamic creation |
| YAML agent toggled off, then on | Dormant entry loaded from DB, flux re-subscribed — no config reload needed |

---

## Acceptance Criteria

- [ ] `AgentEntity` and `AgentRepository` exist in `database/agent/`
- [ ] `AgentPersistenceService` handles CRUD + enable/disable operations
- [ ] `DynamicAgentManager.addDynamicAgent()` persists to DB (active=true)
- [ ] `DynamicAgentManager.removeAgent()` deletes from DB
- [ ] `DynamicAgentManager.enableAgent()` re-subscribes and updates DB
- [ ] `DynamicAgentManager.disableAgent()` disposes subscription and updates DB
- [ ] `DynamicAgentManager.listAgents()` returns correct active state for all agents
- [ ] Application restart: only `active=true` agents get flux/subscription
- [ ] Application restart: `active=false` agents load as dormant (metadata only)
- [ ] YAML agents are persisted to DB on startup
- [ ] Disabled YAML agents stay dormant across restarts
- [ ] All existing tests pass
- [ ] New `@DataJpaTest` for `AgentRepository` passes
- [ ] New `AgentPersistenceServiceTest` passes
- [ ] UI shows persisted agents after restart
- [ ] UI shows correct enabled/disabled state per agent
