# ADR-006: Dynamic Multi-Scanner Architecture

**Status**: Accepted  
**Date**: 2026-04-28  
**Related DPRs**: [Scanner Concept](../docs/dpr-scanner-concept.md), [File History Model](../docs/dpr-file-history-model.md), [Agent-Scanner Relationship](../docs/dpr-agent-scanner-relationship.md)

---

## Context

The original architecture used a single root URL configured in `application.yml` to start one file system scanner. All agents subscribed to this shared `Flux<FileHistory>` and filtered events using their `fileInputRegex`. This approach had limitations:

- **Single watch root**: Cannot monitor multiple independent file systems
- **No isolation**: All agents receive all file events, filtering happens downstream
- **Static configuration**: Scanner root cannot be changed without restarting
- **No dynamic lifecycle**: Scanner lifecycle tied to application startup/shutdown

## Decision

We implemented a **dynamic multi-scanner architecture** where:

1. **N independent file scanners** can be created and destroyed at runtime via REST API
2. **Agent `fileInputRegex` defines scanner assignment**: Each agent's regex must contain a `folderPattern` named group specifying which folder(s) to watch
3. **One scanner per unique folder**: Multiple agents watching the same folder share a single scanner instance
4. **Agents can subscribe to multiple scanners**: If an agent's regex matches multiple folder patterns, it subscribes to all matching scanners
5. **Dynamic lifecycle**: Scanners are created on first agent subscription and destroyed when the last agent unsubscribes

## Alternatives Considered

### Alternative 1: Static Multi-Scanner Configuration

Define multiple scanners in `application.yml` at startup.

**Rejected**: Doesn't support dynamic creation/destruction; requires restart to change configuration.

### Alternative 2: Agent-Specific Scanners (No Sharing)

Create a new scanner for each agent, even if they watch the same folder.

**Rejected**: Wasteful resource usage; multiple WatchServices on same folder cause conflicts.

### Alternative 3: Global Scanner with Explicit Assignment

Keep single scanner, but add `scannerId` field to `AgentDefinition`.

**Rejected**: Doesn't solve the root problem of single watch root; still requires restart to change scanner configuration.

### Alternative 4: Database-Backed Scanner Configuration

Store scanner configurations in database with CRUD operations.

**Rejected**: Over-engineering for current needs; in-memory registry is sufficient for startup-time scanner state.

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

### Open Questions

1. **Wildcard expansion**: How to handle `folderPattern` like `*/src`? **Decision**: Require explicit absolute paths for now.
2. **Error handling**: Inaccessible folder during agent creation. **Decision**: Fail fast with clear error message.
3. **Scanner cleanup delay**: Delay destruction after last unsubscribe? **Decision**: No delay; immediate cleanup.
4. **Graceful shutdown**: Scanner disposal on application shutdown. **Decision**: `@PreDestroy` hook to dispose all scanners.

## Rollback

If issues arise, revert to single-scanner mode by disabling `ScannerRegistry` and using original `FileSystemRecursiveFileScannerAdapter` bean. Existing database schema unchanged — no data migration needed.

---

**Author**: AI Workflow Team  
**Last Updated**: 2026-04-28
