# ADR: Testing Strategy

## Date

2026-04-28

## Context

The application has grown to include multiple layers of complexity:
- **Spring Boot application** with JPA, REST controllers, file scanners, LLM adapters
- **Vaadin/Hilla UI** with Flow components and TypeScript views
- **Multiple databases** (SQLite for agents, H2 for tests)
- **External services** (llama.cpp, Qdrant, Ollama)
- **Reactive pipelines** (Project Reactor Flux/mono)

Previous testing guidance was scattered across individual ADRs (adr-rest-adapters, adr-multi-db-support, adr-ui-components, adr-ui-hilla-component-development, adr-ui-views) with no single source of truth. This led to:

- **Inconsistent test annotations**: Some tests used `@SpringBootTest` without `classes=`, loading the full application context and persisting YAML agents to the database
- **Unclear test tier boundaries**: Developers unsure whether to use `@DataJpaTest`, `@WebMvcTest`, or `@SpringBootTest`
- **Missing integration test guidance**: No standard for tagging tests that require external services (LLM, vector store)
- **No unified testing pyramid**: Each ADR described its own testing approach without cross-referencing

A consolidated testing strategy is needed to:
1. Define clear test tiers with expected execution times
2. Standardize annotation choices per test type
3. Document the `@SpringBootTest` isolation rule to prevent database pollution
4. Provide a decision tree for choosing the right test slice
5. Establish conventions for tagging integration tests

## Decision

### Testing Pyramid

The application follows a six-tier testing pyramid (fastest → slowest):

| Tier | Test Type | Annotation | Scope | Database | Expected Speed | Example |
|------|-----------|-----------|-------|----------|---------------|---------|
| **1** | Unit | `@ExtendWith(MockitoExtension.class)` | Single class, no Spring | None | < 1 ms | `AgentLifecycleUseCaseTest` — mocks `ScannerRegistry`, `AgentPersistenceUsecase` |
| **2** | Data JPA | `@DataJpaTest` | Repository layer | H2 in-memory | < 1 s | `AgentRepositoryTest` — entity mapping, repository queries |
| **3** | Test Slices | `@WebMvcTest`, `@RestClientTest` | Controller or HTTP client | None (mocked) | < 5 s | `AgentRestControllerTest` — `@WebMvcTest` with `@MockitoBean`; `OpenAiHealthClientRestClientTest` — `@RestClientTest` with `MockRestServiceServer` |
| **4** | Browserless (UI) | `@ExtendWith(SpringExtension.class)` + `BrowserlessTest` | Vaadin Flow components/views | None (server-side) | 5–60 ms | `AgentListViewDeleteTest` — navigates to view, queries components |
| **5** | Integration | `@SpringBootTest(classes = TestConfig.class)` | Full context, minimal beans | H2 in-memory (test profile) or SQLite (production) | 1–30 s | `PromptConfigurationTest` — loads only `PromptConfiguration` + `SystemPromptConfiguration` |
| **6** | E2E | Playwright (`*.spec.ts`) | Full application + browser | Production SQLite | 1–30 s | `agents.spec.ts` — real Chromium, real Spring Boot server |

> **Tier 0: Disabled** — `@Disabled` tests that are known to fail or require rework (e.g., `FileIntegrationFlowTest`). Keep them in the codebase for tracking but exclude from test runs.

### Tier 1: Unit Tests

**When to use**: Testing business logic that can run without Spring.

**Annotation**: `@ExtendWith(MockitoExtension.class)` or JUnit 5 `@TestInstance(PER_CLASS)`.

**Rules**:
- No `@SpringBootTest`, `@DataJpaTest`, or any Spring annotation
- Mock all external dependencies (services, repositories, HTTP clients)
- Use `@InjectMocks` for the class under test
- Test pure logic: algorithms, data transformations, validation rules

```java
@ExtendWith(MockitoExtension.class)
class AgentLifecycleUseCaseTest {
    @Mock
    private ScannerRegistry scannerRegistry;
    
    @Mock
    private AgentPersistenceUsecase persistenceService;
    
    @InjectMocks
    private AgentLifecycleUseCase manager;
    
    @Test
    void addDynamicAgent_createsScannerAndPersists() {
        // Arrange
        when(scannerRegistry.createForAgent(anyString(), any(), anyInt()))
            .thenReturn(new ScannerInfo("scanner-1", "agent-1", "/tmp/dir", "IDLE", null, null));
        
        // Act
        manager.addDynamicAgent(agentDef, "/tmp/dir");
        
        // Assert
        verify(scannerRegistry).createForAgent(eq("agent-1"), eq("/tmp/dir"), eq(5));
    }
}
```

### Tier 2: Data JPA Tests

**When to use**: Testing entity mappings, repository queries, and JPA behavior.

**Annotation**: `@DataJpaTest` (auto-configures H2 in-memory by default).

**Rules**:
- Uses H2 in-memory database (fast, no file I/O, auto-cleanup between tests)
- Does NOT load the main application context
- Does NOT trigger `AgentConfiguration.initializeFromYAML()`
- Uses `@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)` for test-specific properties

```java
@DataJpaTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
class AgentRepositoryTest {
    @Autowired
    private AgentRepository repository;
    
    @Test
    void givenAgent_whenSaved_thenReturnAgentWithId() {
        AgentEntity entity = new AgentEntity();
        entity.setId("test-1");
        entity.setTitle("Test Agent");
        entity.setSource("YAML");
        
        AgentEntity saved = repository.save(entity);
        
        assertThat(saved.getId()).isEqualTo("test-1");
    }
}
```

**Why H2 and not SQLite?**
- `@DataJpaTest` auto-configures H2 in-memory — no file I/O, instant cleanup
- H2 is compatible with standard JPA operations used in repository tests
- SQLite is reserved for full-context integration tests that need production-parity

### Tier 3: Test Slices

#### 3a. Driving Adapters — `@WebMvcTest`

**When to use**: Testing REST controllers (inbound HTTP requests).

**Annotation**: `@WebMvcTest(ControllerClass.class)` (optional class parameter to scope to specific controller).

**Rules**:
- Loads only `@RestController` beans and Spring MVC auto-configuration
- Does NOT load `@Service` or `@Component` beans — mock them with `@MockitoBean`
- Uses `MockMvc` for HTTP testing (no real server started)
- Uses `@MockitoBean` (Spring Boot 4.0+) instead of deprecated `@MockBean`

```java
@WebMvcTest(AgentRestController.class)
class AgentRestControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private AgentLifecycleUseCase dynamicAgentManager;
    
    @Test
    void whenCreateAgent_thenReturnCreatedAgent() throws Exception {
        when(dynamicAgentManager.addDynamicAgent(any(), anyString()))
            .thenReturn(expectedInfo);
        
        mockMvc.perform(post("/api/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("test-id-1"));
    }
}
```

#### 3b. Driven Adapters — `@RestClientTest`

**When to use**: Testing HTTP clients (outbound calls to external services).

**Annotation**: `@RestClientTest(ClientClass.class)` (optional class parameter).

**Rules**:
- Auto-configures Jackson, `RestClient.Builder`, and `MockRestServiceServer`
- The client is **not** a `@Component` — register it via `@Bean` in a nested `@Configuration`
- Use relative URIs in expectations (e.g., `/v1/models`) because `@RestClientTest` binds the mock server to the builder
- `MockRestServiceServer.reset()` is handled automatically between tests

```java
@RestClientTest
class OpenAiHealthClientRestClientTest {
    @Configuration
    static class TestConfig {
        @Bean
        OpenAiHealthClient openAiHealthClient(RestClient.Builder builder) {
            return new OpenAiHealthClient(builder);
        }
    }
    
    @Autowired private OpenAiHealthClient client;
    @Autowired private MockRestServiceServer server;
    
    @Test
    void listModels_success_returnsModelNames() {
        server.expect(requestTo("/v1/models"))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
        
        List<String> modelNames = client.listModels().block();
        assertThat(modelNames).hasSize(2);
    }
}
```

### Tier 4: Browserless Tests (UI)

**When to use**: Testing Vaadin Flow components and views without a browser.

**Annotation**: `@ExtendWith(SpringExtension.class)` + `BrowserlessTest` (Vaadin 25.1+).

**Rules**:
- Runs 100× faster than Playwright (server-side, no browser, no servlet container)
- Provides direct Java API access to view components
- Fails immediately on misconfiguration (no timeout waits)
- Use `@ViewPackages` to restrict classpath scanning

```java
@ExtendWith({ SpringExtension.class, TreeOnFailureExtension.class })
@SpringBootTest(classes = AgentListViewDeleteTest.Config.class)
@ViewPackages(classes = { AgentListView.class })
class AgentListViewDeleteTest extends SpringBrowserlessTest {
    @Autowired
    private MockAgentLifecycleUseCase mockManager;
    
    @BeforeEach
    void setUp() {
        mockManager.reset();
        view = navigate(AgentListView.class);
    }
    
    @Test
    void deleteAgent_viaDetailDialog_gridUpdated() {
        // Navigate, click, verify — all server-side
    }
}
```

### Tier 5: Integration Tests

**When to use**: Testing a specific subsystem with real Spring beans (but not the full context).

**Annotation**: `@SpringBootTest(classes = TestConfig.class)` with a minimal `@TestConfiguration`.

**Rules**:
- **ALWAYS** specify `classes =` to load only the beans needed
- Use a nested `@TestConfiguration` with `@Bean` methods for the beans your test requires
- Use `@DynamicPropertySource` to override properties (e.g., temp directories, test URLs)
- Use `@TestPropertySource` for static property overrides
- Does NOT load `AgentConfiguration` (which persists YAML agents to the database)

```java
@SpringBootTest(classes = PromptConfigurationTest.TestConfig.class)
class PromptConfigurationTest {
    @TempDir
    static Path tempDir;
    
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("prompt-config.predefinedPromptFilePath", () -> tempDir.toAbsolutePath().toString());
        registry.add("yaml.agents.enabled", () -> "false");
    }
    
    @Autowired
    SystemPromptConfiguration systemPromptConfiguration;
    
    @Test
    void promptConfigurationsReadIntoMemory() {
        assertThat(systemPromptConfiguration.getAgentWorkflows())
            .hasSizeGreaterThan(1);
    }
    
    @Configuration
    static class TestConfig {
        @Bean
        SystemPromptConfiguration systemPromptConfiguration(PromptConfiguration promptConfiguration,
                ResourcePatternResolver resourcePatternResolver) {
            return new SystemPromptConfiguration(promptConfiguration, resourcePatternResolver);
        }
    }
}
```

#### The `@SpringBootTest` Isolation Rule

> **⚠️ Loading the full context (`@SpringBootTest` without `classes=`) starts `AgentConfiguration`, which**
> calls `initializeFromYAML()` and persists YAML agents to the database. This violates test isolation
> and causes test data leakage between test runs.

**Why this happens**:
1. `@SpringBootTest` loads the entire application context (component scanning)
2. `AgentConfiguration` is a `@Configuration` class that runs `initializeFromYAML()` in its constructor
3. `initializeFromYAML()` calls `persistenceService.save()` for each YAML agent, writing to the SQLite database
4. The `@Transactional` annotation on tests does NOT reliably rollback with SQLite + Hibernate

**Exceptions**: `@SpringBootTest` without `classes=` is acceptable only when you explicitly want to test the full application context behavior (e.g., startup, auto-configuration). Tag such tests with `@Tag("full-context")` so they can be excluded from fast test runs.

### Tier 6: E2E Tests (Playwright)

**When to use**: Testing critical user flows with a real browser and real Spring Boot server.

**Tool**: Playwright (`@playwright/test` in `package.json`).

**Rules**:
- Keep E2E tests to **critical flows only**: navigation, authentication, primary user journeys
- Use Playwright's implicit waits (`expect(locator).toBeVisible()`) — avoid `waitForTimeout()`
- Takes screenshots, video, and traces on failure
- Runs headless by default; use `--headed` flag for debugging

```typescript
// tests/e2e/agents.spec.ts
test('Agent creation flow', async ({ page }) => {
  await page.goto('/agents');
  
  // Click create button
  await page.getByRole('button', { name: 'Create Agent' }).click();
  
  // Fill form
  await page.getByLabel('Title').fill('Test Agent');
  await page.getByLabel('File Input Regex').fill('.*\\.txt');
  
  // Submit
  await page.getByRole('button', { name: 'Create' }).click();
  
  // Verify agent appears in grid
  await expect(page.getByText('Test Agent')).toBeVisible();
});
```

**E2E test setup** (see `playwright.config.ts`):
- Global setup starts `./mvnw spring-boot:run` and waits for the server
- Tests run in Chromium (headless by default)
- After tests: stops the server, captures screenshots/video/traces on failure

### Test Execution Commands

```bash
# All tests (unit + JPA + slices + browserless + integration)
./mvnw test

# All tests with minimal output (recommended for CI)
./mvnw test -q

# Unit tests only (fastest)
./mvnw test -Dtest="*Test" -DexcludedGroups=integration

# Data JPA tests only
./mvnw test -Dtest="*RepositoryTest"

# Test slices only
./mvnw test -Dtest="*RestControllerTest"
./mvnw test -Dtest="*ClientRestClientTest"

# Integration tests (requires external services)
./mvnw verify -Dit.test="*IntegrationTest"

# E2E tests (requires Node.js, Playwright, Chromium)
npm run test:e2e

# E2E tests with visible browser (debugging)
npm run test:e2e:headed
```

### Test Classification Decision Tree

```
Does the test require Spring?
├── No → Tier 1: Unit Test (@ExtendWith(MockitoExtension.class))
│
├── Yes, testing repository/entity? → Tier 2: Data JPA (@DataJpaTest + H2)
│
├── Yes, testing REST controller? → Tier 3a: @WebMvcTest (@MockitoBean for services)
│
├── Yes, testing HTTP client? → Tier 3b: @RestClientTest (MockRestServiceServer)
│
├── Yes, testing Vaadin UI component? → Tier 4: BrowserlessTest
│
├── Yes, testing full Spring context but NOT external services? → Tier 5: @SpringBootTest(classes = TestConfig.class)
│
└── Yes, testing end-to-end with real browser? → Tier 6: Playwright E2E
```

### Test Isolation Rules

| Rule | Reason | Example |
|------|--------|---------|
| **Never write to `/tmp/ai-workflow.db` in tests** | Causes test data leakage between runs | Use `@TempDir` for file-based tests; use H2 in-memory for JPA tests |
| **Always use `@SpringBootTest(classes = ...)`** | Prevents `AgentConfiguration` from persisting YAML agents | See `PromptConfigurationTest` for the pattern |
| **Use `@Tag("integration")` for external service tests** | Allows skipping in CI when services are unavailable | `@Tag("integration")` on `OpenAiInstanceAdapterIntegrationTest` |
| **Use `@TempDir` for file-based tests** | Ensures cleanup between tests | `@TempDir Path tempDir` in `FileSystemScannerAdapterTest` |
| **Mock external services in unit/integration tests** | Tests should be deterministic and fast | `@MockitoBean ChatClient`, `MockRestServiceServer` for HTTP |
| **Do not use `@MockBean`** | Deprecated in Spring Boot 4.0+; use `@MockitoBean` instead | `@MockitoBean` uses Mockito's inline mock maker directly |

### POM Dependencies

```xml
<!-- Base test dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Test slices -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-restclient-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Vaadin Browserless testing (Vaadin 25.1+) -->
<dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>browserless-test-junit6</artifactId>
    <scope>test</scope>
</dependency>

<!-- H2 in-memory database (for @DataJpaTest) -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

## Consequences

### Benefits

1. **Clear test tier boundaries** — Developers know which annotation to use for each test type
2. **No database pollution** — The `@SpringBootTest` isolation rule prevents YAML agent leakage
3. **Fast test feedback** — Unit + JPA + slice tests run in seconds, not minutes
4. **Selective test execution** — Tagged integration tests can be skipped when external services are unavailable
5. **Consistent patterns** — All ADRs can reference this document instead of duplicating testing guidance
6. **Better CI/CD** — Different test tiers can run with different timeouts and parallelism settings

### Trade-offs

1. **More documentation to maintain** — Centralized strategy requires updates when new patterns emerge
2. **Learning curve** — New developers must understand the testing pyramid and annotation choices
3. **Test configuration overhead** — Minimal `@TestConfiguration` classes add boilerplate compared to bare `@SpringBootTest`

### Tight Coupling Points

- **`AgentConfiguration` constructor** — Runs `initializeFromYAML()` unconditionally (unless `yaml.agents.enabled=false`). This is the root cause of the `@SpringBootTest` isolation issue.
- **SQLite + Hibernate transaction rollback** — `@Transactional` does NOT reliably rollback with SQLite, making file-based test cleanup unreliable. Prefer H2 in-memory for tests.

### Important Notes

1. **`@MockitoBean` replaces `@MockBean`** — Spring Boot 4.0+ uses `@MockitoBean` which leverages Mockito's inline mock maker directly, avoiding proxy overhead.
2. **`@DataJpaTest` uses H2 by default** — Do not override with SQLite in repository tests. Use `@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)` for test-specific properties.
3. **BrowserlessTest requires Vaadin 25.1+** — Check the project's Vaadin version before using this approach.
4. **Playwright E2E tests start the full Spring Boot server** — These are the only tier that requires the complete application context. They are intentionally slow and should be kept minimal.

## Migration Path

To migrate existing tests to this strategy:

1. **Identify tests using `@SpringBootTest` without `classes=`** — These are the highest priority to fix
2. **Replace with `@SpringBootTest(classes = TestConfig.class)`** — Create a minimal `@TestConfiguration` with only the beans needed
3. **Replace `@MockBean` with `@MockitoBean`** — Update all test classes using the deprecated annotation
4. **Tag integration tests with `@Tag("integration")`** — For tests that require external services
5. **Move repository tests to `@DataJpaTest`** — If they currently use `@SpringBootTest` with SQLite

## Alternatives Considered

1. **Single `@SpringBootTest` for all tests** — Rejected because it loads the full application context (slow), requires real or embedded database/config, and causes test data leakage.
2. **No test slices — just Mockito** — Rejected because test slices provide auto-configuration for HTTP testing (`MockMvc`, `MockRestServiceServer`), reducing boilerplate and ensuring realistic test setup.
3. **Browser testing for all UI tests** — Rejected because BrowserlessTest runs 100× faster and provides direct Java API access. Playwright is reserved for end-to-end flows only.
4. **SQLite for all database tests** — Rejected because `@Transactional` rollback is unreliable with SQLite + Hibernate. H2 in-memory is faster, cleaner, and compatible with standard JPA operations.

## See Also

- [ADR Overview](adr-overview.md) — ADR format and structure (includes testing strategy section 5f)
- [ADR-001: REST Adapters](adr-rest-adapters.md) — `@WebMvcTest`, `@RestClientTest` patterns
- [ADR: Multi-DB Support](adr-multi-db-support.md) — `@DataJpaTest` with H2 vs `@SpringBootTest` with SQLite
- [ADR: UI Components](adr-ui-components.md) — BrowserlessTest for Vaadin components
- [ADR: UI Views](adr-ui-views.md) — BrowserlessTest vs Playwright E2E for views

## References

- Spring Boot Testing: https://docs.spring.io/spring-boot/reference/testing/
- Spring Boot Test Slices: https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html
- Mockito Inline Mock Maker: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html
- Vaadin Browserless Testing: https://vaadin.com/docs/latest/testing/browserless
- Playwright Java: https://playwright.dev/java
