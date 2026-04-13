# Agent Terminology Refactoring Plan

## Overview

Rename infrastructure from "pipeline" terminology to "agent" terminology to reflect that each configured unit is a single agent that operates independently. Agents are connected via file I/O contracts (regex filters matching output filename templates), not code-level chaining. `AgentWorkflow` is a logical grouping of related agents in YAML configuration only.

### Key Concepts

- **Agent** = File scanner filter + LLM call + File writer
- **AgentWorkflow** = Collection of agents that may be connected via file I/O (logical grouping in YAML)
- **AgentDefinition** = Configuration for a single agent (unchanged)
- **PromptRequest/PromptResponse** = LLM interaction types (unchanged)

### Rationale

Current terminology is misleading:
- `PromptPipelineConfigurator` configures a single agent, not a pipeline
- `PromptChain` suggests code-level chaining, but agents are independent
- File system is the actual integration mechanism, not in-memory pipelines

New terminology clarifies:
- Each agent operates independently
- Workflows emerge from file I/O contracts
- Git history captures the agentic loop naturally

---

## Phase 1: Domain Model Changes

### 1.1 Rename `PromptChain` → `AgentWorkflow`

**File:** `src/main/java/com/hdekker/ai_workflow/pipeline/domain/PromptChain.java`

**Changes:**
```java
// Before
public record PromptChain(List<AgentDefinition> chain) {}

// After
public record AgentWorkflow(List<AgentDefinition> agents) {}
```

**Tasks:**
- [x] Rename class from `PromptChain` to `AgentWorkflow`
- [x] Rename field from `chain` to `agents`
- [x] Update Javadoc: "Logical grouping of related agents in a workflow"
- [x] Rename file to `AgentWorkflow.java`

**Impact Files:**
- `SystemPromptConfiguration.java` - reads YAML, parses workflows
- `PromptPipelineConfiguration.java` - uses `getPromptChains()`
- Test: `PromptConfigurationTest.java`

---

## Phase 2: Infrastructure Renames

### 2.1 `PromptPipelineConfigurator` → `AgentConfigurator`

**File:** `src/main/java/com/hdekker/ai_workflow/app/pipeline/PromptPipelineConfigurator.java`

**Changes:**
```java
// Before
public class PromptPipelineConfigurator {
    public Flux<PromptResponse> configure(AgentDefinition agentDefintion) { ... }
}

// After
public class AgentConfigurator {
    public Flux<PromptResponse> configure(AgentDefinition agentDefinition) { ... }
}
```

**Tasks:**
- [ ] Rename class to `AgentConfigurator`
- [ ] Rename file to `AgentConfigurator.java`
- [ ] Fix typo: `agentDefintion` → `agentDefinition` (parameter name)
- [ ] Update logging: "Configuring agent: {title}" instead of "Configuring pipeline"
- [ ] Update Javadoc

**Impact Files:**
- `DynamicPipelineManager.java` - uses configurator
- Test: `PromptPipelineConfiguratorTest.java` → `AgentConfiguratorTest.java`

---

### 2.2 `PromptPipelineBuilder` → `AgentBuilder`

**File:** `src/main/java/com/hdekker/ai_workflow/app/pipeline/PromptPipelineBuilder.java`

**Changes:**
```java
// Before
public class PromptPipelineBuilder {
    public static interface WithPipelineDefinition { ... }
    public static class BuilderImpl { ... }
}

// After
public class AgentBuilder {
    public static interface WithAgentDefinition { ... }
    public static class BuilderImpl { ... }
}
```

**Tasks:**
- [ ] Rename class to `AgentBuilder`
- [ ] Rename file to `AgentBuilder.java`
- [ ] Rename interface: `WithPipelineDefinition` → `WithAgentDefinition`
- [ ] Keep interfaces unchanged: `Triggered`, `PromptMapped`, `Persistable`, `Splittable`, `Enrichable`
- [ ] Update method: `withDefinition()` Javadoc
- [ ] Update logging references
- [ ] Update static factory: `instance()` Javadoc

**Impact Files:**
- `AgentConfigurator.java` (from 2.1)
- Test: `PromptPipelineBuilderTest.java` → `AgentBuilderTest.java`
- Integration tests: `LLMAdapterIntegrationTest.java`, `BuilderPatternIntegrationTest.java`

---

### 2.3 `PromptPipelineConfiguration` → `AgentConfiguration`

**File:** `src/main/java/com/hdekker/ai_workflow/pipeline/PromptPipelineConfiguration.java`

**Changes:**
```java
// Before
@Configuration
public class PromptPipelineConfiguration {
    List<AgentDefinition> yamlAgents = systemPromptConfiguration.getPromptChains()
        .stream()
        .flatMap(pc-> pc.chain().stream())
        .toList();
}

// After
@Configuration
public class AgentConfiguration {
    List<AgentDefinition> yamlAgents = systemPromptConfiguration.getAgentWorkflows()
        .stream()
        .flatMap(wf-> wf.agents().stream())
        .toList();
}
```

**Tasks:**
- [ ] Rename class to `AgentConfiguration`
- [ ] Rename file to `AgentConfiguration.java`
- [ ] Update: `getPromptChains()` → `getAgentWorkflows()`
- [ ] Update: `pc.chain()` → `wf.agents()`
- [ ] Update logging: "Configuring agent workflows" instead of "Configuring pipeline"
- [ ] Update variable names: `yamlAgents` (keep), update loop variables

**Impact Files:**
- Spring configuration (bean name auto-updates)
- `DynamicPipelineManagerConfiguration.java` - may reference bean
- Test files that inject this bean

---

## Phase 3: System Prompt Configuration

### 3.1 Update YAML Parsing Classes

**File:** `src/main/java/com/hdekker/ai_workflow/prompt/SystemPromptConfiguration.java`

**Tasks:**
- [ ] Rename inner class: `PromptChainYAMLConfigReader` → `AgentWorkflowYAMLConfigReader`
- [ ] Rename record: `PromptChainFiles` → `AgentWorkflowFiles`
- [ ] Rename inner class: `PromptChainFileExtractor` → `AgentWorkflowFileExtractor`
- [ ] Update method: `getPromptChains()` → `getAgentWorkflows()`
- [ ] Update field: `List<PromptChain> promptChains` → `List<AgentWorkflow> agentWorkflows`
- [ ] Update all internal references throughout file

**YAML Property Change Detection:**
```java
// Add to AgentWorkflowYAMLConfigReader.readYamlFile()
PromptWorkflow doc = mapper.readValue(yamlFile, AgentWorkflow.class);

// Check for deprecated 'chain' property
if (yamlContent.contains("chain:")) {
    log.error("YAML file {} uses deprecated 'chain:' property. Use 'agents:' instead.", yamlFile.getFileName());
    throw new IllegalArgumentException("Deprecated YAML format: use 'agents:' instead of 'chain:'");
}
```

**Tasks:**
- [ ] Add validation to detect `chain:` property in YAML
- [ ] Log clear error with migration instruction
- [ ] Fail fast (no migration utility)

**Impact Files:**
- Test: `PromptConfigurationTest.java` - update assertions
- YAML files in `src/main/resources/prompt-chains/` (next phase)

---

## Phase 4: REST Layer

### 4.1 Rename `PipelineInfo` → `AgentInfo`

**File:** `src/main/java/com/hdekker/ai_workflow/rest/dto/PipelineInfo.java`

**Changes:**
```java
// Before
public record PipelineInfo(
    String id,
    AgentDefinition agentDefinition,
    LocalDateTime createdAt,
    boolean active,
    String source
) {}

// After
public record AgentInfo(
    String id,
    AgentDefinition definition,
    LocalDateTime createdAt,
    boolean active,
    String source
) {}
```

**Tasks:**
- [ ] Rename record to `AgentInfo`
- [ ] Rename file to `AgentInfo.java`
- [ ] Rename field: `agentDefinition` → `definition` (shorter, clearer)
- [ ] Keep: `id`, `createdAt`, `active`, `source`

**Impact Files:**
- `PipelineRestController.java`
- `PipelineInfoService.java`
- `PipelineInfoListView.java`
- All tests for above

---

### 4.2 Rename `PipelineRestController` → `AgentRestController`

**File:** `src/main/java/com/hdekker/ai_workflow/rest/PipelineRestController.java`

**Changes:**
```java
// Before
@RestController
@RequestMapping("/api/pipelines")
public class PipelineRestController {
    @PostMapping
    public ResponseEntity<PipelineInfo> createPipeline(@RequestBody AgentDefinition agentDefinition) { ... }
    
    @GetMapping
    public ResponseEntity<List<PipelineInfo>> listPipelines() { ... }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePipeline(@PathVariable String id) { ... }
}

// After
@RestController
@RequestMapping("/api/agents")
public class AgentRestController {
    @PostMapping
    public ResponseEntity<AgentInfo> createAgent(@RequestBody AgentDefinition agentDefinition) { ... }
    
    @GetMapping
    public ResponseEntity<List<AgentInfo>> listAgents() { ... }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String id) { ... }
}
```

**Tasks:**
- [ ] Rename class to `AgentRestController`
- [ ] Rename file to `AgentRestController.java`
- [ ] Change `@RequestMapping("/api/pipelines")` → `@RequestMapping("/api/agents")`
- [ ] Rename method: `createPipeline()` → `createAgent()`
- [ ] Rename method: `listPipelines()` → `listAgents()`
- [ ] Rename method: `deletePipeline()` → `deleteAgent()`
- [ ] Update return types: `PipelineInfo` → `AgentInfo`
- [ ] Update parameter names in methods

**Impact Files:**
- Frontend/Hilla clients (auto-generated from endpoint)
- Test: `PipelineRestControllerTest.java` → `AgentRestControllerTest.java`

---

## Phase 5: Dynamic Pipeline Manager

### 5.1 Rename `DynamicPipelineManager` → `DynamicAgentManager`

**File:** `src/main/java/com/hdekker/ai_workflow/app/pipeline/management/DynamicPipelineManager.java`

**Changes:**
```java
// Before
public class DynamicPipelineManager {
    private final Map<String, PipelineRegistryEntry> pipelineRegistry;
    private final PromptPipelineConfigurator pipelineConfigurator;
    
    public void initializeFromYAML(List<AgentDefinition> yamlAgents) { ... }
    public PipelineInfo addDynamicPipeline(AgentDefinition def) { ... }
    public void removePipeline(String id) { ... }
    public List<PipelineInfo> listPipelines() { ... }
    
    private record PipelineRegistryEntry(...) {}
}

// After
public class DynamicAgentManager {
    private final Map<String, AgentRegistryEntry> agentRegistry;
    private final AgentConfigurator agentConfigurator;
    
    public void initializeFromYAML(List<AgentDefinition> yamlAgents) { ... }
    public AgentInfo addDynamicAgent(AgentDefinition def) { ... }
    public void removeAgent(String id) { ... }
    public List<AgentInfo> listAgents() { ... }
    
    private record AgentRegistryEntry(...) {}
}
```

**Tasks:**
- [ ] Rename class to `DynamicAgentManager`
- [ ] Rename file to `DynamicAgentManager.java`
- [ ] Rename field: `pipelineRegistry` → `agentRegistry`
- [ ] Rename field: `pipelineConfigurator` → `agentConfigurator`
- [ ] Rename record: `PipelineRegistryEntry` → `AgentRegistryEntry`
- [ ] Rename method: `addDynamicPipeline()` → `addDynamicAgent()`
- [ ] Rename method: `removePipeline()` → `removeAgent()`
- [ ] Rename method: `listPipelines()` → `listAgents()`
- [ ] Keep method: `initializeFromYAML()` (name is fine)
- [ ] Update return types: `PipelineInfo` → `AgentInfo`
- [ ] Update all internal references
- [ ] Update logging: "Added dynamic agent" instead of "Added dynamic pipeline"

**Impact Files:**
- `AgentConfiguration.java` (from 2.3) - creates bean
- `DynamicPipelineManagerConfiguration.java` → `DynamicAgentManagerConfiguration.java`
- Test: `DynamicPipelineManagerTest.java` → `DynamicAgentManagerTest.java`
- Integration tests using this manager

---

### 5.2 Rename `DynamicPipelineManagerConfiguration` → `DynamicAgentManagerConfiguration`

**File:** `src/main/java/com/hdekker/ai_workflow/pipeline/management/DynamicPipelineManagerConfiguration.java`

**Tasks:**
- [ ] Rename class to `DynamicAgentManagerConfiguration`
- [ ] Rename file to `DynamicAgentManagerConfiguration.java`
- [ ] Rename bean method: `dynamicPipelineManager()` → `dynamicAgentManager()`
- [ ] Update return type: `DynamicPipelineManager` → `DynamicAgentManager`
- [ ] Update bean creation: `new DynamicPipelineManager(...)` → `new DynamicAgentManager(...)`

**Impact Files:**
- Spring context (bean name auto-updates)
- Any class that `@Autowired` this bean by type

---

## Phase 6: UI Layer (Vaadin + Hilla)

### 6.1 Rename `PipelineInfoService` → `AgentInfoService`

**File:** `src/main/java/com/hdekker/ai_workflow/ui/service/PipelineInfoService.java`

**Changes:**
```java
// Before
@Service
public class PipelineInfoService {
    public Mono<List<PipelineInfo>> getAllPipelineInfos() {
        return webClient.get()
            .uri("/api/pipelines")
            .retrieve()
            .bodyToFlux(PipelineInfo.class)
            .collectList();
    }
    
    public Mono<Void> deletePipeline(String id) {
        return webClient.delete()
            .uri("/api/pipelines/{id}", id)
            ...
    }
}

// After
@Service
public class AgentInfoService {
    public Mono<List<AgentInfo>> getAllAgentInfos() {
        return webClient.get()
            .uri("/api/agents")
            .retrieve()
            .bodyToFlux(AgentInfo.class)
            .collectList();
    }
    
    public Mono<Void> deleteAgent(String id) {
        return webClient.delete()
            .uri("/api/agents/{id}", id)
            ...
    }
}
```

**Tasks:**
- [ ] Rename class to `AgentInfoService`
- [ ] Rename file to `AgentInfoService.java`
- [ ] Rename method: `getAllPipelineInfos()` → `getAllAgentInfos()`
- [ ] Rename method: `deletePipeline()` → `deleteAgent()`
- [ ] Update URI: `/api/pipelines` → `/api/agents`
- [ ] Update return types: `PipelineInfo` → `AgentInfo`
- [ ] Update logging messages

**Impact Files:**
- `PipelineInfoListView.java` → `AgentListView.java`
- Test: `PipelineInfoServiceIntegrationTest.java` → `AgentInfoServiceIntegrationTest.java`

---

### 6.2 Rename `PipelineInfoListView` → `AgentListView`

**File:** `src/main/java/com/hdekker/ai_workflow/ui/views/PipelineInfoListView.java`

**Changes:**
```java
// Before
@Route("pipeline-info")
@PageTitle("Pipeline Grid")
public class PipelineInfoListView extends VerticalLayout {
    private final Grid<PipelineInfo> grid;
    private final PipelineInfoService pipelineInfoService;
    
    H2 header = new H2("Pipeline Grid");
    Button createButton = new Button("New Pipeline", ...);
    
    grid.addColumn(pipeline -> pipeline.agentDefinition().title(), ...);
}

// After
@Route("agents")
@PageTitle("Agent List")
public class AgentListView extends VerticalLayout {
    private final Grid<AgentInfo> grid;
    private final AgentInfoService agentInfoService;
    
    H2 header = new H2("Agent List");
    Button createButton = new Button("New Agent", ...);
    
    grid.addColumn(agent -> agent.definition().title(), ...);
}
```

**Tasks:**
- [ ] Rename class to `AgentListView`
- [ ] Rename file to `AgentListView.java`
- [ ] Change `@Route("pipeline-info")` → `@Route("agents")`
- [ ] Change `@PageTitle("Pipeline Grid")` → `@PageTitle("Agent List")`
- [ ] Rename field: `pipelineInfoService` → `agentInfoService`
- [ ] Update grid type: `Grid<PipelineInfo>` → `Grid<AgentInfo>`
- [ ] Update header text: "Pipeline Grid" → "Agent List"
- [ ] Update button text: "New Pipeline" → "New Agent"
- [ ] Update column accessor: `pipeline.agentDefinition()` → `agent.definition()`
- [ ] Update notification messages:
  - "No pipelines found" → "No agents found"
  - "Loaded {n} pipelines" → "Loaded {n} agents"
  - "Create new Pipeline dialog" → "Create new Agent dialog"
- [ ] Update constructor parameter type
- [ ] Update `reloadData()`, `updateGrid()` parameter types

**Impact Files:**
- Frontend routing (Hilla auto-generates from Java routes)
- Test: `PipelineInfoListViewTest.java` → `AgentListViewTest.java`

---

## Phase 7: YAML Configuration Files

### 7.1 Rename Directory

**Task:**
- [ ] Rename `src/main/resources/prompt-chains/` → `src/main/resources/agent-workflows/`

**Update Constant:**
**File:** `src/main/java/com/hdekker/ai_workflow/prompt/SystemPromptConfiguration.java`
```java
// Before
public static final String SYSTEM_PROMPT_CHAIN_DIRECTORY_SEARCH = "prompt-chains";
public static final String SYSTEM_PROMPT_CHAIN_DIRECTORY_SEARCH_CLASSPATH = "classpath*:"+ SYSTEM_PROMPT_CHAIN_DIRECTORY_SEARCH + "/**";

// After
public static final String SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH = "agent-workflows";
public static final String SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH_CLASSPATH = "classpath*:"+ SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH + "/**";
```

**Tasks:**
- [ ] Rename constant: `SYSTEM_PROMPT_CHAIN_DIRECTORY_SEARCH` → `SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH`
- [ ] Rename constant: `SYSTEM_PROMPT_CHAIN_DIRECTORY_SEARCH_CLASSPATH` → `SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH_CLASSPATH`
- [ ] Update all references to these constants in the file
- [ ] Rename physical directory

---

### 7.2 Update YAML Structure

**Files:**
- `src/main/resources/agent-workflows/solid-priority/chain.yml`
- `src/main/resources/agent-workflows/function-anlaysis/chain.yml`

**Changes:**
```yaml
# Before (solid-priority/chain.yml)
chain:
  - 
    fileInputRegex: (?:.*/)?(?<name>.*)\.(?<ext>java)
    title: SOLID_NON_COMPLIANCE
    body: solid-compliance.md
    agentType: Split
    outputStructure: solid-compliance-output.md
    outputFilenameTemplate: output/solid-priorty/non-compliance/${name}.md
  -
    fileInputRegex: .*output/solid-priorty/non-compliance/(?<name>.*)\.(?<ext>md)
    title: PRIORITY_ORDER
    body: solid-priority.md
    agentType: Map
    outputStructure: solid-priority-output.md
    outputFilenameTemplate: output/solid-priorty/priorty-order/${name}.md

# After
agents:
  - 
    fileInputRegex: (?:.*/)?(?<name>.*)\.(?<ext>java)
    title: SOLID_NON_COMPLIANCE
    body: solid-compliance.md
    agentType: Split
    outputStructure: solid-compliance-output.md
    outputFilenameTemplate: output/solid-priorty/non-compliance/${name}.md
  -
    fileInputRegex: .*output/solid-priorty/non-compliance/(?<name>.*)\.(?<ext>md)
    title: PRIORITY_ORDER
    body: solid-priority.md
    agentType: Map
    outputStructure: solid-priority-output.md
    outputFilenameTemplate: output/solid-priorty/priorty-order/${name}.md
```

**Tasks:**
- [ ] Update `solid-priority/chain.yml`: Change `chain:` → `agents:`
- [ ] Update `function-anlaysis/chain.yml`: Change `chain:` → `agents:`
- [ ] Consider renaming `chain.yml` → `workflow.yml` (optional, but consistent)

**Optional: Rename YAML Files**
- [ ] Rename `chain.yml` → `workflow.yml` in both directories
- [ ] Update `PromptChainFileExtractor` logic to look for `workflow.yml` instead of `chain.yml`

---

## Phase 8: Test Updates

### 8.1 Unit Test Renames

**Tasks:**
- [ ] Rename `PromptPipelineConfiguratorTest.java` → `AgentConfiguratorTest.java`
  - Update all references to `PromptPipelineConfigurator` → `AgentConfigurator`
  - Update `PipelineInfo` → `AgentInfo`
  
- [ ] Rename `PromptPipelineBuilderTest.java` → `AgentBuilderTest.java`
  - Update `PromptPipelineBuilder` → `AgentBuilder`
  - Update `WithPipelineDefinition` → `WithAgentDefinition`
  
- [ ] Rename `PipelineRestControllerTest.java` → `AgentRestControllerTest.java`
  - Update endpoint: `/api/pipelines` → `/api/agents`
  - Update `PipelineInfo` → `AgentInfo`
  - Update method names in tests
  
- [ ] Rename `PipelineInfoListViewTest.java` → `AgentListViewTest.java`
  - Update view class reference
  - Update `PipelineInfo` → `AgentInfo`
  - Update route: `/pipeline-info` → `/agents`
  
- [ ] Rename `PipelineInfoServiceIntegrationTest.java` → `AgentInfoServiceIntegrationTest.java`
  - Update service class reference
  - Update endpoint URIs
  - Update `PipelineInfo` → `AgentInfo`
  
- [ ] Rename `DynamicPipelineManagerTest.java` → `DynamicAgentManagerTest.java`
  - Update manager class reference
  - Update method names: `addDynamicPipeline()` → `addDynamicAgent()`, etc.
  - Update `PipelineInfo` → `AgentInfo`

---

### 8.2 Integration Test Updates

**File:** `LLMAdapterIntegrationTest.java`

**Tasks:**
- [ ] Update variable names from `pipeline` to `agent` where appropriate
- [ ] Update logging assertions
- [ ] Update `PromptPipelineBuilder` → `AgentBuilder` references
- [ ] Keep test logic unchanged (adapter behavior unchanged)

**File:** `FileSystemWorkflowIntegrationTest.java`

**Tasks:**
- [ ] Update references to `PromptPipelineConfiguration` → `AgentConfiguration`
- [ ] Update `DynamicPipelineManager` → `DynamicAgentManager`
- [ ] Update `PipelineInfo` → `AgentInfo` in assertions

**File:** `BuilderPatternIntegrationTest.java`

**Tasks:**
- [ ] Update `PromptPipelineBuilder` → `AgentBuilder`
- [ ] Update interface names
- [ ] Update variable names for clarity

**File:** `PromptConfigurationTest.java`

**Tasks:**
- [ ] Update `getPromptChains()` → `getAgentWorkflows()`
- [ ] Update `PromptChain` → `AgentWorkflow`
- [ ] Update `pc.chain()` → `wf.agents()` in assertions

---

## Phase 9: Documentation & Cleanup

### 9.1 Update README.md

**File:** `README.md`

**Search & Replace:**
- [ ] "prompt chain" → "agent workflow" (where referring to YAML grouping)
- [ ] "Pipeline Agent Configuration" → "Agent Configuration"
- [ ] "multiple chains in configuration" → "multiple workflows in configuration"
- [ ] Update section "Prompt Graphs" to clarify agent vs workflow terminology
- [ ] Keep "pipeline" only when referring to data flow concept (not code)

---

### 9.2 Update AGENTS.md

**File:** `AGENTS.md`

**Tasks:**
- [ ] Update LLM Adapters section:
  - "prompt chain" → "agent workflow"
  - "PromptPipelineConfigurator" → "AgentConfigurator"
- [ ] Update SplitterLLMAdapter Architecture section
- [ ] Keep adapter descriptions (unchanged behavior)

---

### 9.3 Update ADRs

**Directory:** `docs/`

**Files to check:**
- [ ] `adr-dynamic-scanners.md` - update `PromptPipelineConfigurator` → `AgentConfigurator`
- [ ] Any other ADRs referencing renamed classes

---

### 9.4 Update Project Documentation

**Files:**
- [ ] `project/docs/software-arch.md` - update class references
- [ ] `project/plans/observability-plan.md` - update span names
- [ ] `project/state/source-file-checklist.md` - update file names

---

### 9.5 Code Comments & Javadoc

**Global Search:**
- [ ] Search for "pipeline configuration" → "agent configuration" (where appropriate)
- [ ] Search for "prompt chain" → "agent workflow"
- [ ] Update any inline comments referencing old terminology
- [ ] Keep "pipeline" when referring to reactive streams/data flow

---

## Implementation Order

1. **Phase 1:** Domain model (`PromptChain` → `AgentWorkflow`)
2. **Phase 2:** Infrastructure (Configurator, Builder, Configuration)
3. **Phase 3:** System prompt parsing (YAML reader updates)
4. **Phase 4:** REST layer (DTO, controller, endpoints)
5. **Phase 5:** Manager layer (`DynamicPipelineManager` → `DynamicAgentManager`)
6. **Phase 6:** UI layer (Service, View)
7. **Phase 7:** YAML files (directory rename, structure update)
8. **Phase 8:** Tests (all unit and integration tests)
9. **Phase 9:** Documentation (README, ADRs, comments)

---

## Breaking Changes Summary

| Change | Impact | Mitigation |
|--------|--------|------------|
| REST endpoint: `/api/pipelines` → `/api/agents` | API clients break | Update UI in same deployment |
| Route: `/pipeline-info` → `/agents` | Bookmarks break | Internal tool, communicate change |
| YAML: `chain:` → `agents:` | Config files break | All configs internal, update together |
| Directory: `prompt-chains/` → `agent-workflows/` | External references break | None expected (internal) |
| Java API: Class renames | Code references break | Internal codebase only |

---

## Decisions Made

1. ✅ Rename directory: `prompt-chains/` → `agent-workflows/`
2. ✅ Change REST endpoint: `/api/pipelines` → `/api/agents`
3. ✅ Change route: `/pipeline-info` → `/agents`
4. ✅ No migration utility (fail fast with clear error)
5. ✅ Leave `PromptRequest`/`PromptResponse` unchanged
6. ✅ Rename field `agentDefinition` → `definition` in `AgentInfo` (shorter)

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking API endpoints | Medium | Update UI clients in same PR, no external clients |
| YAML config breakage | Low | All configs are internal, update together |
| Test failures during refactor | High | Run tests after each phase, incremental commits |
| Route changes break bookmarks | Low | Internal tool, can communicate to team |
| Naming inconsistencies | Medium | Comprehensive search & replace, code review |
| Missed references | Medium | IDE refactor tools, global search before commit |

---

## Verification Checklist

After implementation, verify:

- [ ] `./mvnw clean install` succeeds
- [ ] `./mvnw test -q` passes all tests
- [ ] `./mvnw spring-boot:run` starts successfully
- [ ] Agents load from YAML at startup
- [ ] `/api/agents` endpoint returns agent list
- [ ] `/agents` route displays agent grid
- [ ] Create new agent via UI works
- [ ] Delete agent via UI works
- [ ] File processing still works end-to-end
- [ ] No `Pipeline` or `Chain` references remain (except in history/comments)

---

## Rollback Plan

If issues arise:

1. Git revert all changes (atomic commit recommended)
2. Restore previous working version
3. Analyze failures
4. Consider phased rollout:
   - Option A: Keep both old and new endpoints temporarily (doubles code)
   - Option B: Smaller batches of changes with more verification points

---

## Future Enhancements (Out of Scope)

After this refactor, consider:

1. **Agent relationship visualization in UI:**
   - Show which agents' outputs match which agents' inputs
   - Graph view of agent workflows
   - Trace file processing through agent chain

2. **Workflow templates:**
   - Pre-defined agent workflows users can instantiate
   - Import/export workflows as YAML

3. **Agent metrics:**
   - Files processed per agent
   - Average LLM response time per agent
   - Error rates by agent

4. **Dynamic agent composition:**
   - UI to create workflows by connecting agents
   - Save workflow as YAML automatically

---

## Notes

- This is a **refactoring** only - no behavioral changes
- All tests should pass with same assertions (just renamed types)
- LLM adapter behavior unchanged
- File I/O contracts unchanged
- Reactive stream behavior unchanged
- Only terminology and structure improved for clarity

---

## Estimated Effort

| Phase | Estimated Time |
|-------|----------------|
| Phase 1: Domain Model | 30 min |
| Phase 2: Infrastructure | 1 hour |
| Phase 3: System Prompt Config | 1 hour |
| Phase 4: REST Layer | 1 hour |
| Phase 5: Manager Layer | 1 hour |
| Phase 6: UI Layer | 1.5 hours |
| Phase 7: YAML Files | 30 min |
| Phase 8: Tests | 2-3 hours |
| Phase 9: Documentation | 1 hour |
| **Total** | **~10 hours** |

---

## Success Criteria

- ✅ All code compiles without errors
- ✅ All tests pass
- ✅ Application starts and loads agents from YAML
- ✅ UI displays agents correctly
- ✅ Create/delete agents via UI works
- ✅ File processing end-to-end works
- ✅ No references to old terminology in active code
- ✅ Code is clearer and more maintainable
