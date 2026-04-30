# UCR-001: AI Workflow — System Use Case Catalog

**Status**: Active  
**Created**: 2026-04-29  
**Author**: AI Workflow Team

---

## Purpose

This document provides a **high-level catalog of all use cases** in the AI Workflow system. It answers the question **"what does this system do?"** — the functional scope that ADRs explain *why* we built it and DPRs explain *how* it works.

Use this catalog as a navigation aid when reading ADRs and DPRs. Each use case links to its relevant ADR and DPR for deeper context.

---

## Use Case Overview

The system is an **agentic file-driven workflow engine**. Users define agents that watch directories for file changes, process those files through LLM-powered pipelines, and write results back to the file system. A Vaadin/Hilla UI and REST API provide management, monitoring, and control.

### Use Case Categories

| # | Category | Description |
|---|----------|-------------|
| 1 | **Agent Lifecycle** | Create, read, update, delete, enable, disable, and refresh dynamic agents |
| 2 | **Agent Scanner** | Watch directories for file changes, detect new/modified files via hash comparison |
| 3 | **LLM Processing** | Send file content to LLMs, parse responses, write output to files |
| 4 | **Workflow Orchestration** | Chain agents together via file I/O contracts (output of one → input of another) |
| 5 | **Scanner Observability** | Track scanner metrics (file count, discovered, unchanged) and UI push updates |
| 6 | **LLM Health Monitoring** | Poll LLM endpoints on schedule, persist status, detect stale/WARN/DOWN states |
| 7 | **Persistence** | Persist agents, scanners, and file metadata to SQLite databases |
| 8 | **REST API** | Expose agent and scanner management operations via HTTP |
| 9 | **UI Management** | Visual agent/scanner/observability dashboards with real-time updates |

---

## Detailed Use Cases

### UC-01: Agent Lifecycle Management

**Goal**: Manage the full lifecycle of dynamic agents (create through destroy).

**Actors**: User (via REST API or UI)

**Preconditions**: System is running; database is initialized.

**Main Flow**:
1. User creates an agent by providing a title, prompt body, agent type, input regex, output template, and target directory.
2. System validates the target directory exists and is readable.
3. System creates a scanner for the agent (one-to-one relationship).
4. System builds a reactive processing pipeline (Flux) connecting the scanner to the LLM adapter and file writer.
5. System persists the agent to the database with `active=true`.
6. System subscribes to the flux, beginning file processing.

**Alternative Flows**:
- **Disable agent**: Dispose the flux subscription, persist `active=false`, move agent to dormant state. Scanner is **not** destroyed (allows quick re-enable).
- **Enable agent**: Re-create scanner, re-subscribe to flux, persist `active=true`.
- **Update agent**: Remove existing agent (destroy scanner, unsubscribe), then re-add with updated definition.
- **Refresh agent**: Dispose subscription, reset scanner to full-scan mode, re-subscribe.
- **Delete agent**: Dispose subscription, destroy scanner, delete from database.

**Postconditions**: Agent is in the requested state (active, dormant, deleted).

**Related ADRs**: ADR-003 (Vaadin/Hilla UI), ADR-007 (Multi-Database)  
**Related DPRs**: DPR: Agent-Scanner Relationship, DPR: Testing Strategy

---

### UC-02: Directory Scanning & Change Detection

**Goal**: Monitor a directory for file system events and detect new or modified files.

**Actors**: System (automated, triggered by file system events)

**Preconditions**: Scanner is created and attached to a directory.

**Main Flow**:
1. Scanner starts by performing an initial full scan of all files in the directory.
2. For each file, the system computes a SHA-256 hash and compares it against stored metadata.
3. **New or changed file**: Emit a `FileHistory` event through the reactive flux. Store the new hash.
4. **Unchanged file**: Skip the file; record the "unchanged" metric; transition scanner to `FILTERED` status.
5. Scanner enters watch mode, listening for CREATE/MODIFY/DELETE events.
6. On each event, repeat steps 2–4.
7. File emissions are throttled via a coalescing delay window to avoid redundant processing.

**Status Lifecycle**:
- `IDLE` → `EMITTING_INITIAL` → `EMITTING_UPDATES` / `FILTERED` → `IDLE`
- Any state → `ERROR` (on unrecoverable failure)

**Postconditions**: New/changed files are emitted for downstream processing; unchanged files are skipped.

**Related ADRs**: ADR-002 (REST Adapters)  
**Related DPRs**: DPR: Scanner Concept, DPR: Agent-Scanner Relationship, DPR: File History Model

---

### UC-03: LLM-Powered File Processing

**Goal**: Send file content to an LLM, receive a response, and write it to the file system.

**Actors**: System (automated, triggered by scanner events)

**Preconditions**: Agent is active; scanner has emitted a `FileHistory` event.

**Main Flow**:
1. Agent pipeline receives a `FileHistory` event containing file content and URL.
2. System wraps the content into a `PromptRequest` and sends it to the LLM via `ChatClient`.
3. LLM returns a response.
4. System applies the agent's output template to format the response.
5. System writes the formatted response to the target directory as a new file.

**Agent Types**:

| Agent Type | Behavior | Output |
|------------|----------|--------|
| **Map** (default) | One input → one LLM call → one output file | Single file |
| **Split** | LLM response contains `--- ItemKey ---` tokens | Multiple output files, one per split |
| **Reduce** | Aggregates multiple inputs into a single prompt → single output | Single aggregated file |

**Postconditions**: Response is written to the file system; the output file may trigger downstream agents.

**Related ADRs**: ADR-010 (llama.cpp)  
**Related DPRs**: (none yet — LLM adapter details are in the source code)

---

### UC-04: Workflow Orchestration via File I/O

**Goal**: Chain multiple agents together so that the output of one agent becomes the input of another.

**Actors**: System (automated, file-driven)

**Preconditions**: Multiple agents are configured with matching input regex and output filename templates.

**Main Flow**:
1. Agent A processes a file and writes output to its target directory.
2. The output file triggers a file system event on Agent B's watched directory (if they share or overlap directories).
3. Agent B's scanner detects the new file; its input regex matches the output filename pattern.
4. Agent B processes the file as its input, continuing the chain.

**Key Design**: Agents are connected via **file I/O contracts** — not code-level chaining. The regex on each agent's `fileInputRegex` matches the output filename template of upstream agents.

**Postconditions**: Files flow through the agent chain, each processing step enriching or transforming the content.

**Related ADRs**: ADR-007 (Multi-Database — file metadata persistence)  
**Related DPRs**: DPR: File History Model, DPR: Agent-Scanner Relationship

---

### UC-05: Scanner Observability & Metrics

**Goal**: Track per-agent scanner metrics and push real-time updates to the UI.

**Actors**: User (via UI), System (automated metrics tracking)

**Preconditions**: Scanner is running for one or more agents.

**Main Flow**:
1. ScannerObserverUseCase tracks per-agent metrics: file count, total discovered, unchanged count.
2. On each file discovery or unchanged event, the use case records the metric and pushes a `ScannerMetricsChangedEvent`.
3. UI views register a `Consumer<ScannerMetricsChangedEvent>` callback to receive real-time updates.
4. UI displays metrics in a grid (files count, discovered, unchanged) and refreshes automatically.

**Metrics**:

| Metric | Type | Description |
|--------|------|-------------|
| `fileCount` | Gauge | Current number of files in the watched directory |
| `totalDiscovered` | Counter | Total files found (initial scan + incremental) |
| `unchanged` | Counter | Files whose hash matches previous record (skipped) |

**Postconditions**: UI reflects current scanner state; user can monitor scanning activity in real time.

**Related ADRs**: ADR-011 (Observability)  
**Related DPRs**: DPR: Scanner Observability, DPR: Scanner Concept

---

### UC-06: LLM Health Monitoring

**Goal**: Periodically poll LLM endpoints and report their health status (UP / WARN / DOWN).

**Actors**: System (automated scheduled polling)

**Preconditions**: An observability endpoint and model are configured in `application.yml`.

**Main Flow**:
1. On startup, `AgentStatusUsecase` begins scheduled polling at the configured interval (default: 60 seconds).
2. Each poll sends a health check request to the configured LLM endpoint.
3. System persists the result (endpoint, model count, status, timestamp) to the LLM status database.
4. If the last successful check was more than `warn-after-hours` ago, status is set to `WARN`.
5. If the endpoint is unreachable, status is set to `DOWN`.
6. UI displays current status with color-coded badges (green/amber/red).

**Manual Trigger**: User can request an immediate poll via `POST /api/observability/llm-status/poll`.

**Postconditions**: LLM endpoint status is persisted and displayed; alerts are logged for DOWN/WARN states.

**Related ADRs**: ADR-011 (Observability), ADR-010 (llama.cpp)  
**Related DPRs**: (none yet)

---

### UC-07: Persistence & Data Management

**Goal**: Persist agents, scanners, and file metadata to SQLite databases.

**Actors**: System (automated), User (via UI or API)

**Preconditions**: Database is initialized with the required tables.

**Main Flow**:

| Data Type | Database | Persistence Trigger |
|-----------|----------|---------------------|
| **Agents** | `agent.db` | On agent create, update, enable/disable, delete |
| **Scanners** | `scanner.db` | On scanner create, status change, last emitted update |
| **File Metadata** | `file_meta.db` | On file discovery (hash stored for change detection) |
| **LLM Status** | `llm_status.db` | On each scheduled health check poll |

**Startup Behavior**:
1. On application start, `AgentLifecycleUseCase` loads YAML-defined agents and persists them.
2. `AgentLifecycleUseCase` restores active agents from the database (re-creates scanners and subscriptions).
3. Disabled agents are loaded as dormant (no scanner, no subscription).

**Postconditions**: Agent and scanner state survives application restarts; file change detection works correctly after restart.

**Related ADRs**: ADR-007 (Multi-Database)  
**Related DPRs**: DPR: Database Configuration

---

### UC-08: REST API

**Goal**: Expose agent and scanner management operations via HTTP endpoints.

**Actors**: External clients, UI

**Endpoints**:

| Method | Path | Use Case |
|--------|------|----------|
| `POST` | `/api/agents` | Create a new dynamic agent |
| `GET` | `/api/agents` | List all agents (active + dormant) |
| `PUT` | `/api/agents/{id}` | Update an agent (remove + re-add) |
| `DELETE` | `/api/agents/{id}` | Delete an agent |
| `PUT` | `/api/agents/{id}/enable` | Enable a disabled agent |
| `PUT` | `/api/agents/{id}/disable` | Disable an active agent |
| `POST` | `/api/agents/{id}/refresh` | Refresh an agent (full rescan) |
| `GET` | `/api/scanners` | List all scanners |
| `DELETE` | `/api/scanners/{id}` | Delete a scanner |
| `GET` | `/api/observability/llm-status` | Get LLM endpoint health status |
| `POST` | `/api/observability/llm-status/poll` | Trigger immediate LLM health poll |

**Postconditions**: Each endpoint delegates to the appropriate use case and returns the result as JSON.

**Related ADRs**: ADR-002 (REST Adapters)  
**Related DPRs**: (none yet)

---

### UC-09: UI Dashboard & Management

**Goal**: Provide a visual interface for managing agents, scanners, and observability.

**Actors**: User (browser)

**Views**:

| View | Purpose | Key Features |
|------|---------|--------------|
| **Agent List** | Manage agents | Create, edit, delete, enable/disable, refresh agents in a grid |
| **Scanner List** | Monitor scanners | View scanner status, file counts, and metrics in a grid |
| **Observability** | Monitor LLM health | View LLM endpoint status with color-coded badges |

**Interaction Pattern**:
- Views inject services; services delegate to use cases.
- Components accept data via constructors and communicate via `Consumer` callbacks.
- Real-time updates are pushed via Spring events → UI callbacks → `UI.access()` for thread safety.

**Postconditions**: User can manage the entire system from the browser; real-time updates keep the UI in sync with system state.

**Related ADRs**: ADR-003 (Vaadin/Hilla UI), ADR-004 (Flow/Hilla Routing)  
**Related DPRs**: DPR: Testing Strategy

---

## Use Case Dependencies

```
UC-01 (Agent Lifecycle)
  ├── UC-02 (Directory Scanning) — scanner is created during agent creation
  ├── UC-03 (LLM Processing) — flux connects scanner to LLM
  ├── UC-04 (Workflow Orchestration) — agents chain via file I/O
  └── UC-07 (Persistence) — agent state is saved to DB

UC-02 (Directory Scanning)
  ├── UC-05 (Scanner Observability) — metrics tracked during scanning
  └── UC-07 (Persistence) — file metadata stored for change detection

UC-03 (LLM Processing)
  └── UC-04 (Workflow Orchestration) — output files trigger downstream agents

UC-06 (LLM Health Monitoring)
  └── UC-09 (UI Dashboard) — status displayed in observability view

UC-08 (REST API)
  └── UC-01, UC-05, UC-06 — endpoints delegate to use cases

UC-09 (UI Dashboard)
  ├── UC-01, UC-05, UC-06 — views display data from use cases
  └── UC-08 — UI calls REST API (or uses Vaadin services)
```

---

## Out of Scope (Planned)

These use cases are mentioned in the project roadmap but not yet implemented:

| Use Case | Description | Status |
|----------|-------------|--------|
| **Memory Extraction** | LLM-based multi-level memory extraction from conversations | TODO |
| **Vector Search** | Qdrant-based semantic search over stored memories | TODO |
| **Tool Call** | Allow agents to read from the filesystem during LLM processing | TODO |
| **Multimodal** | Support for images and audio files in agent pipelines | TODO |
| **OpenCode Agents** | Integration with external OpenCode agent definitions | TODO |

---

## See Also

- [Design Principles](../docs/design-principles.md) — Standing rules guiding development
- [ADR-001](../adrs/adr-001-testing-strategy.md) — Testing Strategy
- [ADR-002](../adrs/adr-002-rest-adapters.md) — REST Adapters
- [ADR-003](../adrs/adr-003-vaadin-hilla-ui.md) — Vaadin/Hilla UI Components
- [ADR-007](../adrs/adr-007-multi-database.md) — Multi-Database
- [ADR-010](../adrs/adr-010-llama-cpp.md) — llama.cpp
- [ADR-011](../adrs/adr-011-observability.md) — Observability
