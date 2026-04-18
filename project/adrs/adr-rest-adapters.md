# ADR-001: REST Adapters

## Status

Accepted

## Date

2026-04-17

## Context

The application needs to communicate with external LLM endpoints (OpenAI-compatible and Ollama) for health monitoring and model discovery, and also expose its own REST endpoints for the UI and other clients. These are two distinct directions of communication: inbound HTTP requests (driving the application) and outbound HTTP calls (driven by the application). We need a consistent pattern for both directions that is testable, configurable, and maintainable.

## Decision

We use a **two-layer adapter pattern** for **driven adapters** (outbound) and a **REST controller pattern** for **driving adapters** (inbound), both within the Ports & Adapters (Hexagonal Architecture) paradigm.

### Driving Adapters (Inbound)

REST controllers receive HTTP requests from external clients (browsers, UI, other services) and drive the application. They belong in the `rest` package and use Spring MVC annotations (`@RestController`, `@RequestMapping`, etc.).

```
com.hdekker.ai_workflow.rest/
├── AgentRestController.java           # Driving adapter: handles inbound /api/agents
├── ObservabilityRestController.java   # Driving adapter: handles inbound /api/observability
└── dto/                               # Shared DTOs (inbound + outbound data shapes)
    ├── AdapterStatus.java
    ├── AgentInfo.java
    ├── LLMStatus.java
    └── OpenAiModelsResponse.java
```

**Testing:** We use `@WebMvcTest` (Spring Boot's MVC test slice) to test driving adapters in isolation. It auto-configures Spring MVC, loads only `@RestController` beans, and mocks the rest of the application.

```java
@WebMvcTest(ObservabilityRestController.class)
public class ObservabilityRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LLMStatusService llmStatusService;

    @Test
    public void whenGetLLMStatus_thenReturnsStatusList() throws Exception {
        when(llmStatusService.getCurrentStatus()).thenReturn(List.of(status));

        mockMvc.perform(get("/api/observability/llm-status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }
}
```

Key points:
- `@WebMvcTest` auto-configures Spring MVC, Jackson, and `MockMvc`
- Use `@MockitoBean` to mock service dependencies (Spring Boot 4.0+)
- Test the HTTP contract: status codes, response bodies, JSON paths
- Does NOT start a real server — uses mock servlet environment
- Does NOT scan `@Service` or `@Component` beans — they must be mocked

### Driven Adapters (Outbound)

REST clients call external services (LLM endpoints) that are driven by the application. They belong in the `llm` package and follow a two-layer split:

```
com.hdekker.ai_workflow.llm/
├── OpenAiHealthClient.java            # REST client (HTTP layer)
├── OpenAiHealthAdapter.java           # Business adapter (logic layer)
├── OpenAiHealthConfiguration.java     # Spring configuration
├── OpenAiInstanceAdapterUtils.java    # Shared utilities
├── OpenAiInstanceConfiguration.java   # Instance configuration
├── OpenAiInstanceConfigurationProperties.java
└── output/
    └── LLMOutputParsingUtils.java     # Output parsing utilities
```

#### REST Client

The client wraps `RestClient` for a single endpoint. It is instantiated with either a base URL or a pre-configured `RestClient`/`RestClient.Builder`:

```java
public class OpenAiHealthClient {
    private final RestClient restClient;

    // Production: built from endpoint URL and timeout
    public OpenAiHealthClient(String endpoint, int timeoutMs) {
        this.restClient = RestClient.builder()
            .baseUrl(endpoint)
            .build();
    }

    // Test: injected with pre-configured RestClient
    public OpenAiHealthClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // Test: injected with RestClient.Builder (for @RestClientTest)
    public OpenAiHealthClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public Mono<List<String>> listModels() {
        return Mono.fromCallable(() -> {
            OpenAiModelsResponse response = restClient.get()
                .uri("/v1/models")
                .retrieve()
                .body(OpenAiModelsResponse.class);
            // parse and return model IDs
        });
    }
}
```

#### Business Adapter

The adapter owns the business logic: calling the client, handling timeouts, mapping results to domain objects, and logging. It creates the client internally.

```java
public class OpenAiHealthAdapter {
    private final int timeoutMs;

    public Mono<LLMStatus> checkHealth(String endpoint, String configuredModel) {
        OpenAiHealthClient client = new OpenAiHealthClient(endpoint, timeoutMs);
        
        return client.listModels()
            .map(modelNames -> new LLMStatus(endpoint, configuredModel, AdapterStatus.UP, ...))
            .onErrorResume(e -> Mono.just(downStatus(...)))
            .timeout(Duration.ofMillis(timeoutMs))
            .onErrorResume(timeoutEx -> Mono.just(timeoutStatus(...)));
    }
}
```

### Configuration

Configuration flows through `ObservabilityProperties` (prefix: `app.observability`), wired via `OpenAiHealthConfiguration`:

```java
@ConfigurationProperties(value = "app.observability")
public class ObservabilityProperties {
    private long pollingInterval = 60000;
    private long warnAfterHours = 1;
    private int healthTimeout = 5000;
}
```

```java
@Configuration
public class OpenAiHealthConfiguration {
    @Bean
    public OpenAiHealthAdapter openAiHealthAdapter(ObservabilityProperties props) {
        return new OpenAiHealthAdapter(props.getHealthTimeout());
    }
}
```

In `application.yml`:

```yaml
app:
  observability:
    polling-interval: 60000
    warn-after-hours: 1
    health-timeout: 5000
```

### Testing Driven Adapters

We use `@RestClientTest` to test the REST client's HTTP interaction via `MockRestServiceServer`. A nested `@Configuration` with a `@Bean` method provides the client from a `RestClient.Builder`:

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

Key points:
- `@RestClientTest` auto-configures Jackson, `RestClient.Builder`, and `MockRestServiceServer`
- The client is **not** a `@Component` — it's registered via `@Bean` in a nested `@Configuration`
- Use **relative URIs** in expectations (e.g., `/v1/models`) because `@RestClientTest` binds the mock server to the builder, not the full URL
- `MockRestServiceServer.reset()` is handled automatically between tests

### Testing Driving Adapters

We use `@WebMvcTest` to test REST controllers. It loads only `@RestController` beans and mocks the rest of the application context:

```java
@WebMvcTest(ObservabilityRestController.class)
public class ObservabilityRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LLMStatusService llmStatusService;

    @Test
    public void whenGetLLMStatus_thenReturnsStatusList() throws Exception {
        when(llmStatusService.getCurrentStatus()).thenReturn(List.of(status));

        mockMvc.perform(get("/api/observability/llm-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }
}
```

Key points:
- `@WebMvcTest` auto-configures Spring MVC, Jackson, and `MockMvc`
- Use `@MockitoBean` (Spring Boot 4.0+) to mock service dependencies
- Test the HTTP contract: status codes, response bodies, JSON paths
- Does NOT start a real server — uses mock servlet environment
- Does NOT load `@Service` or `@Component` beans — they must be mocked via `@MockitoBean`
- Use `@WebMvcTest(AgentRestController.class)` to scope to a specific controller, or omit the argument to load all `@RestController` beans

### POM Dependencies

Both test slices are provided by Spring Boot test starters. Add these to `pom.xml`:

```xml
<!-- Driving adapter tests (@WebMvcTest) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Driven adapter tests (@RestClientTest) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-restclient-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Base test dependencies (required by both) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Test auto-configuration (required for @MockitoBean in Spring Boot 4.0+) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-test-autoconfigure</artifactId>
    <scope>test</scope>
</dependency>
```

### Comparison: Driving vs Driven Adapters

| Aspect | Driving Adapter (`@RestController`) | Driven Adapter (`OpenAiHealthClient`) |
|--------|-------------------------------------|---------------------------------------|
| Direction | Inbound (external client → app) | Outbound (app → external service) |
| Role | Drives the application | Is driven by the application |
| Package | `rest/` | `llm/` |
| Test annotation | `@WebMvcTest` | `@RestClientTest` |
| POM dependency | `spring-boot-webmvc-test` | `spring-boot-restclient-test` |
| Mock tool | `MockMvc` | `MockRestServiceServer` |
| Dependency injection | `@Autowired` on controller fields | Three-constructor pattern (production + two test constructors) |
| Test config | `@MockitoBean` for service mocks | Nested `@Configuration` with `@Bean` method |
| What's tested | HTTP contract (status, JSON body) | HTTP interaction (request/response mapping) |

## Trade-offs

| Aspect | Benefit | Cost |
|--------|---------|------|
| Driving adapter pattern (`@RestController`) | Clean HTTP contract, easy to test with `@WebMvcTest` | More classes per endpoint |
| Driven adapter two-layer pattern | Clear separation: HTTP vs. business logic | More classes per external service |
| Non-singleton clients | Each call can target a different endpoint | No connection pooling across calls |
| `@WebMvcTest` | Fast, isolated controller testing | Requires `@MockitoBean` for all dependencies |
| `@RestClientTest` | Spring-native, minimal boilerplate | Requires nested `@Configuration` for non-`@Component` beans |

## Alternatives Considered

1. **Inject `RestClient` as a singleton `@Bean`** — Rejected because each endpoint needs its own `baseUrl`. Would require a map of beans or a factory.
2. **Make client a `@Component`** — Rejected because it would be picked up by component scanning, causing Spring to try to instantiate it with no default constructor. The three-constructor ambiguity also prevents Spring from choosing the right one.
3. **Use `@RestClientTest` with `@Import`** — Rejected because `@Import` registers the class as a bean but Spring still can't resolve which constructor to use. A nested `@Configuration` with `@Bean` gives explicit control.
4. **Use `@SpringBootTest` for controller tests** — Rejected because it loads the full application context (slow) and requires real or embedded database/config. `@WebMvcTest` is faster and more focused.
5. **Use `@MockBean` instead of `@MockitoBean`** — `@MockitoBean` is the Spring Boot 4.0+ replacement for `@MockBean`, using Mockito's inline mock maker directly without proxying.
