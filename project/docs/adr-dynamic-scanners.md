# ADR: Dynamic Multi-Scanner Architecture with Agent Subscriptions

## Status

**Proposed** - Ready for implementation

## Context

The current architecture uses a single root URL configured in `application.yml` to start one file system scanner. All agents subscribe to this shared `Flux<FileHistory>` and filter events using their `fileInputRegex`. This approach has limitations:

- **Single watch root**: Cannot monitor multiple independent file systems
- **No isolation**: All agents receive all file events, filtering happens downstream
- **Static configuration**: Scanner root cannot be changed without restarting the application
- **No dynamic lifecycle**: Scanner lifecycle is tied to application startup/shutdown

## Decision

We will implement a **dynamic multi-scanner architecture** where:

1. **N independent file scanners** can be created and destroyed at runtime via REST API
2. **Agent `fileInputRegex` defines scanner assignment**: Each agent's regex must contain a `folderPattern` named group that specifies which folder(s) to watch
3. **One scanner per unique folder**: Multiple agents watching the same folder share a single scanner instance
4. **Agents can subscribe to multiple scanners**: If an agent's regex matches multiple folder patterns, it subscribes to all matching scanners
5. **Dynamic lifecycle**: Scanners are created when the first agent subscribes and destroyed when the last agent unsubscribes

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ REST API Layer                                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ POST /api/agents (with folderPattern in fileInputRegex)              │ │
│ │ GET  /api/scanners (list active scanners)                               │ │
│ │ DELETE /api/agents/{id} (triggers scanner cleanup if empty)          │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ ScannerRegistry (NEW)                                                      │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ Map<String, ScannerMetadata> where key = absolute folder path          │ │
│ │                                                                         │ │
│ │ ScannerMetadata:                                                        │ │
│ │   - FileScanner instance                                                │ │
│ │   - Set<String> subscribedAgentIds                                      │ │
│ │   - Disposable (for Spring Integration flow disposal)                   │ │
│ │   - Flux<FileHistory> (shared per scanner, rate-limited)               │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ ScannerFactory (NEW)                                                       │
│ - Creates FileSystemRecursiveFileScannerAdapter instances                  │
│ - Manages Spring Integration flow registration with unique IDs             │
│ - Applies rate limiting (delayElements) to control file read rate          │
│ - Handles flow disposal on scanner removal                                 │
└──────────────────────────┬──────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Multiple FileSystemRecursiveFileScannerAdapter instances                   │
│ - Scanner-1: /projectA/src → Flux-1 (rate-limited)                         │
│ - Scanner-2: /projectB/src → Flux-2 (rate-limited)                         │
│ - Scanner-3: /projectC/src → Flux-3 (rate-limited)                         │
└──────────────────────────┬──────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ DynamicAgentManager (MODIFIED)                                          │
│ - Parses folderPattern from agent's fileInputRegex                         │
│ - Subscribes agent to appropriate scanner(s) via ScannerRegistry           │
│ - Tracks agent → scanner mappings in AgentRegistryEntry                 │
│ - On agent removal: unsubscribes agent, destroys empty scanners         │
└──────────────────────────┬──────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ Agents (Multiple per scanner)                                              │
│ - Agent-1: subscribes to Scanner-1, filters by full fileInputRegex         │
│ - Agent-2: subscribes to Scanner-2, filters by full fileInputRegex         │
│ - Agent-3: subscribes to Scanner-1 & Scanner-2 (multi-scanner agent)       │
└─────────────────────────────────────────────────────────────────────────────┘
```
### Data Flow

1. **Agent Creation**:
    ```
    POST /api/agents
    {
      "fileInputRegex": "(?P<folderPattern>.*/src)/.*\\.java",
      "title": "JavaProcessor",
      ...
    }
    
    ↓
    RegexParser extracts folderPattern group → expands to concrete paths
    ↓
    For each unique folder path:
      - ScannerRegistry.getOrCreateScanner("/projectA/src")
      - If scanner doesn't exist: ScannerFactory creates new instance
      - Register agent subscription
    ↓
    DynamicAgentManager.configure(agent, scannerFlux)
    ↓
    Agent subscribes to filtered Flux<FileHistory>
    ```

2. **Agent Removal**:
    ```
    DELETE /api/agents/{id}
    
    ↓
    DynamicAgentManager looks up agent's scanner paths
    ↓
    For each scanner path:
      - Unsubscribe agent from ScannerRegistry
      - If subscription count == 0: destroy scanner
    ↓
    Dispose agent's subscription
    ```

### Regex Format Specification

Agents must define a `folderPattern` named group in their `fileInputRegex`:

```json
{
  "fileInputRegex": "(?P<folderPattern>.*/src)/(?P<path>.*)/(?P<name>[^/]+)\\.(?P<ext>java)",
  "title": "JavaProcessor",
  "body": "Process Java files...",
  "agentType": "Map",
  "outputStructure": "Generate documentation...",
  "outputFilenameTemplate": "docs/${name}.md"
}
```

**Requirements**:
- `folderPattern` named group is **mandatory** (agent creation fails without it)
- `folderPattern` must match a valid absolute or relative folder path
- The full regex is still used for filtering events after scanner subscription

**Examples**:

| Regex | folderPattern Match | Resulting Scanner |
|-------|---------------------|-------------------|
| `(?P<folderPattern>/home/user/projectA)/.*\\.java` | `/home/user/projectA` | One scanner at `/home/user/projectA` |
| `(?P<folderPattern>.*/src)/.*\\.java` | Multiple matches | One scanner per unique `/project/src` found |
| `(?P<folderPattern>/shared)/.*\\.java` | `/shared` | One scanner at `/shared` |

### File Metadata Storage

File metadata continues to use **absolute paths** as unique keys in `FileMetadataEntity.url`. This ensures:
- No collisions between scanners watching different folders with same relative paths
- Existing database schema requires no changes
- Hash comparison works correctly across scanner boundaries

### File Read Rate Control

To prevent memory exhaustion when watching folders with many files (e.g., 1000+ files), each scanner applies **rate limiting** using Reactor's `delayElements()` operator.

**Architecture**:
- **WatchService** detects file changes immediately (event-driven)
- **Reactor Flux** controls the consumption rate with `delayElements()`
- **Default delay**: 5 seconds between file reads
- **No batching**: Files are processed one-at-a-time with controlled spacing

**Implementation**:
```java
Flux<FileHistory> sourceFlux = IntegrationReactiveUtils.messageChannelToFlux(filesChannel)
    .map(m -> { /* convert to FileMetadata */ })
    .map(fileComparator::matches)
    .filter(fh -> !fh.hashMatches())
    .delayElements(Duration.ofSeconds(5))  // Rate limit: 5s between reads
    .share();
```

**Benefits**:
- ✅ WatchService provides immediate notification of file changes
- ✅ No memory explosion with large folders (files read at controlled rate)
- ✅ Backpressure naturally propagates to downstream agents
- ✅ Simple configuration (single delay parameter, no complex batching logic)

**Trade-offs**:
- ⚠️ Files are not read instantly when discovered (5s delay is intentional)
- ⚠️ High-frequency file changes may queue up (acceptable for most use cases)
- ⚠️ Cannot process 1000 files in parallel (by design to prevent overload)

## Consequences

### Positive

1. **True multi-project support**: Can monitor multiple independent file systems simultaneously
2. **Resource efficiency**: Only scan folders that agents actually need
3. **Dynamic lifecycle**: Add/remove scanners without application restart
4. **Isolation**: Scanner failures don't cascade to unrelated agents
5. **Flexibility**: Agents can monitor multiple folders with a single pipeline

### Negative

1. **Increased complexity**: More moving parts (registry, factory, lifecycle management)
2. **Regex migration required**: Existing agents must add `folderPattern` to their regex
3. **Potential for orphan scanners**: If application crashes, scanners may not be cleaned up (mitigated by graceful shutdown hook)
4. **Memory overhead**: Each scanner maintains its own Flux and Spring Integration flow

### Neutral

1. **Backward compatibility**: Existing YAML agents require migration or fallback mechanism
2. **Testing complexity**: Need integration tests for scanner lifecycle, not just unit tests

## Migration Path

### Phase 1: Core Infrastructure (Week 1)

1. Create `ScannerRegistry.java` - manages scanner instances and subscriptions
2. Create `ScannerFactory.java` - creates/destroys scanner instances dynamically
3. Create `RegexParser.java` - extracts `folderPattern` from agent regex
4. Refactor `FileSystemRecursiveFileScannerAdapter` to accept constructor parameters (non-`@Component`)

### Phase 2: Integration (Week 2)

5. Modify `DynamicAgentManager` to use `ScannerRegistry` instead of single `FileScanner`
6. Modify `AgentConfigurator` to accept scanner-specific Flux
7. Extend `AgentRegistryEntry` to track `Set<String> scannerPaths`
8. Implement graceful shutdown hook for scanner cleanup

### Phase 3: REST API & Testing (Week 3)

9. Create `ScannerRestController` for scanner management
10. Extend `AgentInfo` DTO with `scannerPaths` field
11. Write integration tests for scanner lifecycle
12. Add validation for `folderPattern` in agent creation

### Phase 4: Migration & Documentation (Week 4)

13. Update YAML agent configs with `folderPattern`
14. Add fallback for legacy agents (optional: use default scanner)
15. Update API documentation
16. Write operational runbook for scanner troubleshooting

## Rollback Plan

If issues arise:
1. Revert to single-scanner mode by disabling `ScannerRegistry` and using original `FileSystemRecursiveFileScannerAdapter` bean
2. Existing database schema unchanged - no data migration needed
3. Agent definitions can be rolled back to simple regex (without `folderPattern`)

## Alternatives Considered

### Alternative 1: Static Multi-Scanner Configuration

Define multiple scanners in `application.yml` at startup:

```yaml
scanners:
  - id: "scanner-1"
    url: file:/projectA
  - id: "scanner-2"
    url: file:/projectB
```

**Rejected**: Doesn't support dynamic creation/destruction; requires restart to change configuration.

### Alternative 2: Agent-Specific Scanners (No Sharing)

Create a new scanner for each agent, even if they watch the same folder.

**Rejected**: Wasteful resource usage; multiple WatchServices on same folder cause conflicts.

### Alternative 3: Global Scanner with Explicit Assignment

Keep single scanner, but add `scannerId` field to `AgentDefinition`:

```json
{
  "scannerId": "scanner-1",
  "fileInputRegex": ".*\\.java",
  ...
}
```

**Rejected**: Doesn't solve the root problem of single watch root; still requires restart to change scanner configuration.

### Alternative 4: Database-Backed Scanner Configuration

Store scanner configurations in database with CRUD operations.

**Rejected**: Over-engineering for current needs; in-memory registry is sufficient for startup-time scanner state.

## Technical Specifications

### ScannerRegistry API

```java
public interface ScannerRegistry {
    Flux<FileHistory> getOrCreateScanner(String folderPath);
    void subscribeAgent(String folderPath, String agentId);
    void unsubscribeAgent(String folderPath, String agentId);
    Optional<ScannerMetadata> getScanner(String folderPath);
    List<ScannerSummary> listScanners();
    void shutdown();
}
```

### ScannerFactory API

```java
public interface ScannerFactory {
    Duration DEFAULT_DELAY_BETWEEN_READS = Duration.ofSeconds(5);
    
    FileScanner createScanner(String folderPath, String scannerId);
    FileScanner createScanner(String folderPath, String scannerId, Duration delayBetweenReads);
    void destroyScanner(String scannerId);
}
```

**Usage**:
```java
// Use default 5-second delay
FileScanner scanner = factory.createScanner("/project/src", "scanner-1");

// Custom delay (optional)
FileScanner scanner = factory.createScanner("/project/src", "scanner-2", Duration.ofSeconds(10));
```

### RegexParser API

```java
public interface RegexParser {
    boolean hasFolderPattern(String regex);
    Set<String> extractFolderPaths(String regex);
    String validateFolderPattern(String regex);
}
```

### DynamicAgentManager Modifications

```java
public class DynamicAgentManager {
    
    private final ScannerRegistry scannerRegistry;
    
    public AgentInfo addDynamicAgent(AgentDefinition def) {
        Set<String> scannerPaths = RegexParser.extractFolderPaths(def.fileInputRegex());
        // Subscribe to scanners, create agents, track mappings
    }
    
    public void removeAgent(String id) {
        AgentRegistryEntry entry = agentRegistry.get(id);
        entry.scannerPaths().forEach(path -> 
            scannerRegistry.unsubscribeAgent(path, id));
        // Destroy empty scanners
    }
}
```

## Open Questions

1. **Wildcard expansion**: How to handle `folderPattern` like `*/src`? Should we scan all matching folders at runtime, or require explicit paths?
   
   **Decision**: Require explicit absolute paths for now. Wildcard expansion can be added later.

2. **Error handling**: If a folder is inaccessible, should we fail agent creation or create scanner in error state?
   
   **Decision**: Fail fast with clear error message during agent creation.

3. **Scanner cleanup delay**: Should we delay scanner destruction after last unsubscribe?
   
   **Decision**: No delay; immediate cleanup to free resources. If agent is recreated, new scanner will be created.

4. **Graceful shutdown**: How to handle scanner disposal on application shutdown?
   
   **Decision**: Implement `@PreDestroy` hook to dispose all scanners gracefully before shutdown.

## References

- Current implementation: `FileSystemRecursiveFileScannerAdapter.java`
- Spring Integration File Inbound Adapter documentation
- Reactor Flux sharing and backpressure handling
- Reactor `delayElements()` operator: Controls rate of element emission
- AGENTS.md: Build and testing conventions

---

**Author**: AI Workflow Team  
**Date**: 2025-01-10  
**Last Updated**: 2026-04-16
