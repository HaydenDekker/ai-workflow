# ADR-010: llama.cpp

## Context

This application needs to connect to local LLM models hosted via llama.cpp. Rather than using a cloud API, the goal is to run models locally for privacy, cost, and offline capabilities. llama.cpp exposes an OpenAI-compatible REST API, which allows Spring AI's OpenAI abstraction to work without any llama.cpp-specific dependencies.

Key requirements:
- Connect to llama.cpp server running locally
- Support both chat and embedding models (potentially on different ports)
- Handle slow local model inference with appropriate timeouts
- Implement retry logic for server busy scenarios (503 errors)
- Support streaming responses
- Use GGUF format models

## Decision

### Architecture Overview

The application connects to llama.cpp (or any OpenAI-compatible LLM server) using Spring AI's OpenAI abstraction. This works because llama.cpp exposes an OpenAI-compatible REST API endpoint.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │          OpenAiChatConfig (Configuration Layer)             │   │
│  │                                                               │   │
│  │  @Bean public OpenAiApi chatApi() {                         │   │
│  │      return OpenAiApi.builder()                              │   │
│  │          .baseUrl("http://localhost:8080")                   │   │
│  │          .apiKey(new SimpleApiKey("not-needed"))             │   │
│  │          .completionsPath("/v1/chat/completions")            │   │
│  │          .build();                                           │   │
│  │  }                                                            │   │
│  │                                                               │   │
│  │  @Bean public ChatModel chatModel(OpenAiApi api) {           │   │
│  │      return OpenAiChatModel.builder()                         │   │
│  │          .openAiApi(api)                                      │   │
│  │          .defaultOptions(OpenAiChatOptions.builder()          │   │
│  │              .model("gemma3:4b-it-q8_0")                      │   │
│  │              .build())                                        │   │
│  │          .build();                                            │   │
│  │  }                                                            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                        │                                              │
│                        ▼                                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              ChatClient (Higher-Level Abstraction)          │   │
│  │  @Bean public ChatClient chatClient(ChatModel chatModel)    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  │ HTTP POST /v1/chat/completions
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    llama.cpp Server                                  │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  llama.cpp (built with server support)                        │  │
│  │  Command: ./llama-server -m model.gguf -p prompt -n 512       │  │
│  │  Or: docker run -p 8080:8080 ghcr.io/ggerganov/llama.cpp:server│  │
│  │                                                                 │  │
│  │  Exposes OpenAI-compatible endpoints:                         │  │
│  │  - POST /v1/chat/completions (chat models)                    │  │
│  │  - POST /v1/embeddings (embedding models)                     │  │
│  │  - GET /v1/models (list available models)                     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  Model: gemma3:4b-it-q8_0 (GGUF format)                              │
│  Port: 8080                                                           │
└─────────────────────────────────────────────────────────────────────┘
```

### Dependency Declarations

The llama.cpp integration requires **only** the Spring AI OpenAI starter - no llama.cpp-specific dependencies needed:

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>2.0.0-M3</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring AI OpenAI Starter - Works with ANY OpenAI-compatible API -->
    <!-- This includes llama.cpp, Ollama, vLLM, LocalAI, etc. -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>

    <!-- Optional: Spring AI Qdrant for vector storage -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
    </dependency>

    <!-- Optional: Spring AI Ollama if you also want native Ollama support -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Key Point:** The `spring-ai-starter-model-openai` dependency works with **any** OpenAI-compatible API endpoint, including:
- llama.cpp server
- Ollama
- vLLM
- LocalAI
- OpenRouter
- Any self-hosted LLM server with OpenAI-compatible API

### llama.cpp Server Setup

#### Option 1: Docker (Recommended)

```bash
# Pull and run llama.cpp server
docker run -d -p 8080:8080 \
  -v /path/to/models:/models \
  ghcr.io/ggerganov/llama.cpp:server \
  --model /models/gemma3:4b-it-q8_0.gguf \
  --host 0.0.0.0 \
  --port 8080 \
  --n-predict 512

# For embedding models (separate instance)
docker run -d -p 3300:8080 \
  -v /path/to/models:/models \
  ghcr.io/ggerganov/llama.cpp:server \
  --model /models/qwen3-embedding.gguf \
  --host 0.0.0.0 \
  --port 8080 \
  --embedding
```

#### Option 2: Binary

```bash
# Build llama.cpp
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp
make

# Run chat model
./llama-server -m models/gemma3:4b-it-q8_0.gguf \
  --host 0.0.0.0 \
  --port 8080 \
  --n-predict 512

# Run embedding model
./llama-server -m models/qwen3-embedding.gguf \
  --host 0.0.0.0 \
  --port 3300 \
  --embedding
```

#### Option 3: Download Pre-built

```bash
# Download from releases (Windows)
wget https://github.com/ggerganov/llama.cpp/releases/download/b3571/server.exe
# Or use brew on macOS
brew install llama.cpp
```

### Configuration Properties

#### application.properties

```properties
# ============================================================
# CHAT MODEL CONFIGURATION (llama.cpp)
# ============================================================

# Base URL of llama.cpp server
spring.ai.openai.chat.base-url=http://localhost:8080/v1

# API key (llama.cpp doesn't require one by default)
spring.ai.openai.chat.api-key=not-needed

# Model name (must match the model loaded in llama.cpp)
spring.ai.openai.chat.options.model=gemma3:4b-it-q8_0

# Timeout for slow local models (local models can take 1-5 minutes)
spring.ai.openai.chat.options.timeout=300s

# ============================================================
# EMBEDDING MODEL CONFIGURATION (Optional - separate llama.cpp instance)
# ============================================================

# Separate llama.cpp instance for embeddings (different port)
spring.ai.openai.embedding.base-url=http://localhost:3300/v1
spring.ai.openai.embedding.api-key=not-needed
spring.ai.openai.embedding.options.model=qwen3-embedding

# ============================================================
# RETRY CONFIGURATION
# ============================================================

# Retry on server errors (llama.cpp may return 503 when busy)
spring.ai.retry.maxAttempts=3
spring.ai.retry.backoff-delay=5s

# ============================================================
# LOGGING (for debugging)
# ============================================================

logging.level.org.springframework.ai=DEBUG
logging.level.org.springframework.retry=DEBUG
```

### Custom Configuration with Timeout and Retry

For production use with local models, create a custom configuration bean:

```java
package com.hdekker.qdrant.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

@Configuration
public class LlamaCppChatConfig {

    /**
     * Custom OpenAiApi bean configured for llama.cpp with:
     * - Extended timeouts (local models are slow)
     * - Retry logic (handles 503 Service Unavailable)
     * - Virtual threads (Java 21+) for better concurrency
     */
    @Bean
    public OpenAiApi chatApi(RestClient.Builder restClientBuilder, 
                             WebClient.Builder webClientBuilder) {
        
        // Configure HTTP client with extended timeout
        java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
                .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
                .build();
        
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(2)); // Local models can be slow!

        // Configure RestClient with retry logic
        RestClient.Builder customRestClientBuilder = restClientBuilder
                .clone()
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    int retryCount = 0;
                    int maxRetries = 3;
                    ClientHttpResponse response = null;

                    while (retryCount < maxRetries) {
                        response = execution.execute(request, body);
                        if (response.getStatusCode().is5xxServerError()) {
                            // Retry on 5xx errors (llama.cpp returns 503 when busy)
                            retryCount++;
                        } else {
                            return response;
                        }
                    }
                    return response;
                });

        // Configure WebClient for streaming with response timeout
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(2));

        WebClient.Builder customWebClientBuilder = webClientBuilder
                .clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        
        return OpenAiApi.builder()
                .baseUrl("http://localhost:8080")
                .apiKey(new SimpleApiKey("not-needed"))
                .restClientBuilder(customRestClientBuilder)
                .webClientBuilder(customWebClientBuilder)
                .completionsPath("/v1/chat/completions")
                .build();
    }

    /**
     * Primary ChatModel bean for llama.cpp
     */
    @Bean
    @Primary
    public ChatModel chatModel(@Qualifier("chatApi") OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gemma3:4b-it-q8_0")
                        // Pass llama.cpp specific parameters
                        .extraBody(Map.of(
                            "chat_template_kwargs", Map.of("enable_thinking", false),
                            "temperature", 0.7,
                            "top_p", 0.9,
                            "n", 1
                        ))
                        .build())
                .build();
    }

    /**
     * High-level ChatClient for dependency injection
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
```

### Usage Example

```java
package com.hdekker.qdrant.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlamaCppService {
    
    private final ChatClient chatClient;
    
    public LlamaCppService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    public String generateResponse(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
    
    public String streamResponse(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }
}
```

### Testing Strategy

Three tiers of testing — from fastest (no LLM) to slowest (real llama.cpp):

#### Tier 1: Unit Tests — `@ExtendWith(MockitoExtension.class)`

Test business logic with a mocked `ChatClient`. No Spring context, no HTTP, no LLM required.

```java
@ExtendWith(MockitoExtension.class)
class LlamaCppServiceTest {
    
    @Mock
    private ChatClient chatClient;
    
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    
    @InjectMocks
    private LlamaCppService service;
    
    @Test
    void generateResponse_delegatesToChatClient() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("Hello")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(new CallResponse("Hi there"));
        
        String result = service.generateResponse("Hello");
        
        assertThat(result).isEqualTo("Hi there");
    }
}
```

#### Tier 2: Configuration Tests — `@SpringBootTest(classes = TestConfig.class)`

Test that the `LlamaCppChatConfig` beans wire correctly **without** starting a full application context.

```java
@SpringBootTest(classes = LlamaCppChatConfigTest.TestConfig.class)
class LlamaCppChatConfigTest {
    
    @Autowired
    private ChatClient chatClient;
    
    @Test
    void chatClient_isNonNull() {
        assertThat(chatClient).isNotNull();
    }
    
    @Configuration
    static class TestConfig {
        // Minimal config: provide only the beans needed
        @Bean
        public OpenAiApi chatApi() {
            return OpenAiApi.builder()
                    .baseUrl("http://localhost:8080")
                    .apiKey(new SimpleApiKey("not-needed"))
                    .completionsPath("/v1/chat/completions")
                    .build();
        }
        
        @Bean
        public ChatModel chatModel(OpenAiApi api) {
            return OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model("gemma3:4b-it-q8_0")
                            .build())
                    .build();
        }
        
        @Bean
        public ChatClient chatClient(ChatModel chatModel) {
            return ChatClient.builder(chatModel).build();
        }
    }
}
```

> **Why not `@SpringBootTest` without `classes=`?**
> Loading the full context starts `AgentConfiguration`, which persists YAML agents to the database. Always use `classes =` to load only the beans you need. See [DPR: Testing Strategy](../docs/dpr-testing-strategy.md) for the `@SpringBootTest` isolation rule.

#### Tier 3: Integration Tests — `@Tag("integration")` + Real llama.cpp

Tests that require a real llama.cpp server. Tagged with `@Tag("integration")` so they can be skipped in CI when no LLM is available.

```java
@Tag("integration")
@SpringBootTest(classes = LlamaCppIntegrationTest.TestConfig.class)
class LlamaCppIntegrationTest {
    
    @Autowired
    private ChatClient chatClient;
    
    @Test
    void testLlamaCppConnection() {
        String response = chatClient.prompt()
                .user("Say hello")
                .call()
                .content();
        
        assertThat(response).isNotBlank();
    }
    
    @Configuration
    static class TestConfig {
        @Bean
        public OpenAiApi chatApi() {
            return OpenAiApi.builder()
                    .baseUrl("http://localhost:8080")
                    .apiKey(new SimpleApiKey("not-needed"))
                    .completionsPath("/v1/chat/completions")
                    .build();
        }
        
        @Bean
        public ChatModel chatModel(OpenAiApi api) {
            return OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model("gemma3:4b-it-q8_0")
                            .build())
                    .build();
        }
        
        @Bean
        public ChatClient chatClient(ChatModel chatModel) {
            return ChatClient.builder(chatModel).build();
        }
    }
}
```

Run integration tests selectively:
```bash
./mvnw test                              # Unit + config tests only (no llama.cpp needed)
./mvnw verify -Dit.test=LlamaCppIntegrationTest  # Integration tests (requires llama.cpp)
```

### Verifying Connection

Test your llama.cpp connection (requires a running server):

```bash
# Check if server is running
curl http://localhost:8080/models

# Test chat endpoint
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma3:4b-it-q8_0",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### GGUF Model Formats

llama.cpp uses GGUF format models. Common sources:

```bash
# Download from HuggingFace
# Format: organization/model-name:variant
huggingface-cli download TheBloke/gemma-7B-it-GGUF \
  --include "gemma-7b-it-Q4_K_M.gguf" \
  --local-dir ./models

# Popular GGUF repositories:
# - TheBloke (quantized models)
# - bartowski (optimized models)
# - MaziyarPanahi (clean models)
```

### Performance Considerations

| Factor | Recommendation |
|--------|----------------|
| **Model Size** | 4GB-8GB for consumer GPUs (4-bit quantized) |
| **Context Window** | 2048-4096 tokens (adjust via `-c` flag) |
| **GPU Layers** | `-ngl 35` for full GPU offloading |
| **Batch Size** | 512 for balance of speed/memory |
| **Threads** | Match physical CPU cores |

Example optimized llama.cpp command:

```bash
./llama-server -m models/gemma3:4b-it-q8_0.gguf \
  --host 0.0.0.0 \
  --port 8080 \
  -c 4096 \
  -b 512 \
  -ngl 35 \
  -t 8 \
  --n-predict 512
```

## Consequences

### Benefits

1. **No Vendor Lock-in**: Works with any OpenAI-compatible API (llama.cpp, Ollama, vLLM, LocalAI)
2. **Privacy**: Models run locally, no data sent to external services
3. **Cost**: No API costs for local inference
4. **Offline**: Works without internet connection
5. **Flexibility**: Easy to switch between different model providers

### Trade-offs

1. **Hardware Requirements**: Local inference requires adequate CPU/GPU/RAM
2. **Slower Inference**: Local models are slower than cloud APIs
3. **Model Management**: Must manually download and manage GGUF models
4. **Server Management**: Must keep llama.cpp server running

### Important Notes

1. **Separate Instances**: For production, run separate llama.cpp instances for chat and embeddings
2. **API Key**: Can be any value if llama.cpp is not configured with authentication
3. **Model Names**: Must match exactly what llama.cpp reports via `/v1/models`
4. **Timeouts**: Local models can take 1-5 minutes for complex prompts

## How to Configure for Another Project

### Step 1: Add Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Configure application.properties

```properties
spring.ai.openai.chat.base-url=http://localhost:8080/v1
spring.ai.openai.chat.api-key=not-needed
spring.ai.openai.chat.options.model=your-model-name
spring.ai.openai.chat.options.timeout=300s
```

### Step 3: Create Configuration Class

```java
@Configuration
public class LlamaCppConfig {
    
    @Bean
    public OpenAiApi chatApi() {
        return OpenAiApi.builder()
                .baseUrl("http://localhost:8080")
                .apiKey(new SimpleApiKey("not-needed"))
                .completionsPath("/v1/chat/completions")
                .build();
    }
    
    @Bean
    public ChatModel chatModel(OpenAiApi api) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("your-model-name")
                        .build())
                .build();
    }
}
```

### Step 4: Start llama.cpp Server

```bash
docker run -d -p 8080:8080 \
  -v /path/to/models:/models \
  ghcr.io/ggerganov/llama.cpp:server \
  --model /models/your-model.gguf \
  --host 0.0.0.0 \
  --port 8080
```

## References

- Spring AI OpenAI: https://docs.spring.io/spring-ai/reference/api/chat/openai.html
- llama.cpp Server: https://github.com/ggerganov/llama.cpp/tree/master/examples/server
- GGUF Model Format: https://github.com/ggerganov/ggml/blob/master/docs/gguf.md
- HuggingFace GGUF Models: https://huggingface.co/models?library=gguf
- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/