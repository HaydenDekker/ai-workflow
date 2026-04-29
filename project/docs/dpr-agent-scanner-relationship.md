# DPR: Agent-Scanner Relationship

**Related ADR**: [ADR-006: Dynamic Multi-Scanner Architecture](../adrs/adr-006-dynamic-scanners.md)

---

## Purpose

This document describes how agents subscribe to scanners, the `ScannerRegistry` and `ScannerFactory` APIs, how `RegexParser` extracts folder patterns from agent definitions, and the dynamic lifecycle of scanner-subscription relationships.

---

## Agent Subscription Model

### Relationship Types

| Type | Description | Example |
|------|-------------|---------|
| **One-to-one** | One agent subscribes to one scanner | Agent watches `/projectA/src` only |
| **One-to-many** | One agent subscribes to multiple scanners | Agent watches `/projectA/src` and `/projectB/src` |
| **Many-to-one** | Multiple agents share one scanner | Two agents both watch `/projectA/src` |

### Subscription Flow

```
Agent Created (POST /api/agents)
        │
        ▼
RegexParser extracts folderPattern group
        │
        ▼
For each unique folder path:
        │
        ├── ScannerRegistry.getOrCreateScanner(path)
        │       ├── Scanner exists? → Return existing flux
        │       └── Scanner doesn't exist? → Factory creates it
        │
        ├── Register agent subscription
        │       ├── Add agent ID to subscribedAgentIds
        │       └── Track agent → scanner mapping
        │
        └── Agent subscribes to filtered Flux<FileHistory>
```

### Unsubscription Flow

```
Agent Removed (DELETE /api/agents/{id})
        │
        ▼
For each scanner path the agent was subscribed to:
        │
        ├── Unsubscribe agent from ScannerRegistry
        │       └── Remove agent ID from subscribedAgentIds
        │
        ├── Check subscription count
        │       ├── count > 0 → Keep scanner alive
        │       └── count == 0 → Destroy scanner
        │
        └── Dispose agent's subscription
```

---

## ScannerRegistry API

The `ScannerRegistry` manages scanner instances and tracks agent subscriptions per scanner:

```java
public interface ScannerRegistry {

    /**
     * Get an existing scanner's flux or create a new one.
     * Registers the agent subscription if not already registered.
     *
     * @param folderPath absolute folder path to watch
     * @param agentId    ID of the subscribing agent
     * @return shared Flux<FileHistory> for this scanner
     */
    Flux<FileHistory> getOrCreateScanner(String folderPath, String agentId);

    /**
     * Unregister an agent subscription.
     * Destroys the scanner if this was the last subscription.
     *
     * @param folderPath absolute folder path
     * @param agentId    ID of the unsubscribing agent
     */
    void unsubscribeAgent(String folderPath, String agentId);

    /**
     * Get metadata for an active scanner.
     */
    Optional<ScannerMetadata> getScanner(String folderPath);

    /**
     * List all active scanners with summary info.
     */
    List<ScannerSummary> listScanners();

    /**
     * Shutdown all scanners gracefully.
     */
    void shutdown();
}
```

### ScannerMetadata

```java
public record ScannerMetadata(
    FileScanner fileScanner,
    Set<String> subscribedAgentIds,
    Disposable disposable,
    Flux<FileHistory> flux
) {}
```

### ScannerSummary

```java
public record ScannerSummary(
    String folderPath,
    int subscriptionCount,
    boolean isActive
) {}
```

---

## ScannerFactory API

The `ScannerFactory` creates and destroys scanner instances:

```java
public interface ScannerFactory {

    Duration DEFAULT_DELAY_BETWEEN_READS = Duration.ofSeconds(5);

    /**
     * Create a scanner with default delay.
     *
     * @param folderPath absolute folder path to watch
     * @param scannerId  unique identifier for this scanner
     * @return configured FileScanner instance
     */
    FileScanner createScanner(String folderPath, String scannerId);

    /**
     * Create a scanner with custom delay.
     */
    FileScanner createScanner(String folderPath, String scannerId, Duration delayBetweenReads);

    /**
     * Destroy a scanner, disposing its WatchService and Spring Integration flow.
     */
    void destroyScanner(String scannerId);
}
```

### Scanner Creation Steps

1. **Initialize WatchService** on the target folder
2. **Create Spring Integration flow** with unique ID:
   - File inbound adapter → message channel
   - Channel converted to `Flux<FileHistory>`
3. **Apply rate limiting** via `delayElements()`
4. **Share the flux** for multi-agent subscription
5. **Register in ScannerRegistry** with metadata

### Scanner Destruction Steps

1. **Dispose Spring Integration flow** (unregisters inbound adapter)
2. **Close WatchService** (releases OS-level file handle)
3. **Remove from ScannerRegistry** (clears metadata)

---

## RegexParser

The `RegexParser` extracts the `folderPattern` named group from an agent's `fileInputRegex`:

```java
public interface RegexParser {

    /**
     * Check if the regex contains a folderPattern named group.
     */
    boolean hasFolderPattern(String regex);

    /**
     * Extract all unique folder paths matched by the folderPattern group.
     * For agent creation, returns the group definition (not resolved paths).
     */
    Set<String> extractFolderPaths(String regex);

    /**
     * Validate that the regex has a valid folderPattern group.
     * Returns error message if invalid, null if valid.
     */
    String validateFolderPattern(String regex);
}
```

### Regex Format Specification

Agents **must** define a `folderPattern` named group in their `fileInputRegex`:

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

### Requirements

- `folderPattern` named group is **mandatory** — agent creation fails without it
- `folderPattern` must match a valid absolute or relative folder path
- The full regex is still used for filtering events after scanner subscription

### Examples

| Regex | folderPattern Match | Resulting Scanner |
|-------|---------------------|-------------------|
| `(?P<folderPattern>/home/user/projectA)/.*\.java` | `/home/user/projectA` | One scanner at `/home/user/projectA` |
| `(?P<folderPattern>.*/src)/.*\.java` | Multiple matches | One scanner per unique `/project/src` found |
| `(?P<folderPattern>/shared)/.*\.java` | `/shared` | One scanner at `/shared` |

### Dynamic Lifecycle with Regex

When a regex matches multiple folders, the agent subscribes to **one scanner per unique folder**:

```
Regex: (?P<folderPattern>.*/src)/.*\.java
        │
        ▼
RegexParser extracts folderPattern group
        │
        ▼
Matches found at runtime:
  - /projectA/src
  - /projectB/src
  - /projectC/src
        │
        ▼
Agent subscribes to 3 scanners:
  - Scanner-1: /projectA/src
  - Scanner-2: /projectB/src
  - Scanner-3: /projectC/src
```

---

## DynamicAgentManager Integration

The `DynamicAgentManager` coordinates between agents, scanners, and the registry:

```java
public class DynamicAgentManager {

    private final ScannerRegistry scannerRegistry;
    private final AgentRegistry agentRegistry;
    private final RegexParser regexParser;

    public AgentInfo addDynamicAgent(AgentDefinition def) {
        // 1. Validate regex has folderPattern
        String validationError = regexParser.validateFolderPattern(def.fileInputRegex());
        if (validationError != null) {
            throw new IllegalArgumentException("Invalid regex: " + validationError);
        }

        // 2. Extract folder paths from regex
        Set<String> scannerPaths = regexParser.extractFolderPaths(def.fileInputRegex());

        // 3. Subscribe to each scanner
        List<Flux<FileHistory>> scannerFluxes = new ArrayList<>();
        for (String path : scannerPaths) {
            scannerFluxes.add(scannerRegistry.getOrCreateScanner(path, def.id()));
        }

        // 4. Create agent with combined flux
        // ... (agent creation logic)

        // 5. Track agent → scanner mappings
        AgentRegistryEntry entry = new AgentRegistryEntry(
            def, scannerFluxes, scannerPaths
        );
        agentRegistry.put(def.id(), entry);

        return entry.toInfo();
    }

    public void removeAgent(String id) {
        AgentRegistryEntry entry = agentRegistry.get(id);
        if (entry == null) return;

        // 6. Unsubscribe from each scanner
        entry.scannerPaths().forEach(path ->
            scannerRegistry.unsubscribeAgent(path, id)
        );

        // 7. Dispose agent subscription
        // ...
    }
}
```

---

## Error Handling

### Inaccessible Folders

**Decision**: Fail fast with a clear error message during agent creation.

```java
if (!Files.isDirectory(Paths.get(folderPath))) {
    throw new IllegalArgumentException(
        "Folder does not exist or is not accessible: " + folderPath
    );
}
```

### Scanner Creation Failure

If a scanner cannot be created (e.g., permission denied), the agent creation fails with an error. The agent is **not** partially subscribed.

### Agent Re-subscription After Scanner Destruction

If an agent is removed (destroying its scanner) and then re-added, a **new scanner instance** is created. All file metadata is preserved from the database, so hash-based change detection continues correctly.

---

## See Also

- [DPR: Scanner Concept](dpr-scanner-concept.md) — Scanner lifecycle, rate limiting, architecture
- [DPR: File History Model](dpr-file-history-model.md) — FileHistory event model, hashing
- [ADR-006: Dynamic Multi-Scanner Architecture](../adrs/adr-006-dynamic-scanners.md) — Why dynamic multi-scanners were chosen
