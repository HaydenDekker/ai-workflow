# DPR: Scanner Concept

---

## Purpose

This document explains what scanners are, how they work, their lifecycle, and how rate limiting controls file read behavior. It is a design note — the architectural decision to use dynamic multi-scanners is captured in the related ADR.

---

## What Is a Scanner?

A scanner watches a **single directory** on the file system and emits a reactive stream of file change events. Each scanner:

- Uses Java's `WatchService` (via Spring Integration's file inbound adapter) to detect file changes
- Wraps events in `FileHistory` objects (see [DPR: File History Model](dpr-file-history-model.md))
- Applies rate limiting to control consumption speed
- Shares its `Flux<FileHistory>` with all agents subscribed to that folder

### Scanner Responsibilities

1. **Watch**: Monitor a folder for file system events (CREATE, MODIFY, DELETE)
2. **Collect**: Convert raw watch events into `FileHistory` objects with metadata
3. **Filter**: Skip files that haven't changed (hash comparison against stored state)
4. **Rate-limit**: Control the pace of file reads to prevent memory exhaustion
5. **Share**: Broadcast the same `Flux<FileHistory>` to all subscribing agents

---

## How Scanners Work

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  WatchService (OS-level)                                     │
│  └── Detects: CREATE, MODIFY, DELETE events                 │
│       immediately on file change                              │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  Spring Integration File Channel                              │
│  └── Converts WatchService events → messages                  │
│       (FileMetadata objects)                                  │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  Flux<FileHistory> Pipeline                                   │
│  └── Convert → Filter (hash) → Rate-limit → Share            │
│                                                                │
│  sourceFlux                                                    │
│    .map(m → FileMetadata)                                     │
│    .map(fileComparator::matches)                               │
│    .filter(fh → !fh.hashMatches())                            │
│    .delayElements(5s)                                         │
│    .share()                                                   │
└──────────────────────┬───────────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
     Agent-1      Agent-2      Agent-3
     (filter)     (filter)     (filter)
```

### Data Flow

1. **WatchService** detects a file change immediately (event-driven, no polling)
2. **Spring Integration** converts the event into a `FileMetadata` message
3. **Flux pipeline** processes the event:
   - Converts `FileMetadata` to `FileHistory`
   - Compares file hash against stored state (skips unchanged files)
   - Applies rate limiting (5-second delay between reads)
   - Shares the resulting `Flux` with all subscribers
4. **Agents** subscribe to the shared flux and filter further using their `fileInputRegex`

---

## Scanner Lifecycle

A scanner has three states:

| State | Description |
|-------|-------------|
| **Created** | `WatchService` is initialized but no agents are subscribed |
| **Active** | At least one agent is subscribed; scanner is processing events |
| **Destroyed** | Last agent unsubscribed; `WatchService` and Spring Integration flow disposed |

### Lifecycle Transitions

```
Created ──(first subscribe)──► Active ──(last unsubscribe)──► Destroyed
    ▲                                                     │
    │                                                     ▼
    └───────────(re-subscribe)────── Active               (irreversible)
```

**Key decisions**:
- Scanners are **created on first agent subscription** (lazy initialization)
- Scanners are **destroyed immediately when the last agent unsubscribes** (no delay)
- If an agent is recreated after scanner destruction, a **new scanner instance** is created
- On application shutdown, all active scanners are disposed via a `@PreDestroy` hook

### Scanner Metadata

Each scanner instance is tracked in `ScannerRegistry` with the following metadata:

| Field | Type | Description |
|-------|------|-------------|
| `fileScanner` | `FileScanner` | The active scanner instance |
| `subscribedAgentIds` | `Set<String>` | IDs of agents currently subscribed |
| `disposable` | `Disposable` | Spring Integration flow reference for disposal |
| `flux` | `Flux<FileHistory>` | Shared reactive stream for agents |

---

## File Read Rate Control

### Why Rate Limit?

When watching folders with many files (e.g., 1000+ files), immediate processing of every file system event can cause memory exhaustion. Rate limiting controls the consumption rate independently of the detection rate.

### How It Works

- **WatchService** detects file changes **immediately** (event-driven)
- **Reactor Flux** controls the **consumption rate** with `delayElements()`
- **Default delay**: 5 seconds between file reads
- **No batching**: Files are processed one-at-a-time with controlled spacing

### Implementation

```java
Flux<FileHistory> sourceFlux = IntegrationReactiveUtils.messageChannelToFlux(filesChannel)
    .map(m -> { /* convert to FileMetadata */ })
    .map(fileComparator::matches)
    .filter(fh -> !fh.hashMatches())
    .delayElements(Duration.ofSeconds(5))  // Rate limit: 5s between reads
    .share();
```

### Behavior

| Scenario | Behavior |
|----------|----------|
| 1 file change | Read after 5s delay |
| 100 file changes in 1s | Queued; read one every 5s |
| Continuous rapid changes | Steady state: one file every 5s |
| 1000+ files in folder | Processed sequentially at controlled rate |

### Benefits

- ✅ WatchService provides immediate notification of file changes
- ✅ No memory explosion with large folders (files read at controlled rate)
- ✅ Backpressure naturally propagates to downstream agents
- ✅ Simple configuration (single delay parameter, no complex batching logic)

### Trade-offs

- ⚠️ Files are not read instantly when discovered (5s delay is intentional)
- ⚠️ High-frequency file changes may queue up (acceptable for most use cases)
- ⚠️ Cannot process 1000 files in parallel (by design to prevent overload)

---

## Scanner Configuration

### Default Settings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `delayBetweenReads` | `5s` | Seconds between file reads |
| `maxConcurrentReads` | `1` | Files processed one-at-a-time |
| `watchServicePollTimeout` | `OS default` | How long WatchService blocks before re-checking |

### Customizing Delay

```java
// Use default 5-second delay
FileScanner scanner = factory.createScanner("/project/src", "scanner-1");

// Custom delay (optional)
FileScanner scanner = factory.createScanner("/project/src", "scanner-2", Duration.ofSeconds(10));
```

---

## See Also

- [DPR: File History Model](dpr-file-history-model.md) — How file events are modeled and hashed
- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanners
- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanners
