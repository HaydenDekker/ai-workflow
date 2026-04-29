# Design Principles — Master Index

**Status**: Active  
**Created**: 2026-04-29  
**Author**: AI Workflow Team

---

## Purpose

This document is the **central index** for all architectural decisions and design notes in the AI Workflow project. It links every ADR (Architecture Decision Record) and DPR (Design Pattern Record) together, provides standing design principles that apply across all layers, and defines naming conventions.

### How to Use This Document

- **Looking for *why* a decision was made?** → Find it in an **ADR** (`project/adrs/adr-NNN-*.md`)
- **Looking for *how* something works?** → Find it in a **DPR** (`project/docs/dpr-*.md`)
- **Need to understand the overall structure?** → This document ties everything together
- **New to the project?** → Start with [ADR Overview](../adrs/adr-overview.md), then skim the Design Principles below

### Document Types

| Type | Location | Contains | Example |
|------|----------|----------|---------|
| **ADR** | `project/adrs/` | *Why* we made architectural choices, alternatives considered, consequences | "Why we chose Vaadin + Hilla over React SPA" |
| **DPR** | `project/docs/` | *How* concepts work — implementation details, code examples, tutorials | "How to add a new SQLite database" |
| **Plan** | `project/plans/` | Implementation plans for multi-phase work | "Scanner observer usecase plan" |

---

## Quick Navigation

### Architecture Decision Records (ADRs)

| ADR | Title | Subject |
|-----|-------|---------|
| [ADR-001](../adrs/adr-001-testing-strategy.md) | Testing Strategy | Why a six-tier testing pyramid was adopted |
| [ADR-002](../adrs/adr-rest-adapters.md) | REST Adapters | Why a two-layer adapter pattern for driving and driven adapters |
| [ADR-003](../adrs/adr-ui-components.md) | Vaadin/Hilla UI Components | Why Vaadin + Hilla was chosen over React SPA or Thymeleaf |
| [ADR-004](../adrs/adr-ui-views.md) | Flow/Hilla Routing | Why a hybrid navigation approach for Flow + Hilla coexistence |
| [ADR-005](../adrs/adr-ui-hilla-component-development.md) | Storybook | Why Storybook was chosen for Hilla/React component development |
| [ADR-006](../adrs/adr-006-dynamic-scanners.md) | Dynamic Multi-Scanner | Why dynamic multi-scanner architecture over static configuration |
| [ADR-007](../adrs/adr-007-multi-database.md) | Multi-Database | Why separate SQLite databases for agent vs. memory data |
| [ADR-008](../adrs/adr-application-memory-extraction.md) | Memory Extraction | Why LLM-based multi-level memory extraction |
| [ADR-009](../adrs/adr-qdrant-vector-store.md) | Qdrant Vector Store | Why Spring AI VectorStore abstraction with Qdrant backend |
| [ADR-010](../adrs/adr-chat-model-setup-for-llama-cpp.md) | llama.cpp | Why OpenAI-compatible abstraction for local LLM models |
| [ADR-011](../adrs/adr-011-micrometer.md) | Observability | Why health-first monitoring with Micrometer/Prometheus |
| [ADR Overview](../adrs/adr-overview.md) | ADR Format Guide | When to create an ADR, structure, and naming conventions |

### Design Pattern Records (DPRs)

| DPR | Title | Parent ADR |
|-----|-------|------------|
| [DPR: Testing Strategy](dpr-testing-strategy.md) | Six-tier testing pyramid with code examples, commands, and isolation rules | ADR-001 |
| [DPR: Scanner Concept](dpr-scanner-concept.md) | How scanners watch directories, rate-limit events, and share flux | ADR-006 |
| [DPR: File History Model](dpr-file-history-model.md) | FileHistory event model, SHA-256 hashing, and metadata storage | ADR-006 |
| [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) | How agents subscribe to scanners, ScannerRegistry/Factory APIs, RegexParser | ADR-006 |
| [DPR: Database Configuration](dpr-database-configuration.md) | How to add new SQLite databases and tables — step-by-step tutorial | ADR-007 |
| [DPR: Scanner Observability](dpr-scanner-observability.md) | Scanner health monitoring and status tracking | — |

### Moved Documents

| Document | Location | Subject |
|----------|----------|---------|
| [Hilla Setup Guide](hilla-setup-guide.md) | `docs/hilla-setup-guide.md` | Hilla configuration and file router basics (moved from `adrs/`) |

---

## Design Principles

These **seven standing rules** apply across all layers of the application. They guide daily development decisions and are referenced by ADRs and DPRs.

### 1. Service Access Boundary

**Components never access services; views own all service access.**

Components must never inject or access services directly. Views inject services, own reactive chains, and coordinate all component state. Components accept data via constructors and communicate via `Consumer` callbacks.

**Why**: Threading safety (reactive callbacks run on arbitrary threads), Reactor safety (`Mono<Void>` gotcha), testability (pure UI components), and re-entrancy (views can gate and reorder calls).

> **See**: [ADR-003: Vaadin/Hilla UI Components](../adrs/adr-ui-components.md#0-service-access-boundary), [DPR: Testing Strategy](dpr-testing-strategy.md)

---

### 2. Hexagonal Architecture

**Ports in `usecase/`, adapters in `adapter/`, dependencies flow inward.**

The application follows the Ports & Adapters (Hexagonal) pattern:
- **Driving adapters** (REST controllers) live in `rest/` — they receive inbound requests
- **Driven adapters** (HTTP clients, file scanners) live in their domain packages (`llm/`, `scanner/`) — they are called by the application
- **Ports** (interface abstractions) define how the application layer communicates outward
- **Use cases** live in `usecase/` and depend only on ports, never on adapter implementations

> **See**: [ADR-002: REST Adapters](../adrs/adr-rest-adapters.md), [ADR Overview](../adrs/adr-overview.md#quick-reference-driving-vs-driven-adapters)

---

### 3. Test Isolation

**Never write to production SQLite in tests; use `@SpringBootTest(classes = ...)` with minimal beans.**

- Always specify `classes =` in `@SpringBootTest` to prevent `AgentConfiguration` from persisting YAML agents to the database
- Use `@DataJpaTest` with H2 in-memory for repository/entity tests
- Use `@MockitoBean` (not `@MockBean`) for Spring Boot 4.0+ compatibility
- Tag integration tests with `@Tag("integration")` for selective execution
- Use `@TempDir` for file-based tests to ensure cleanup

> **See**: [DPR: Testing Strategy](dpr-testing-strategy.md), [ADR-001: Testing Strategy](../adrs/adr-001-testing-strategy.md)

---

### 4. Fail Fast

**Validate at creation time; reject invalid configurations immediately.**

- Scanner folder paths must exist and be accessible at agent creation time — fail with a clear error message
- Regex patterns must contain a valid `folderPattern` named group — agent creation fails without it
- Configuration properties should have sensible defaults but validate required fields early
- Cross-database entity packages must be in separate directories to avoid mapping conflicts

---

### 5. Graceful Degradation

**Components fall back to local state when backends are unavailable.**

- When an LLM endpoint is down, the observability dashboard shows a WARN/DOWN status without crashing the application
- Scanner failures are isolated per folder — one inaccessible folder doesn't affect other scanners
- The application continues operating with cached or last-known state when external services are unreachable

---

### 6. Reactive Safety

**Always wrap Vaadin updates in `UI.access()`; never embed reactive chains in components.**

- All Vaadin component updates must happen on the UI thread via `UI.getCurrent().access()` or `.getUI().get().access()`
- Scheduled executors run on background threads — always use `UI.access()` before component manipulation
- Be wary of `Mono<Void>` and `Mono.empty()` — they complete without emitting a value, so `subscribe(Consumer<T>)` is **never invoked**
- Components must not own reactive chains; views own them and pass data to components via callbacks
- Always stop `ScheduledExecutorService` in `onDetach()` to prevent memory leaks

> **See**: [ADR-003: Vaadin/Hilla UI Components](../adrs/adr-ui-components.md#0-service-access-boundary), [ADR-004: Flow/Hilla Routing](../adrs/adr-ui-views.md#reactive-data-loading)

---

### 7. Co-location

**Stories next to components; tests next to source; config next to consumers.**

- **Stories** live next to their components: `HelloWorld.tsx` → `HelloWorld.stories.tsx`
- **Tests** live next to their source: `AgentListView.java` → `AgentListViewTest.java`
- **Configuration** lives next to its consumers: `DatabaseConfig.java` in the same package as the beans it configures
- **DPRs** live in `project/docs/`; **ADRs** live in `project/adrs/`; **plans** live in `project/plans/`
- Theme CSS lives in `src/main/frontend/themes/` alongside the Vaadin components that use it

---

## Naming Conventions

### Class Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Package | `com.hdekker.ai_workflow.<domain>` | `com.hdekker.ai_workflow.rest` |
| DTO class | PascalCase | `AgentInfo`, `AdapterStatus` |
| Port interface | `<Action>Port` | `StoreMemoriesPort` |
| Adapter class | `<Entity>Adapter` | `DatabaseMemoryAdapter`, `OpenAiHealthAdapter` |
| Service class | `<Domain>Service` | `LLMStatusService`, `AgentInfoService` |
| Use case class | `<Action>UseCase` | `StoreMemoriesUseCase` |
| Configuration class | `<Domain>Config` or `<Domain>Configuration` | `DatabaseConfig`, `OpenAiChatConfig` |
| Properties class | `<Domain>Properties` or `<Domain>Properties` | `ObservabilityProperties` |
| Entity class | `<Entity>Entity` | `AgentEntity`, `FileMetadataEntity` |
| Repository interface | `<Entity>Repository` | `AgentRepository` |
| Test class | `<Subject>Test` | `AgentListViewTest` |

### File Naming

| Type | Convention | Example |
|------|-----------|---------|
| ADR | `adr-NNN-<slug>.md` | `adr-001-testing-strategy.md` |
| DPR | `dpr-<slug>.md` | `dpr-testing-strategy.md` |
| Java source | `PascalCase.java` | `AgentListView.java` |
| Test source | `<Subject>Test.java` | `AgentListViewTest.java` |
| Storybook story | `*<ComponentName>*.stories.tsx` | `HelloWorld.stories.tsx` |
| YAML config | `snake_case.yaml` | `agent-definition.yaml` |

### ADR Numbering

ADRs are numbered sequentially starting from 001. When splitting an ADR into multiple documents, the original ADR keeps its number and related DPRs reference it.

| Number | Subject |
|--------|---------|
| 001 | Testing Strategy |
| 002 | REST Adapters |
| 003 | Vaadin/Hilla UI Components |
| 004 | Flow/Hilla Routing |
| 005 | Storybook |
| 006 | Dynamic Multi-Scanner |
| 007 | Multi-Database |
| 008 | Memory Extraction |
| 009 | Qdrant Vector Store |
| 010 | llama.cpp |
| 011 | Observability (Micrometer) |

---

## See Also

- [ADR Overview](../adrs/adr-overview.md) — Full ADR format guide, when to create an ADR, structure
- [ADR-001: Testing Strategy](../adrs/adr-001-testing-strategy.md) — Six-tier testing pyramid decision
- [DPR: Testing Strategy](dpr-testing-strategy.md) — How to implement each test tier
- [DPR: Database Configuration](dpr-database-configuration.md) — How to add new SQLite databases
- [Hilla Setup Guide](hilla-setup-guide.md) — Hilla configuration basics

---

## Project Structure

```
project/
├── adrs/
│   ├── adr-overview.md                    # ADR format guide (unchanged)
│   ├── adr-001-testing-strategy.md        # Why six-tier testing pyramid
│   ├── adr-002-rest-adapters.md           # Why REST adapter pattern
│   ├── adr-003-vaadin-hilla-ui.md         # Why Vaadin + Hilla
│   ├── adr-004-flow-hilla-routing.md      # Why hybrid Flow/Hilla routing
│   ├── adr-005-storybook.md               # Why Storybook for component dev
│   ├── adr-006-dynamic-scanners.md        # Why dynamic multi-scanner
│   ├── adr-007-multi-database.md          # Why multi-database
│   ├── adr-008-memory-extraction.md       # Why LLM-based memory extraction
│   ├── adr-009-qdrant-vector-store.md     # Why Spring AI VectorStore + Qdrant
│   ├── adr-010-llama-cpp.md               # Why OpenAI-compatible abstraction
│   └── adr-011-micrometer.md              # Why health-first monitoring
├── docs/
│   ├── design-principles.md               # ← This document (master index)
│   ├── dpr-testing-strategy.md            # How to implement each test tier
│   ├── dpr-scanner-concept.md             # How scanners work
│   ├── dpr-file-history-model.md          # How FileHistory events work
│   ├── dpr-agent-scanner-relationship.md  # How agents subscribe to scanners
│   ├── dpr-database-configuration.md      # How to add new SQLite databases
│   ├── dpr-scanner-observability.md       # How scanner observability works
│   └── hilla-setup-guide.md               # Hilla setup (moved from adrs/)
└── plans/
    ├── design-principles-update.md        # This migration plan
    ├── scanner-observer-usecase.md        # Scanner observer usecase plan
    └── ...
```
