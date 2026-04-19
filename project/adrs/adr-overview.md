# ADR Overview — Architecture Decision Records

## What Is an ADR?

An **Architecture Decision Record (ADR)** documents a significant technical decision made during **vertical integration** of the application. Each ADR captures a choice about an **adapter** — either adapter-*driven* (the application calls out to an external service) or adapter-*driving* (external clients call into the application) — and explains how that adapter connects to the domain application layer.

ADR format is inspired by Michael Nygard's original concept ("I decided this" / "Here's why") and is adapted for a Spring Boot, Hexagonal / Ports & Adapters architecture.

---

## When to Create an ADR

Create an ADR when you:

- **Introduce a new external integration** — e.g., a vector store, LLM provider, database, REST API, file system scanner.
- **Adopt a new architectural pattern** — e.g., dynamic multi-scanner, queue-based LLM processing, multi-database.
- **Make a breaking change to an existing adapter** — e.g., switching from synchronous to reactive LLM calls.
- **Define how an adapter interfaces with the domain layer** — e.g., port interfaces, DTO contracts, configuration contracts.

> **Rule of thumb:** If the decision affects *how the application layer communicates with the outside world*, it belongs in an ADR.

---

## ADR Structure

Every ADR should follow this structure. Sections marked with `[Optional]` may be omitted when not applicable.

### 1. Title

`# ADR-NNN: <Descriptive Title>`

- Use a short, action-oriented title (e.g., *"REST Adapters"*, *"Qdrant Vector Store Integration"*).
- Number sequentially (`ADR-001`, `ADR-002`, …).
- Place in `project/adrs/` as `adr-<slug>.md`.

### 2. Date

```markdown
## Date

YYYY-MM-DD
```

The date the decision was finalized.

### 3. Context

> **Where does this decision come from?**

Describe the **problem space** and **constraints** that motivated the decision. Cover:

- **What is the current situation?** (baseline state)
- **What requirements or pain points exist?** (e.g., "single watch root", "no isolation", "tight coupling")
- **What are the non-functional requirements?** (testability, configurability, maintainability, performance)
- **What external services or systems are involved?**

Keep this section focused. Avoid describing the solution — that comes next.

### 4. Decision

> **How do we set it up and how does it work?**

This is the core of the ADR. Explain **what** was decided and **how** to implement it. Include:

#### 5a. Architecture Overview

A **diagram** (ASCII art or link to a diagram) showing how the adapter fits into the overall architecture:

```
┌──────────────────────────────────────────────────┐
│           Spring Boot Application                 │
│                                                   │
│  ┌──────────────┐    ┌────────────────────┐      │
│  │  Port (iface)│    │  Adapter (impl)    │      │
│  │              │───▶│                    │      │
│  └──────────────┘    └────────┬───────────┘      │
│                               │                   │
└───────────────────────────────┼───────────────────┘
                                ▼
                    ┌─────────────────────┐
                    │  External Service   │
                    └─────────────────────┘
```

Show the **data flow** and **responsibility boundaries** between:
- The **application layer** (use cases, services)
- The **port** (interface abstraction)
- The **adapter** (concrete implementation)
- **External services** (databases, APIs, file systems)

#### 5b. Component Structure

Show the **package / file layout**:

```
com.example.adapter/
├── MyAdapter.java              # Business adapter (logic layer)
├── MyClient.java               # REST/HTTP client (I/O layer)
├── MyConfiguration.java        # Spring configuration
├── MyProperties.java           # Configuration properties
└── output/
    └── MyOutputParsing.java    # Output utilities
```

#### 5c. Code Examples

Include **key code snippets** that illustrate the adapter pattern:

- Port interface definition
- Adapter implementation (business logic)
- Client wrapper (I/O layer, if applicable)
- Configuration and dependency injection

Keep snippets minimal — show the *pattern*, not every method.

#### 5d. Configuration

Document how to **configure** the adapter:

- `application.yml` / `application.properties` properties
- Spring `@ConfigurationProperties` class
- `@Configuration` beans and wiring

#### 5e. Dependencies (pom.xml)

List the **Maven dependencies** required:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
</dependency>
```

Include any BOM entries or version properties if non-standard.

#### 5f. Testing Strategy

Explain **how the adapter is tested**:

- Driving adapter tests (e.g., `@WebMvcTest` with `MockMvc`)
- Driven adapter tests (e.g., `@RestClientTest` with `MockRestServiceServer`)
- Unit tests for business logic
- Integration tests (if applicable)
- POM dependencies for test slices

### 5. Consequences

> **What are the trade-offs and implications?**

Organize into subsections:

#### Benefits

- **What does this decision enable?** (testability, flexibility, isolation, etc.)
- **What problems does it solve?**

#### Trade-offs

- **What do we give up?** (complexity, performance, version maturity, etc.)
- **What are the costs?**

#### Tight Coupling Points

> ⚠️ **Areas of concern** — coupling, shared state, or design smells that should be addressed in future work.

#### Important Notes

- **Runtime behavior** (e.g., "ChatClient is the primary bean", "collection auto-creation")
- **Assumptions** (e.g., "embedding model must be accessible via HTTP")
- **Known limitations** (e.g., "no cross-DB transactions")

### 6. How-To Guides [Optional]

> **How to use and extend this decision.**

Practical instructions for developers:

- **How to add a new feature/extension** (e.g., "How to Add New Extraction Level")
- **How to swap a provider** (e.g., "How to Swap LLM Provider")
- **How to configure for another project** (step-by-step setup)
- **Operational runbooks** (how to monitor, troubleshoot, scale)

### 7. Alternatives Considered [Optional]

> **What other options were evaluated and why were they rejected?**

For each alternative:

1. **Name** — short description
2. **Why considered** — what problem it would solve
3. **Why rejected** — specific drawbacks

This section helps future reviewers understand the decision space and prevents rehashing old debates.

### 8. Migration Path [Optional]

> **How to transition from the old approach to the new one.**

If the ADR introduces a breaking change or new pattern:

- **Phased rollout plan** (Week 1, Week 2, …)
- **Backward compatibility** strategy
- **Data migration** steps (if applicable)

### 9. Rollback Plan [Optional]

> **How to revert if things go wrong.**

- **Revert steps** — how to return to the previous state
- **Data safety** — ensure no data loss on rollback
- **Grace period** — how long the old code path is supported

### 10. Open Questions [Optional]

> **What is still unresolved?**

- **Q:** … **Decision:** … (resolved)
- **Q:** … **Pending:** … (open)

### 11. See Also

> **Links to related ADRs.**

```markdown
- [ADR-002: Qdrant Vector Store Integration](adr-qdrant-vector-store.md)
- [ADR-003: Chat Model Setup for llama.cpp](adr-chat-model-setup-for-llama-cpp.md)
```

Cross-reference related decisions so readers can navigate the decision graph.

### 12. References [Optional]

> **External documentation and sources.**

- Spring AI Documentation
- Qdrant Documentation
- Clean Architecture (Robert C. Martin)
- Project Reactor Documentation
- Any other external resources

---

## Quick Reference: Driving vs. Driven Adapters

| Aspect | Driving Adapter | Driven Adapter |
|--------|----------------|----------------|
| **Direction** | Inbound (external → app) | Outbound (app → external) |
| **Role** | Drives the application | Is driven by the application |
| **Spring Pattern** | `@RestController` | `@Service` / `@Configuration` |
| **Test Slice** | `@WebMvcTest` | `@RestClientTest` / unit tests |
| **Mock Tool** | `MockMvc` | `MockRestServiceServer` |
| **Package** | `rest/` | `llm/`, `vectorstore/`, etc. |
| **Focus** | HTTP contract, DTO mapping | HTTP interaction, business logic |

---

## Example ADR Checklist

Before submitting an ADR, verify:

- [ ] Title is clear and numbered
- [ ] Date is accurate
- [ ] Context explains *why* (problem, not solution)
- [ ] Decision includes architecture diagram
- [ ] Decision includes component structure
- [ ] Decision includes code examples
- [ ] Decision includes configuration and dependencies
- [ ] Decision includes testing strategy
- [ ] Consequences cover benefits, trade-offs, and coupling points
- [ ] Cross-references to related ADRs exist
- [ ] Links are valid and paths are relative

---

## Naming Convention

| Element | Convention | Example |
|---------|-----------|---------|
| ADR file | `adr-<slug>.md` | `adr-rest-adapters.md` |
| ADR title | `ADR-NNN: <Title>` | `ADR-001: REST Adapters` |
| Package | `com.hdekker.ai_workflow.<domain>` | `com.hdekker.ai_workflow.rest` |
| Config prefix | `app.<domain>.` | `app.observability.` |
| DTO class | PascalCase | `AdapterStatus` |
| Port interface | `<Action>Port` | `StoreMemoriesPort` |
| Adapter class | `<Entity>Adapter` | `DatabaseMemoryAdapter` |
