# Design Principles - Master Index

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
- **New to the project?** → Start here, then skim the Design Principles below

### Document Types

| Type | Location | Contains | Example |
|------|----------|----------|---------|
| **UCR** | `project/ucrs/` | **What** the system does — functional use case catalog, scoped to a domain area | "AI Workflow use case catalog" |
| **ADR** | `project/adrs/` | *Why* we made architectural choices, alternatives considered, consequences | "Why we chose Vaadin + Hilla over React SPA" |
| **DPR** | `project/docs/` | *How* concepts work — implementation details, code examples, tutorials | "How to add a new SQLite database" |
| **Plan** | `project/plans/` | Implementation plans for multi-phase work | "Scanner observer usecase plan" |

---

### How to Add an ADR or DPR

#### What Is an ADR?

An **Architecture Decision Record** documents a significant technical decision made during **vertical integration**. Each ADR captures a choice about an **adapter** - either adapter-*driven* (the application calls out to an external service) or adapter-*driving* (external clients call into the application) - and explains how that adapter connects to the domain application layer.

ADR format is inspired by Michael Nygard's original concept ("I decided this" / "Here's why") and is adapted for a Spring Boot, Hexagonal / Ports & Adapters architecture.

#### When to Create an ADR

Create an ADR when you:

- **Introduce a new external integration** - e.g., a vector store, LLM provider, database, REST API, file system scanner.
- **Adopt a new architectural pattern** - e.g., dynamic multi-scanner, queue-based LLM processing, multi-database.
- **Make a breaking change to an existing adapter** - e.g., switching from synchronous to reactive LLM calls.
- **Define how an adapter interfaces with the domain layer** - e.g., port interfaces, DTO contracts, configuration contracts.

> **Rule of thumb:** If the decision affects *how the application layer communicates with the outside world*, it belongs in an ADR.

#### ADR Structure

Every ADR should follow this structure. Sections marked with `[Optional]` may be omitted when not applicable.

1. **Title** - `ADR-NNN: <Descriptive Title>`, e.g., "REST Adapters", "Qdrant Vector Store Integration". Placed in `project/adrs/` as `adr-<slug>.md`.
2. **Date** - YYYY-MM-DD, when the decision was finalized.
3. **Context** - The problem space and constraints. What is the current situation? What requirements or pain points exist? What non-functional requirements? What external services are involved? Keep it focused - avoid describing the solution.
4. **Decision** - What was decided and how to implement it.
   - **4a. Architecture Overview** - ASCII diagram showing how the adapter fits into the architecture, data flow, and responsibility boundaries.
   - **4b. Typical Component Structure** - Package / file layout.
   - **4c. Code Examples** - Key snippets (port interface, adapter implementation, client wrapper, configuration). Keep minimal - show the pattern, not every method.
   - **4d. Configuration** - `application.yml` / `application.properties` properties, `@ConfigurationProperties` class, `@Configuration` beans.
   - **4e. Dependencies** - Maven dependencies required, BOM entries, version properties.
   - **4f. Testing Strategy** - Driving adapter tests (`@WebMvcTest`), driven adapter tests (`@RestClientTest`), unit tests, integration tests. See [ADR-001: Testing Strategy](../adrs/adr-001-testing-strategy.md) for the full testing pyramid.
     > **⚠️ `@SpringBootTest` Isolation Rule**: Always specify `classes =` to load only the beans needed. Loading the full context starts `AgentConfiguration`, which calls `initializeFromYAML()` and persists YAML agents to the database. Use a minimal `@TestConfiguration` with `@Bean` methods. Acceptable only when you explicitly want to test full application context behavior - tag such tests with `@Tag("full-context")`.
5. **How-To Guides** [Optional] - Practical instructions for extending the decision (add new feature, swap provider, configure for another project, operational runbooks).
6. **References** [Optional] - External documentation and sources.

#### What Is a UCR?

A **Use Case Record** documents *what* the system does — a catalog of functional use cases scoped to a domain area. It sits between ADRs (which explain *why*) and DPRs (which explain *how*), providing the functional context that ties everything together.

UCRs are high-level by design: they describe user goals, actor roles, main and alternative flows, preconditions, postconditions, and cross-references to relevant ADRs and DPRs. They are not implementation specs.

#### When to Create a UCR

Create a UCR when you:

- **Need a functional reference** — a high-level view of "what this system does" for a given domain area.
- **Onboard new developers** — UCRs give a quick functional overview before diving into ADRs and DPRs.
- **Group related use cases** — when a domain (e.g., "agent lifecycle", "scanner observability") has multiple use cases that are easier to read together.
- **Plan new features** — a UCR can capture planned use cases before implementation begins.

#### UCR Structure

A UCR should follow this structure:

1. **Title** — `UCR-NNN: <Descriptive Title>`, placed in `project/ucrs/` as `ucr-<slug>.md`.
2. **Purpose** — Brief summary of what the UCR covers.
3. **Use Case Categories** — High-level grouping of related use cases.
4. **Detailed Use Cases** — For each use case: goal, actors, preconditions, main flow, alternative flows, postconditions, and cross-references.
5. **Use Case Dependencies** — Diagram or table showing relationships between use cases.
6. **Out of Scope** — Planned use cases not yet implemented.
7. **See Also** — Links to relevant ADRs, DPRs, and other UCRs.

#### What Is a DPR?

A **Design Pattern Record** documents *how* a concept works — implementation details, code examples, tutorials. It complements its parent ADR by filling in the "how" that the ADR's "why" leaves open.

#### When to Create a DPR

Create a DPR when you:

- **Explain how a concept works** - e.g., file scanning, memory extraction, scanner health monitoring.
- **Provide implementation guidance** - e.g., step-by-step tutorials, code examples, best practices.
- **Document recurring patterns** - e.g., event handling, flux sharing, regex parsing.
- **Support a parent ADR** - DPRs should reference their parent ADR for context.
- **After a new feature has been added to the application and tested** - document how it works for future developers.

#### DPR Structure

A DPR should follow this structure:

1. **Title** - `DPR: <Descriptive Title>`, placed in `project/docs/` as `dpr-<slug>.md`.
2. **Overview** - Brief summary of what the concept is and how it works.
3. **How It Works** - Detailed explanation with diagrams, code examples, and walkthroughs.
4. **Key Components** - Classes, interfaces, configuration involved.
5. **Usage Examples** - How to use the concept in practice.
6. **Related Documents** - Links to parent ADR, related DPRs, and source code.

#### Cross-Reference Rules

- **ADRs and DPRs must never reference plans** - plans are intermediary artifacts only.
  - ADRs and DPRs document *decisions* and *designs* - they capture the "why" and "how" of lasting architectural choices.
  - Plans document *implementation steps* - they are transient, evolving artifacts that become obsolete once implemented.
  - Never link to, reference, or mention files in `project/plans/` from any ADR or DPR.
  - If an ADR or DPR needs to mention related work, link to other ADRs, DPRs, or source code - never to plans.
  - Plans may reference ADRs/DPRs for context, but not vice versa.
- **Why**: Plans change as implementation progresses. ADRs and DPRs are durable records. Cross-referencing plans causes stale links, confusion, and maintenance overhead when plans are deleted or rewritten.
- ADRs may reference DPRs for implementation detail.
- DPRs should reference their parent ADR for context.

#### Quick Reference: Document Types

| Aspect | UCR | ADR | DPR |
|--------|-----|-----|-----|
| **Question** | What? | Why? | How? |
| **Audience** | New developers, PMs | Architects, senior devs | Developers implementing |
| **Detail Level** | High-level functional | Architectural detail | Implementation detail |
| **Audience** | Anyone reading the docs | Engineers making decisions | Engineers writing code |
| **Cross-References** | ADRs, DPRs | DPRs (for "how") | Parent ADR (for "why") |
| **Lifespan** | Evolves with system | Stable until decision changes | Stable until implementation changes |

#### Quick Reference: Driving vs. Driven Adapters

| Aspect | Driving Adapter | Driven Adapter |
|--------|----------------|----------------|
| **Direction** | Inbound (external → app) | Outbound (app → external) |
| **Role** | Drives the application | Is driven by the application |
| **Spring Pattern** | `@RestController` | `@Service` / `@Configuration` |
| **Test Slice** | `@WebMvcTest` | `@RestClientTest` / unit tests |
| **Mock Tool** | `MockMvc` | `MockRestServiceServer` |
| **Package** | `rest/` | `llm/`, `vectorstore/`, etc. |
| **Focus** | HTTP contract, DTO mapping | HTTP interaction, business logic |

## Quick Navigation

### Use Case Records (UCRs)

| UCR | Title | Scope |
|-----|-------|-------|
| [UCR-001](../ucrs/ucr-001-use-case-catalog.md) | Use Case Catalog | High-level catalog of all system use cases |

### Architecture Decision Records (ADRs)

| ADR | Title | Subject |
|-----|-------|---------|
| [ADR-001](../adrs/adr-001-testing-strategy.md) | Testing Strategy | Why a six-tier testing pyramid was adopted |
| [ADR-002](../adrs/adr-002-rest-adapters.md) | REST Adapters | Why a two-layer adapter pattern for driving and driven adapters |
| [ADR-003](../adrs/adr-003-vaadin-hilla-ui.md) | Vaadin/Hilla UI Components | Why Vaadin + Hilla was chosen over React SPA or Thymeleaf |
| [ADR-004](../adrs/adr-004-flow-hilla-routing.md) | Flow/Hilla Routing | Why a hybrid navigation approach for Flow + Hilla coexistence |
| [ADR-005](../adrs/adr-005-storybook.md) | Storybook | Why Storybook was chosen for Hilla/React component development |

| [ADR-007](../adrs/adr-007-multi-database.md) | Multi-Database | Why separate SQLite databases for agent vs. memory data |
| [ADR-008](../adrs/adr-008-application-memory-extraction.md) | Memory Extraction | Why LLM-based multi-level memory extraction |
| [ADR-009](../adrs/adr-009-qdrant-vector-store.md) | Qdrant Vector Store | Why Spring AI VectorStore abstraction with Qdrant backend |
| [ADR-010](../adrs/adr-010-llama-cpp.md) | llama.cpp | Why OpenAI-compatible abstraction for local LLM models |
| [ADR-011](../adrs/adr-011-observability.md) | Observability | Why health-first monitoring with Micrometer/Prometheus |

### Design Pattern Records (DPRs)

| DPR | Title | Parent ADR |
|-----|-------|------------|
| [DPR: Testing Strategy](dpr-testing-strategy.md) | Six-tier testing pyramid with code examples, commands, and isolation rules | ADR-001 |
| [DPR: Scanner Concept](dpr-scanner-concept.md) | How scanners watch directories, manage status lifecycle (IDLE/EMITTING/FILTERED/ERROR), emit FileHistory events | - |
| [DPR: File History Model](dpr-file-history-model.md) | FileHistory event model, SHA-256 hashing, and metadata storage | - |
| [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) | How agents subscribe to scanners, ScannerRegistry/Factory APIs, RegexParser | - |
| [DPR: Database Configuration](dpr-database-configuration.md) | How to add new SQLite databases and tables - step-by-step tutorial | ADR-007 |
| [DPR: Scanner Observability](dpr-scanner-observability.md) | Scanner health monitoring and status tracking | - |

---

## Design Principles

These **seven standing rules** apply across all layers of the application. They guide daily development decisions and are referenced by ADRs and DPRs.

### 1. Service Access Boundary

**Components never access services; views own all service access.**

Components must never inject or access services directly. Views inject services, own reactive chains, and coordinate all component state. Components accept data via constructors and communicate via `Consumer` callbacks.

**Why**: Threading safety (reactive callbacks run on arbitrary threads), Reactor safety (`Mono<Void>` gotcha), testability (pure UI components), and re-entrancy (views can gate and reorder calls).

> **See**: [ADR-003: Vaadin/Hilla UI Components](../adrs/adr-003-vaadin-hilla-ui.md#0-service-access-boundary), [DPR: Testing Strategy](dpr-testing-strategy.md)

---

### 2. Hexagonal Architecture

**Ports in `usecase/`, adapters in `adapter/`, dependencies flow inward.**

The application follows the Ports & Adapters (Hexagonal) pattern:
- **Driving adapters** (REST controllers) live in `rest/` - they receive inbound requests
- **Driven adapters** (HTTP clients, file scanners) live in their domain packages (`llm/`, `scanner/`) - they are called by the application
- **Ports** (interface abstractions) define how the application layer communicates outward
- **Use cases** live in `usecase/` and depend only on ports, never on adapter implementations

> **See**: [ADR-002: REST Adapters](../adrs/adr-002-rest-adapters.md)

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

- Scanner folder paths must exist and be accessible at agent creation time - fail with a clear error message
- Regex patterns must contain a valid `folderPattern` named group - agent creation fails without it
- Configuration properties should have sensible defaults but validate required fields early
- Cross-database entity packages must be in separate directories to avoid mapping conflicts

---

### 5. Graceful Degradation

**Components fall back to local state when backends are unavailable.**

- When an LLM endpoint is down, the observability dashboard shows a WARN/DOWN status without crashing the application
- Scanner failures are isolated per folder - one inaccessible folder doesn't affect other scanners
- The application continues operating with cached or last-known state when external services are unreachable

---

### 6. Reactive Safety

**Always wrap Vaadin updates in `UI.access()`; never embed reactive chains in components.**

- All Vaadin component updates must happen on the UI thread via `UI.getCurrent().access()` or `.getUI().get().access()`
- Scheduled executors run on background threads - always use `UI.access()` before component manipulation
- Be wary of `Mono<Void>` and `Mono.empty()` - they complete without emitting a value, so `subscribe(Consumer<T>)` is **never invoked**
- Components must not own reactive chains; views own them and pass data to components via callbacks
- Always stop `ScheduledExecutorService` in `onDetach()` to prevent memory leaks

> **See**: [ADR-003: Vaadin/Hilla UI Components](../adrs/adr-003-vaadin-hilla-ui.md#0-service-access-boundary), [ADR-004: Flow/Hilla Routing](../adrs/adr-004-flow-hilla-routing.md#reactive-data-loading)

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
| 007 | Multi-Database |
| 008 | Memory Extraction |
| 009 | Qdrant Vector Store |
| 010 | llama.cpp |
| 011 | Observability (Micrometer) |

### UCR Numbering

UCRs are numbered sequentially starting from 001. Each UCR covers a scoped domain area (e.g., the full system catalog, a single subdomain).

| Number | Subject |
|--------|---------|
| 001 | Use Case Catalog |

---

## See Also

- [UCR-001: Use Case Catalog](../ucrs/ucr-001-use-case-catalog.md) — High-level functional overview of all use cases
- [ADR-001: Testing Strategy](../adrs/adr-001-testing-strategy.md) — Six-tier testing pyramid decision
- [DPR: Testing Strategy](dpr-testing-strategy.md) — How to implement each test tier
- [DPR: Database Configuration](dpr-database-configuration.md) — How to add new SQLite databases
---

## Project Structure

```
project/
├── adrs/
│   ├── adr-001-testing-strategy.md        # Why six-tier testing pyramid
│   ├── adr-002-rest-adapters.md           # Why REST adapter pattern
│   ├── adr-003-vaadin-hilla-ui.md         # Why Vaadin + Hilla
│   ├── adr-004-flow-hilla-routing.md      # Why hybrid Flow/Hilla routing
│   ├── adr-005-storybook.md               # Why Storybook for component dev
│   ├── adr-007-multi-database.md          # Why multi-database
│   ├── adr-008-application-memory-extraction.md  # Why LLM-based memory extraction
│   ├── adr-009-qdrant-vector-store.md     # Why Spring AI VectorStore + Qdrant
│   ├── adr-010-llama-cpp.md               # Why OpenAI-compatible abstraction
│   └── adr-011-observability.md           # Why health-first monitoring
├── docs/
│   ├── design-principles.md               # ← This document (master index)
│   ├── dpr-testing-strategy.md            # How to implement each test tier
│   ├── dpr-scanner-concept.md             # How scanners work
│   ├── dpr-file-history-model.md          # How FileHistory events work
│   ├── dpr-agent-scanner-relationship.md  # How agents subscribe to scanners
│   ├── dpr-database-configuration.md      # How to add new SQLite databases
│   ├── dpr-scanner-observability.md       # How scanner observability works
├── ucrs/
│   └── ucr-001-use-case-catalog.md        # What the system does — use case catalog
└── plans/
    ├── design-principles-update.md        # This migration plan
    ├── scanner-observer-usecase.md        # Scanner observer usecase plan
    └── ...
```
