# ADR-001: REST Adapters

## Status

Accepted

## Date

2026-04-17

## Context

The application needs to communicate with external LLM endpoints (OpenAI-compatible and Ollama) for health monitoring and model discovery. These endpoints are not Spring beans, use different HTTP libraries, and have distinct API contracts. We need a consistent pattern for integrating external REST services that is testable, configurable, and maintainable.

## Decision

We use a **two-layer adapter pattern** within the `com.hdekker.ai_workflow.llm` package:

1. **REST Client** — handles HTTP communication, request/response mapping, and parsing
2. **Business Adapter** — handles business logic, error handling, and timeout management

### Package Structure

```
com.hdekker.ai_workflow.llm/
├── OpenAiHealthClient.java          # REST client (HTTP layer)
├── OpenAiHealthAdapter.java         # Business adapter (logic layer)
├── OpenAiHealthConfiguration.java   # Spring configuration
├── OpenAiInstanceAdapterUtils.java  # Shared utilities
├── OpenAiInstanceConfiguration.java # Instance configuration
├── OpenAiInstanceConfigurationProperties.java
└── output/
    └── LLMOutputParsingUtils.java   # Output parsing utilities
```

### REST Client

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

### Business Adapter

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

### Testing

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

### Trade-offs

| Aspect | Benefit | Cost |
|--------|---------|------|
| Two-layer pattern | Clear separation: HTTP vs. business logic | More classes per external service |
| Non-singleton clients | Each call can target a different endpoint | No connection pooling across calls |
| `@RestClientTest` | Spring-native, minimal boilerplate | Requires nested `@Configuration` for non-`@Component` beans |

### Alternatives Considered

1. **Inject `RestClient` as a singleton `@Bean`** — Rejected because each endpoint needs its own `baseUrl`. Would require a map of beans or a factory.
2. **Make client a `@Component`** — Rejected because it would be picked up by component scanning, causing Spring to try to instantiate it with no default constructor. The three-constructor ambiguity also prevents Spring from choosing the right one.
3. **Use `@RestClientTest` with `@Import`** — Rejected because `@Import` registers the class as a bean but Spring still can't resolve which constructor to use. A nested `@Configuration` with `@Bean` gives explicit control.
