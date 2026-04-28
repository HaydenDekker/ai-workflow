# Plan: Design Principles Update — ADR Cleanup & DPR Migration

**Status**: In Progress  
**Created**: 2026-04-28  
**Author**: AI Workflow Team  
**Last Updated**: 2026-04-28  

---

## Progress

| Phase | Status | Date | Notes |
|-------|--------|------|-------|
| **Phase 0** | ⬜ Not started | — | — |
| **Phase 1.1** | ✅ Complete | 2026-04-28 | `adr-testing-strategy.md` → `dpr-testing-strategy.md` + `adr-001-testing-strategy.md`. Old ADR removed. |
| **Phase 1.2** | ⬜ Not started | — | — |
| **Phase 1.3** | ⬜ Not started | — | — |
| **Phase 1.4** | ⬜ Not started | — | — |
| **Phase 2.1** | ⬜ Not started | — | — |
| **Phase 2.2** | ⬜ Not started | — | — |
| **Phase 3.1** | ⬜ Not started | — | — |
| **Phase 3.2** | ⬜ Not started | — | — |
| **Phase 3.3** | ⬜ Not started | — | — |
| **Phase 3.4** | ⬜ Not started | — | — |
| **Phase 4** | ⬜ Not started | — | — |

---

## 1. Objective

Separate **architectural decisions** (ADRs) from **application design notes** (DPRs) by:

1. Drafting a master `design-principles.md` document as the central index
2. Working through each ADR systematically, extracting design notes into DPRs
3. Trimming ADRs to focus on *why* (decisions and consequences), moving *how* (implementation details) to DPRs
4. Repeating until all ADRs are clean and all design concepts have dedicated DPRs

The end state is a clear separation:
- **ADRs** (`project/adrs/`) — *Why* we made architectural choices
- **DPRs** (`project/docs/dpr-*.md`) — *How* application concepts work
- **`design-principles.md`** (`project/docs/`) — Master index linking everything together

---

## 2. Current State

### ADR Inventory (13 files)

| # | File | Subject | Issue |
|---|------|---------|-------|
| — | `adr-overview.md` | ADR format guide | ✅ Keep as-is |
| 1 | `adr-rest-adapters.md` | REST adapter pattern | ⚠️ Minor — some code examples could be DPR |
| 2 | `adr-ui-components.md` | Vaadin/Hilla components | ⚠️ Contains design notes (Service Access Boundary, Reactor Gotcha, CSS theming) |
| 3 | `adr-ui-views.md` | Flow/Hilla routing | ⚠️ Repeats Service Access Boundary from ADR-002 |
| 4 | `adr-ui-hilla-component-development.md` | Storybook for components | ⚠️ 80% is design note/tutorial, not ADR |
| — | `adr-dynamic-scanners.md` | Multi-scanner architecture | 🔴 Major — Scanner concept, rate limiting, regex parsing all mixed in |
| — | `adr-testing-strategy.md` | Testing pyramid | ⚠️ Borderline — testing principles document, not a decision |
| — | `adr-multi-db-support.md` | Multi-database config | ⚠️ Contains "How to Add a Third Database" tutorial |
| — | `adr-application-memory-extraction.md` | Memory extraction architecture | ⚠️ Tight coupling notes, prompt template details, extraction level handler |
| — | `adr-qdrant-vector-store.md` | Qdrant integration | ⚠️ `QdrantService` implementation, SHA-256 algorithm, filter expressions |
| — | `adr-chat-model-setup-for-llama-cpp.md` | llama.cpp setup | ⚠️ Docker commands, GGUF download instructions, performance table |
| — | `adr-application-observability.md` | LLM health monitoring | ⚠️ `AdapterStatusComponent` implementation, polling parameters, CSS |
| — | `adr-hilla-setup-guide.md` | Hilla setup guide | 🔴 Not an ADR — this is a tutorial |

### Existing DPRs (1 file)

| File | Subject |
|------|---------|
| `docs/dpr-database-configuration.md` | Adding new SQLite databases |

### Missing Design Notes (11 identified)

See Section 4 for the full list of DPRs to create.

---

## 3. Process Overview

The work is organized into **4 phases**, each producing deliverables before moving to the next:

```
Phase 0: Master Document
    ↓
Phase 1: Core ADRs (Testing + UI)
    ↓
Phase 2: Scanner + Infrastructure ADRs
    ↓
Phase 3: LLM + Vector Store ADRs
    ↓
Phase 4: Cleanup + Cross-References
```

**Rules for every phase:**
1. **Draft first** — write the new DPR (or master document) before touching any ADR
2. **Validate** — ensure the ADR still makes sense after content is moved
3. **Cross-reference** — add `See Also` links in both directions (ADR → DPR, DPR → ADR)
4. **Rename** — standardize ADR filenames to `adr-NNN-*.md` format
5. **Verify** — no content is lost; every detail that was in the ADR is either kept or migrated

---

## 4. Phase 0: Draft Master Document

### Deliverable: `project/docs/design-principles.md`

This document serves as the central index and manifesto. It contains:

1. **Purpose statement** — what this document is and how to use it
2. **Quick Navigation** — two tables:
   - ADRs table: links to all ADRs with one-line summaries
   - DPRs table: links to all DPRs with one-line summaries
3. **Design Principles** — 7-8 standing rules that apply across all layers (see Section 6)
4. **Naming Conventions** — class naming, package naming, file naming standards
5. **See Also** — links to related external documentation

### Tasks

| # | Task | Output |
|---|------|--------|
| 0.1 | Draft `design-principles.md` with placeholder tables | `project/docs/design-principles.md` (draft) |
| 0.2 | Add Design Principles section with the 7 principles (see Section 6) | Same file |
| 0.3 | Review draft with team | Feedback collected |
| 0.4 | Finalize master document | `project/docs/design-principles.md` (final) |

### Design Principles (to be included)

1. **Service Access Boundary** — Components never access services; views own all service access
2. **Hexagonal Architecture** — Ports in `usecase/`, adapters in `adapter/`, dependencies flow inward
3. **Test Isolation** — Never write to production SQLite in tests; use `@SpringBootTest(classes = ...)`
4. **Fail Fast** — Validate at creation time; reject invalid configurations immediately
5. **Graceful Degradation** — Components fall back to local state when backends are unavailable
6. **Reactive Safety** — Always wrap Vaadin updates in `UI.access()`; never embed reactive chains in components
7. **Co-location** — Stories next to components; tests next to source; config next to consumers
8. **Explicit Over Implicit** — Use explicit target directories over regex-parsed folder patterns

---

## 5. Phase 1: Core ADRs (Testing + UI)

These ADRs are the foundation — they affect every developer's daily work. Clean these first.

### Phase 1.1: `adr-testing-strategy.md` → DPR Migration

> **✅ Complete** — 2026-04-28

**Scope**: Testing strategy is a design principle, not an architectural decision.

**DPR to create**: `docs/dpr-testing-strategy.md`

**Content to migrate to DPR:**
- Six-tier testing pyramid table
- Tier 1-6 descriptions with code examples
- Test execution commands
- Test classification decision tree
- Test isolation rules table
- POM dependencies for testing
- Browserless testing patterns
- Reactor Gotcha (`Mono<Void>` warning)

**Content to keep in ADR** (rename to `adr-001-testing-strategy.md`):
- Why we adopted a six-tier testing pyramid (the decision)
- Why we chose H2 over SQLite for tests
- Why we chose BrowserlessTest over Playwright for UI tests

**Tasks**

| # | Task | Output | Status |
|---|------|--------|--------|
| 1.1.1 | Draft `dpr-testing-strategy.md` with full testing pyramid, code examples, commands | `project/docs/dpr-testing-strategy.md` | ✅ |
| 1.1.2 | Trim `adr-testing-strategy.md` to decision only | `project/adrs/adr-001-testing-strategy.md` | ✅ |
| 1.1.3 | Add cross-reference: ADR → DPR, DPR → ADR | Both files | ✅ |

**Review notes**: Consequences and Alternatives Considered sections removed from ADR. See Also simplified to `adr-overview` + `dpr-testing-strategy` only.

### Phase 1.2: `adr-ui-components.md` → DPR Migration

**Scope**: ADR-002 contains design notes about the Service Access Boundary and Reactor Gotcha that should be standalone DPRs.

**DPRs to create**:

1. `docs/dpr-service-access-boundary.md` — The "components never access services" rule
2. `docs/dpr-reactive-safety.md` — The Reactor Gotcha and `UI.access()` patterns

**Content to migrate to DPRs:**
- Service Access Boundary rule with code examples (WRONG vs CORRECT)
- Why the rule exists (threading, reactor safety, testability, re-entrancy)
- Reactor Gotcha (`Mono<Void>` / `Mono.empty()`) with examples
- `UI.access()` wrapping patterns
- CSS theming conventions (Lumo variables, status badge pattern)
- Lifecycle-aware scheduling (`onDetach()` + `ScheduledExecutorService`)

**Content to keep in ADR** (rename to `adr-002-vaadin-hilla-ui.md`):
- Why Vaadin + Hilla was chosen over React SPA or Thymeleaf
- Architecture overview diagram (Hilla + Flow coexistence)
- Component structure and package layout
- Configuration (`application.yml`, `pom.xml`)
- Testing strategy (browserless + E2E)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 1.2.1 | Draft `dpr-service-access-boundary.md` | `project/docs/dpr-service-access-boundary.md` |
| 1.2.2 | Draft `dpr-reactive-safety.md` | `project/docs/dpr-reactive-safety.md` |
| 1.2.3 | Trim `adr-ui-components.md` to framework choice + architecture | `project/adrs/adr-002-vaadin-hilla-ui.md` |
| 1.2.4 | Add cross-references in all three files | All files |
| 1.2.5 | Update `adr-ui-views.md` to reference `dpr-service-access-boundary.md` instead of repeating it | `project/adrs/adr-003-flow-hilla-routing.md` |

### Phase 1.3: `adr-ui-hilla-component-development.md` → DPR Migration

**Scope**: This is 80% a design note / tutorial, not an ADR.

**DPR to create**: `docs/dpr-storybook-workflow.md`

**Content to migrate to DPR:**
- Storybook configuration (`.storybook/main.ts`, `preview.ts`)
- Step-by-step component creation workflow (Steps 1-7)
- `vi.mock()` patterns (A, B, C, D)
- Shared mock data modules
- Story-level overrides
- Component conventions and checklists
- Service mocking alternatives (`vi.mock()` vs graceful degradation)
- Complete Counter component example

**Content to keep in ADR** (rename to `adr-004-storybook.md`):
- Why Storybook was chosen over Vitest-only or Playwright-only
- Key trade-offs (extra dependency, two Vite instances, mock drift risk)
- Integration with existing Vaadin/Hilla build pipeline

**Tasks**

| # | Task | Output |
|---|------|--------|
| 1.3.1 | Draft `dpr-storybook-workflow.md` with full workflow | `project/docs/dpr-storybook-workflow.md` |
| 1.3.2 | Trim `adr-ui-hilla-component-development.md` to decision + trade-offs | `project/adrs/adr-004-storybook.md` |
| 1.3.3 | Add cross-references | Both files |

### Phase 1.4: `adr-hilla-setup-guide.md` → Move to Docs

**Scope**: This is a tutorial, not an ADR. The decision to use Hilla is already in `adr-ui-components.md`.

**Action**: Move entire file to `docs/hilla-setup-guide.md`. Delete from `adrs/`.

**Tasks**

| # | Task | Output |
|---|------|--------|
| 1.4.1 | Move `adr-hilla-setup-guide.md` → `docs/hilla-setup-guide.md` | `project/docs/hilla-setup-guide.md` |
| 1.4.2 | Delete `adr-hilla-setup-guide.md` from `adrs/` | File removed |

### Phase 1 Summary

| Deliverable | File | Status |
|-------------|------|--------|
| Master document (draft) | `project/docs/design-principles.md` | ⬜ |
| DPR: Testing Strategy | `project/docs/dpr-testing-strategy.md` | ✅ Phase 1.1 |
| DPR: Service Access Boundary | `project/docs/dpr-service-access-boundary.md` | ⬜ |
| DPR: Reactive Safety | `project/docs/dpr-reactive-safety.md` | ⬜ |
| DPR: Storybook Workflow | `project/docs/dpr-storybook-workflow.md` | ⬜ |
| ADR: Testing Strategy (trimmed) | `project/adrs/adr-001-testing-strategy.md` | ✅ Phase 1.1 |
| ADR: Vaadin/Hilla UI (trimmed) | `project/adrs/adr-002-vaadin-hilla-ui.md` | ⬜ |
| ADR: Storybook (trimmed) | `project/adrs/adr-004-storybook.md` | ⬜ |
| Hilla Setup Guide (moved) | `project/docs/hilla-setup-guide.md` | ⬜ |

---

## 6. Phase 2: Scanner + Infrastructure ADRs

These are the highest-priority ADRs to clean up because `adr-dynamic-scanners.md` is the most contaminated with design notes.

### Phase 2.1: `adr-dynamic-scanners.md` → DPR Migration (Priority: Highest)

**Scope**: The scanner concept — what scanners are, how they work, rate limiting, regex parsing — must be extracted into a DPR.

**DPRs to create**:

1. `docs/dpr-scanner-concept.md` — What scanners are and how they work
2. `docs/dpr-file-history-model.md` — The `FileHistory` event model, hashing, comparison
3. `docs/dpr-agent-scanner-relationship.md` — How agents subscribe to scanners, lifecycle management

**Content to migrate to DPRs:**

From `adr-dynamic-scanners.md` → `dpr-scanner-concept.md`:
- Scanner concept overview: "A scanner watches a single directory"
- How scanners work: WatchService → FileHistory → rate limit → share → filter
- Scanner lifecycle: created, active, destroyed
- Rate limiting: `delayElements(5s)`, one-at-a-time processing, backpressure
- File Read Rate Control: watch service detects immediately, reactor flux controls consumption

From `adr-dynamic-scanners.md` → `dpr-file-history-model.md`:
- `FileHistory` event structure
- Absolute path keys for unique identification
- Hash comparison for change detection
- File metadata storage approach

From `adr-dynamic-scanners.md` → `dpr-agent-scanner-relationship.md`:
- Agent subscription model: one-to-one, one-to-many, many-to-one
- `ScannerRegistry` API and subscription tracking
- `ScannerFactory` creation/destruction
- `RegexParser` extraction of `folderPattern` from agent regex
- Regex format specification (named groups, examples)
- Dynamic lifecycle: create on first subscribe, destroy on last unsubscribe

**Content to keep in ADR** (rename to `adr-005-dynamic-scanners.md`):
- Why dynamic multi-scanner was chosen over static configuration
- The decision for one-scanner-per-folder with subscription sharing
- Decision to fail fast on inaccessible folders
- Decision for immediate scanner cleanup (no delay)
- Alternatives considered (static config, agent-specific scanners, global scanner with explicit assignment, database-backed config)
- Consequences (benefits, trade-offs, coupling points)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 2.1.1 | Draft `dpr-scanner-concept.md` | `project/docs/dpr-scanner-concept.md` |
| 2.1.2 | Draft `dpr-file-history-model.md` | `project/docs/dpr-file-history-model.md` |
| 2.1.3 | Draft `dpr-agent-scanner-relationship.md` | `project/docs/dpr-agent-scanner-relationship.md` |
| 2.1.4 | Trim `adr-dynamic-scanners.md` to decision + alternatives + consequences | `project/adrs/adr-005-dynamic-scanners.md` |
| 2.1.5 | Add cross-references in all four files | All files |

### Phase 2.2: `adr-multi-db-support.md` → DPR Migration

**Scope**: Multi-database is an architectural decision, but the "how to add a third database" is a tutorial.

**DPR to create**: `docs/dpr-database-configuration.md`

**Note**: A DPR for database configuration already exists at `docs/dpr-database-configuration.md`. This ADR should be merged into the existing DPR or the existing DPR should be updated to reference this ADR.

**Content to migrate to DPR**:
- Step-by-step "How to Add a Third Database" tutorial
- `DataSourceProperties` implementation details
- JPA properties configuration
- Entity package separation rules
- Repository configuration

**Content to keep in ADR** (rename to `adr-006-multi-database.md`):
- Why separate SQLite databases were chosen for agent vs. memory data
- Decision for independent transaction management per database
- Decision for explicit `@Primary` on agent database
- Consequences (separation of concerns vs. complexity vs. distributed transactions)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 2.2.1 | Review existing `dpr-database-configuration.md` for completeness | Assessment |
| 2.2.2 | Merge ADR content into existing DPR or update DPR with ADR references | `project/docs/dpr-database-configuration.md` |
| 2.2.3 | Trim `adr-multi-db-support.md` to decision + consequences | `project/adrs/adr-006-multi-database.md` |
| 2.2.4 | Add cross-references | Both files |

### Phase 2 Summary

| Deliverable | File |
|-------------|------|
| DPR: Scanner Concept | `project/docs/dpr-scanner-concept.md` |
| DPR: File History Model | `project/docs/dpr-file-history-model.md` |
| DPR: Agent-Scanner Relationship | `project/docs/dpr-agent-scanner-relationship.md` |
| DPR: Database Configuration (updated) | `project/docs/dpr-database-configuration.md` |
| ADR: Dynamic Scanners (trimmed) | `project/adrs/adr-005-dynamic-scanners.md` |
| ADR: Multi-Database (trimmed) | `project/adrs/adr-006-multi-database.md` |

---

## 7. Phase 3: LLM + Vector Store ADRs

These ADRs are mostly architectural decisions with some implementation details that could be DPRs.

### Phase 3.1: `adr-application-memory-extraction.md` → DPR Migration

**Scope**: Memory extraction architecture — the extraction levels, prompt templates, state tracking.

**DPRs to create**:

1. `docs/dpr-memory-extraction-pipeline.md` — The extraction pipeline: MESSAGE, TURN, SESSION levels
2. `docs/dpr-state-tracking.md` — How `ExtractionState` enables incremental processing

**Content to migrate to DPRs**:
- Extraction levels: MESSAGE, TURN, SESSION — what each captures
- Prompt template system: loading, caching, formatting
- State tracking: `ExtractionState` entity, lastProcessed timestamps
- "How to Add New Extraction Level" tutorial
- LLM Adapter Pattern (synchronous vs. queued processing)

**Content to keep in ADR** (rename to `adr-007-memory-extraction.md`):
- Why LLM-based memory extraction was chosen
- Decision for multi-level extraction (MESSAGE/TURN/SESSION)
- Decision for reactive queue for LLM processing
- Decision to use prompt templates from files
- Consequences (clean architecture, testability, LLM latency, error handling)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 3.1.1 | Draft `dpr-memory-extraction-pipeline.md` | `project/docs/dpr-memory-extraction-pipeline.md` |
| 3.1.2 | Draft `dpr-state-tracking.md` | `project/docs/dpr-state-tracking.md` |
| 3.1.3 | Trim `adr-application-memory-extraction.md` | `project/adrs/adr-007-memory-extraction.md` |
| 3.1.4 | Add cross-references | All files |

### Phase 3.2: `adr-qdrant-vector-store.md` → DPR Migration

**Scope**: Qdrant integration — service implementation, document model, filter expressions.

**DPR to create**: `docs/dpr-vector-store-usage.md`

**Content to migrate to DPR**:
- `QdrantService` implementation details
- Document model structure (id, content, metadata)
- Filter expression syntax and examples
- SHA-256 content hashing algorithm for deduplication
- "How to Configure for Another Project" tutorial

**Content to keep in ADR** (rename to `adr-008-qdrant-vector-store.md`):
- Why Spring AI's VectorStore abstraction was chosen
- Decision for Qdrant as backend (vs. other vector stores)
- Decision for auto-configuration + collection auto-creation
- Trade-offs (version maturity, network dependency, no cross-DB transactions)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 3.2.1 | Draft `dpr-vector-store-usage.md` | `project/docs/dpr-vector-store-usage.md` |
| 3.2.2 | Trim `adr-qdrant-vector-store.md` | `project/adrs/adr-008-qdrant-vector-store.md` |
| 3.2.3 | Add cross-references | Both files |

### Phase 3.3: `adr-chat-model-setup-for-llama-cpp.md` → DPR Migration

**Scope**: llama.cpp setup — Docker commands, GGUF downloads, performance tuning.

**DPR to create**: `docs/dpr-llama-cpp-operations.md`

**Content to migrate to DPR**:
- Docker setup commands (chat and embedding instances)
- Binary build instructions
- GGUF model download instructions (HuggingFace sources)
- Performance tuning table (model size, context window, GPU layers, etc.)
- Verification commands (curl tests)

**Content to keep in ADR** (rename to `adr-009-llama-cpp.md`):
- Why OpenAI-compatible abstraction was chosen over native llama.cpp client
- Decision for separate instances for chat vs. embeddings
- Decision for retry logic on 503 errors
- Decision for 300s timeouts (local models are slow)
- Trade-offs (no vendor lock-in, privacy, hardware requirements, model management)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 3.3.1 | Draft `dpr-llama-cpp-operations.md` | `project/docs/dpr-llama-cpp-operations.md` |
| 3.3.2 | Trim `adr-chat-model-setup-for-llama-cpp.md` | `project/adrs/adr-009-llama-cpp.md` |
| 3.3.3 | Add cross-references | Both files |

### Phase 3.4: `adr-application-observability.md` → DPR Migration

**Scope**: Observability — component implementation, polling parameters, CSS.

**DPR to create**: `docs/dpr-observability-components.md`

**Content to migrate to DPR**:
- `AdapterStatusComponent` implementation details
- Polling strategy parameters (60s interval, 1h WARN threshold, 5s timeout)
- CSS styling conventions (status badge classes)
- Vaadin component list used (Card, Layout, Icon, TextField, Button, Notification)

**Content to keep in ADR** (rename to `adr-010-observability.md`):
- Why health-first monitoring (listModels) was chosen over test prompts
- Decision for three-state status lifecycle (UP/WARN/DOWN)
- Decision for SQLite persistence vs. in-memory cache
- Decision for background polling vs. event-driven updates
- Trade-offs (operational visibility, complexity, resource overhead)

**Tasks**

| # | Task | Output |
|---|------|--------|
| 3.4.1 | Draft `dpr-observability-components.md` | `project/docs/dpr-observability-components.md` |
| 3.4.2 | Trim `adr-application-observability.md` | `project/adrs/adr-010-observability.md` |
| 3.4.3 | Add cross-references | Both files |

### Phase 3 Summary

| Deliverable | File |
|-------------|------|
| DPR: Memory Extraction Pipeline | `project/docs/dpr-memory-extraction-pipeline.md` |
| DPR: State Tracking | `project/docs/dpr-state-tracking.md` |
| DPR: Vector Store Usage | `project/docs/dpr-vector-store-usage.md` |
| DPR: llama.cpp Operations | `project/docs/dpr-llama-cpp-operations.md` |
| DPR: Observability Components | `project/docs/dpr-observability-components.md` |
| ADR: Memory Extraction (trimmed) | `project/adrs/adr-007-memory-extraction.md` |
| ADR: Qdrant (trimmed) | `project/adrs/adr-008-qdrant-vector-store.md` |
| ADR: llama.cpp (trimmed) | `project/adrs/adr-009-llama-cpp.md` |
| ADR: Observability (trimmed) | `project/adrs/adr-010-observability.md` |

---

## 8. Phase 4: Cleanup + Cross-References

Final cleanup pass to ensure consistency, completeness, and proper cross-referencing.

### Phase 4.1: Standardize ADR Naming

Rename all remaining ADRs to numbered format:

| Old Name | New Name |
|----------|----------|
| `adr-rest-adapters.md` | `adr-001-rest-adapters.md` |
| `adr-ui-components.md` | `adr-002-vaadin-hilla-ui.md` |
| `adr-ui-views.md` | `adr-003-flow-hilla-routing.md` |
| `adr-ui-hilla-component-development.md` | `adr-004-storybook.md` |
| `adr-dynamic-scanners.md` | `adr-005-dynamic-scanners.md` |
| `adr-testing-strategy.md` | `adr-001-testing-strategy.md` |
| `adr-multi-db-support.md` | `adr-006-multi-database.md` |
| `adr-application-memory-extraction.md` | `adr-007-memory-extraction.md` |
| `adr-qdrant-vector-store.md` | `adr-008-qdrant-vector-store.md` |
| `adr-chat-model-setup-for-llama-cpp.md` | `adr-009-llama-cpp.md` |
| `adr-application-observability.md` | `adr-010-observability.md` |

**Note**: `adr-001` is a conflict between REST Adapters and Testing Strategy. Resolution: Testing Strategy gets `adr-001` (it's the most referenced), REST Adapters gets `adr-002`, and all others shift accordingly.

| Old Name | New Name (final) |
|----------|------------------|
| `adr-rest-adapters.md` | `adr-002-rest-adapters.md` |
| `adr-ui-components.md` | `adr-003-vaadin-hilla-ui.md` |
| `adr-ui-views.md` | `adr-004-flow-hilla-routing.md` |
| `adr-ui-hilla-component-development.md` | `adr-005-storybook.md` |
| `adr-dynamic-scanners.md` | `adr-006-dynamic-scanners.md` |
| `adr-testing-strategy.md` | `adr-001-testing-strategy.md` |
| `adr-multi-db-support.md` | `adr-007-multi-database.md` |
| `adr-application-memory-extraction.md` | `adr-008-memory-extraction.md` |
| `adr-qdrant-vector-store.md` | `adr-009-qdrant-vector-store.md` |
| `adr-chat-model-setup-for-llama-cpp.md` | `adr-010-llama-cpp.md` |
| `adr-application-observability.md` | `adr-011-observability.md` |

**Tasks**

| # | Task | Output |
|---|------|--------|
| 4.1.1 | Rename all ADR files to `adr-NNN-*.md` format | All ADR files renamed |
| 4.1.2 | Update all internal cross-references | All ADR files |

### Phase 4.2: Update Cross-References

Every ADR should have a `See Also` section linking to related ADRs and DPRs. Every DPR should link to its parent ADR.

**Tasks**

| # | Task | Output |
|---|------|--------|
| 4.2.1 | Add `See Also` section to each ADR with links to related ADRs and DPRs | All ADR files |
| 4.2.2 | Add parent ADR link to each DPR | All DPR files |
| 4.2.3 | Update `adr-overview.md` with final ADR inventory table | `project/adrs/adr-overview.md` |
| 4.2.4 | Update `design-principles.md` with final navigation tables | `project/docs/design-principles.md` |

### Phase 4.3: Remove Duplicate Content

Identify and eliminate duplicate content across ADRs and DPRs:

| Duplicate | Keep In | Remove From |
|-----------|---------|-------------|
| Testing strategy code examples | `dpr-testing-strategy.md` | `adr-002-rest-adapters.md` (keep only brief reference) |
| Service Access Boundary rule | `dpr-service-access-boundary.md` | `adr-003-flow-hilla-routing.md` (keep only reference) |
| `@SpringBootTest` isolation rule | `dpr-testing-strategy.md` | `adr-002-rest-adapters.md` (keep only reference) |
| Reactor Gotcha (`Mono<Void>`) | `dpr-reactive-safety.md` | `adr-002-vaadin-hilla-ui.md`, `adr-003-flow-hilla-routing.md` (keep only reference) |

**Tasks**

| # | Task | Output |
|---|------|--------|
| 4.3.1 | Audit all files for duplicate content | Audit list |
| 4.3.2 | Remove duplicates, replace with cross-references | All affected files |
| 4.3.3 | Final consistency pass | All files |

### Phase 4 Summary

| Deliverable | Status |
|-------------|--------|
| All ADRs renamed to `adr-NNN-*.md` | ✅ |
| All cross-references updated | ✅ |
| All duplicates removed | ✅ |
| `adr-overview.md` updated | ✅ |
| `design-principles.md` finalized | ✅ |

---

## 9. Final State

### ADRs (11 files)

```
project/adrs/
├── adr-overview.md                      # ADR format guide (unchanged)
├── adr-001-testing-strategy.md          # Why six-tier testing pyramid
├── adr-002-rest-adapters.md             # Why REST adapter pattern
├── adr-003-vaadin-hilla-ui.md           # Why Vaadin + Hilla
├── adr-004-flow-hilla-routing.md        # Why hybrid Flow/Hilla routing
├── adr-005-storybook.md                 # Why Storybook for component dev
├── adr-006-dynamic-scanners.md          # Why dynamic multi-scanner
├── adr-007-multi-database.md            # Why multi-database
├── adr-008-memory-extraction.md         # Why LLM-based memory extraction
├── adr-009-qdrant-vector-store.md       # Why Spring AI VectorStore + Qdrant
├── adr-010-llama-cpp.md                 # Why OpenAI-compatible abstraction
└── adr-011-observability.md             # Why health-first monitoring
```

### Design Notes (12 files)

```
project/docs/
├── design-principles.md                 # Master index (NEW)
├── dpr-database-configuration.md        # Existing + merged from ADR
├── dpr-testing-strategy.md              # NEW (from adr-testing-strategy.md)
├── dpr-service-access-boundary.md       # NEW (from adr-ui-components.md)
├── dpr-reactive-safety.md               # NEW (from adr-ui-components.md)
├── dpr-storybook-workflow.md            # NEW (from adr-ui-hilla-component-development.md)
├── dpr-scanner-concept.md               # NEW (from adr-dynamic-scanners.md)
├── dpr-file-history-model.md            # NEW (from adr-dynamic-scanners.md)
├── dpr-agent-scanner-relationship.md    # NEW (from adr-dynamic-scanners.md)
├── dpr-memory-extraction-pipeline.md    # NEW (from adr-application-memory-extraction.md)
├── dpr-state-tracking.md                # NEW (from adr-application-memory-extraction.md)
├── dpr-vector-store-usage.md            # NEW (from adr-qdrant-vector-store.md)
├── dpr-llama-cpp-operations.md          # NEW (from adr-chat-model-setup-for-llama-cpp.md)
├── dpr-observability-components.md      # NEW (from adr-application-observability.md)
└── hilla-setup-guide.md                 # MOVED (from adr-hilla-setup-guide.md)
```

### What Each Contains

| Document Type | Contains |
|---------------|----------|
| **ADR** | Why we made the decision, alternatives considered, consequences (trade-offs), coupling points |
| **DPR** | How the concept works, implementation details, code examples, tutorials, how-to guides |
| **`design-principles.md`** | Master index linking all ADRs and DPRs, standing design principles, naming conventions |

---

## 10. Quality Checklist

Before considering each phase complete, verify:

- [ ] The new DPR exists and contains all migrated content
- [ ] The trimmed ADR still makes sense as a standalone decision record
- [ ] No content was lost — every detail is either kept in the ADR or moved to the DPR
- [ ] Cross-references added in both directions (ADR ↔ DPR)
- [ ] The ADR's `See Also` section links to related ADRs and DPRs
- [ ] The DPR's first section references its parent ADR
- [ ] Code examples in DPRs are consistent with current codebase
- [ ] File names follow the naming convention (`adr-NNN-*.md`, `dpr-*.md`)

---

## 11. Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Content loss during migration | High | Draft DPR before modifying ADR; compare side-by-side before proceeding |
| Broken cross-references | Medium | Use relative paths; verify all links after each phase |
| Duplicate content persists | Low | Phase 4.3 is dedicated deduplication pass |
| Naming conflicts (adr-001) | Low | Pre-resolved in Section 4.1 |
| Team doesn't understand separation | Medium | Include clear examples in `adr-overview.md` and `design-principles.md` |
| Plans folder content overlaps with DPRs | Low | Keep `plans/` for implementation plans; DPRs for design concepts |

---

## 12. Execution Order

```
Phase 0: Draft design-principles.md
    ↓
✅ Phase 1.1: adr-testing-strategy.md → dpr-testing-strategy.md        (DONE)
Phase 1.2: adr-ui-components.md → dpr-service-access-boundary.md + dpr-reactive-safety.md
Phase 1.3: adr-ui-hilla-component-development.md → dpr-storybook-workflow.md
Phase 1.4: adr-hilla-setup-guide.md → docs/hilla-setup-guide.md (move, no rename)
    ↓
Phase 2.1: adr-dynamic-scanners.md → dpr-scanner-concept.md + dpr-file-history-model.md + dpr-agent-scanner-relationship.md
Phase 2.2: adr-multi-db-support.md → update dpr-database-configuration.md
    ↓
Phase 3.1: adr-application-memory-extraction.md → dpr-memory-extraction-pipeline.md + dpr-state-tracking.md
Phase 3.2: adr-qdrant-vector-store.md → dpr-vector-store-usage.md
Phase 3.3: adr-chat-model-setup-for-llama-cpp.md → dpr-llama-cpp-operations.md
Phase 3.4: adr-application-observability.md → dpr-observability-components.md
    ↓
Phase 4.1: Rename all ADRs to adr-NNN-*.md
Phase 4.2: Update all cross-references
Phase 4.3: Remove duplicate content
Phase 4.4: Final consistency pass
```

---

## 13. Next Steps

1. **Proceed to Phase 0** — draft `design-principles.md` (the anchor for everything else)
2. **Proceed to Phase 1.2** — clean up `adr-ui-components.md` into DPRs
3. **Proceed phase by phase** — each phase produces working deliverables before the next begins
4. **Review at each phase boundary** — ensure quality before proceeding
