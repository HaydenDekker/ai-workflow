# ADR-001: Testing Strategy

## Date

2026-04-28

## Status

Accepted

---

## Context

The application has grown to include multiple layers of complexity:
- **Spring Boot application** with JPA, REST controllers, file scanners, LLM adapters
- **Vaadin/Hilla UI** with Flow components and TypeScript views
- **Multiple databases** (SQLite for agents, H2 for tests)
- **External services** (llama.cpp)
- **Reactive pipelines** (Project Reactor Flux/mono)

Previous testing guidance was scattered across individual ADRs (`adr-002-rest-adapters`, `adr-003-vaadin-hilla-ui`, `adr-004-flow-hilla-routing`, `adr-005-storybook`) with no single source of truth. This led to:

- **Inconsistent test annotations**: Some tests used `@SpringBootTest` without `classes=`, loading the full application context and persisting YAML agents to the database
- **Unclear test tier boundaries**: Developers unsure whether to use `@DataJpaTest`, `@WebMvcTest`, or `@SpringBootTest`
- **Missing integration test guidance**: No standard for tagging tests that require external services (LLM, vector store)
- **No unified testing pyramid**: Each ADR described its own testing approach without cross-referencing

A consolidated testing strategy was needed to define clear test tier boundaries, standardize annotation choices, and prevent database pollution from `AgentConfiguration` persisting YAML agents during tests.

## Decision

Adopt a **six-tier testing pyramid** as the standard testing approach. A detailed design note (`dpr-testing-strategy`) covers how to implement each tier; this ADR records the decision and its rationale.

- **Tier 1** (Unit): Business logic without Spring — `@ExtendWith(MockitoExtension.class)`
- **Tier 2** (Data JPA): Repository/entity tests — `@DataJpaTest` with H2 in-memory
- **Tier 3** (Test Slices): Controllers and HTTP clients — `@WebMvcTest` / `@RestClientTest`
- **Tier 4** (Browserless UI): Vaadin components without a browser — `BrowserlessTest`
- **Tier 5** (Integration): Specific subsystems with real beans — `@SpringBootTest(classes = TestConfig.class)`
- **Tier 6** (E2E): Critical flows with real browser — Playwright

### Key Design Decisions

1. **H2 in-memory for Tier 2** — `@DataJpaTest` auto-configures H2; no file I/O, instant cleanup, faster than SQLite
2. **BrowserlessTest over Playwright for component tests** — 100× faster, direct Java API access to views
3. **Minimal `@SpringBootTest(classes = ...)`** — Always specify `classes=` to prevent `AgentConfiguration` from persisting YAML agents to the database
4. **`@MockitoBean` over `@MockBean`** — Spring Boot 4.0+ uses Mockito's inline mock maker directly, avoiding proxy overhead

## Migration Path

To migrate existing tests to this strategy:

1. **Identify tests using `@SpringBootTest` without `classes=`** — Highest priority to fix
2. **Replace with `@SpringBootTest(classes = TestConfig.class)`** — Create a minimal `@TestConfiguration` with only the beans needed
3. **Replace `@MockBean` with `@MockitoBean`** — Update all test classes using the deprecated annotation
4. **Tag integration tests with `@Tag("integration")`** — For tests requiring external services
5. **Move repository tests to `@DataJpaTest`** — If they currently use `@SpringBootTest` with SQLite

## See Also

- [ADR Overview](adr-overview.md) — ADR format and structure (includes testing strategy section 5f)
- [DPR: Testing Strategy](../docs/dpr-testing-strategy.md) — Detailed implementation for each tier
