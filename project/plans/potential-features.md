# Potential Features & Enhancements

This document outlines standalone features and enhancements identified during the Ollama to OpenAI API refactoring that can be implemented as future updates.

---

## Dynamic Model Discovery

### Overview

Spring AI's `OpenAiApi` does not expose a `listModels()` method. Implement a custom utility to call the `/v1/models` endpoint directly, providing model discovery functionality across all OpenAI-compatible backends.

### Use Cases

- Dynamic model selection in UI
- Validation of configured model names against available models
- Model metadata display (size, capabilities, etc.)
- Multi-backend model aggregation

### Implementation Details

Create `src/main/java/com/hdekker/ai_workflow/llm/OpenAiModelListUtils.java`:

```java
package com.hdekker.ai_workflow.llm;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

import java.util.List;

public class OpenAiModelListUtils {
    
    public static List<String> listModels(OpenAiApi api, String baseUrl, String apiKey) {
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
        
        ModelListResponse response = restClient.get()
            .uri("/v1/models")
            .retrieve()
            .body(ModelListResponse.class);
        
        return response.data().stream()
            .map(ModelData::id)
            .toList();
    }
    
    record ModelListResponse(String object, List<ModelData> data) {}
    record ModelData(String id, String object, long created, String ownedBy) {}
}
```

### Requirements

- [ ] Handle optional API key (don't add header if empty)
- [ ] Implement proper error handling and logging
- [ ] Add comprehensive exception handling
- [ ] Configure JSON deserialization
- [ ] Add unit tests with mocked HTTP responses
- [ ] Add integration test with real endpoint

### Decision Points

1. **Model listing endpoint path**: Should the implementation use:
   - `/v1/models` (standard OpenAI path)
   - `/models` (Ollama path)
   - Configurable path parameter

2. **Error handling**: If `/v1/models` fails:
   - Fail fast and throw exception
   - Return empty list and log warning
   - Fall back to configured model only

---

## Production-Ready LLM Configuration

### Overview

Implement production-ready configuration to handle slow local models, server busy states, and backend-specific parameters. Based on ADR documentation (`adr-chat-model-setup-for-llama-cpp.md`).

### Use Cases

- Large models requiring extended inference time (5+ minutes)
- Handling server busy states (503 Service Unavailable)
- Backend-specific parameters (llama.cpp `chat_template_kwargs`)
- Separate chat and embedding model endpoints

### Implementation Details

Create `src/main/java/com/hdekker/ai_workflow/llm/OpenAiChatConfig.java`:

```java
package com.hdekker.ai_workflow.llm;

import java.time.Duration;
import java.util.Map;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiChatConfig {

    /**
     * Custom OpenAiApi configured for local LLM servers with:
     * - Extended timeouts (local models are slow)
     * - Retry logic (handles 503 Service Unavailable)
     * - Virtual threads (Java 21+) for better concurrency
     */
    @Bean
    @Primary
    public OpenAiApi chatApi(RestClient.Builder restClientBuilder) {
        
        // Configure HTTP client with extended timeout
        java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
                .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
                .build();
        
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(2));

        return OpenAiApi.builder()
                .baseUrl("http://localhost:11434")
                .apiKey(new SimpleApiKey("not-needed"))
                .restClientBuilder(restClientBuilder)
                .completionsPath("/v1/chat/completions")
                .build();
    }

    /**
     * Primary ChatModel bean with production-ready settings
     */
    @Bean
    @Primary
    public OpenAiChatModel chatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gemma3:27b")
                        .timeout(Duration.ofMinutes(5))
                        // Pass llama.cpp specific parameters if needed
                        .extraBody(Map.of(
                            "chat_template_kwargs", Map.of("enable_thinking", false),
                            "temperature", 0.7,
                            "top_p", 0.9
                        ))
                        .build())
                .build();
    }
}
```

### Key Features

- [ ] **Extended Timeouts**: Local models (especially llama.cpp) can take 1-5 minutes for inference
- [ ] **Retry Logic**: Handle 503 Service Unavailable errors when server is busy
- [ ] **Virtual Threads**: Java 21+ virtual threads for better concurrency with slow operations
- [ ] **Backend-Specific Parameters**: Support `chat_template_kwargs` and other llama.cpp options via `extraBody`
- [ ] **Separate Chat/Embedding Instances**: Support different ports for chat and embedding models

### Configuration Properties

Add to `application.yml`:

```yaml
spring:
  ai:
    openai:
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
      api-key: ${OPENAI_API_KEY:not-needed}
      chat:
        options:
          model: ${LLM_MODEL:gemma3:27b}
          timeout: 300s
          temperature: 0.7
          top-p: 0.9
      retry:
        max-attempts: 3
        backoff-delay: 5s

# For embedding models on separate port
      embedding:
        base-url: ${LLM_EMBEDDING_URL:http://localhost:3300/v1}
        api-key: not-needed
        options:
          model: qwen3-embedding
```

### Requirements

- [ ] Implement retry logic with exponential backoff
- [ ] Add virtual thread executor configuration
- [ ] Support separate chat and embedding model endpoints
- [ ] Add configuration properties for timeout, retry, and backend-specific params
- [ ] Document timeout recommendations for different model sizes
- [ ] Add integration tests for retry behavior

---

## Environment Variable Configuration Enhancements

### Overview

Add production-ready timeout and retry configuration to `application.yml` with environment variable overrides for deployment flexibility.

### Use Cases

- Deployment across different environments (dev, staging, production)
- Runtime configuration without code changes
- Centralized configuration management
- Different timeout settings for different model sizes

### Current Configuration

```yaml
spring:
  ai:
    openai:
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
      api-key: ${OPENAI_API_KEY:not-needed}
      chat:
        options:
          model: ${LLM_MODEL:gemma3:27b}

app:
  ai:
    endpoint: ${LLM_BASE_URL:http://localhost:11434/v1}
    model: ${LLM_MODEL:gemma3:27b}
```

### Enhanced Configuration

```yaml
spring:
  ai:
    openai:
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
      api-key: ${OPENAI_API_KEY:not-needed}
      chat:
        options:
          model: ${LLM_MODEL:gemma3:27b}
          timeout: ${LLM_TIMEOUT:300s}  # Extended timeout for slow models
      retry:
        max-attempts: ${LLM_RETRY_ATTEMPTS:3}
        backoff-delay: ${LLM_RETRY_BACKOFF:5s}

app:
  ai:
    base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
    model: ${LLM_MODEL:gemma3:27b}
    api-key: ${OPENAI_API_KEY:}  # Optional
```

### Requirements

- [ ] Add timeout configuration (default 300s for local models)
- [ ] Add retry configuration (max attempts, backoff delay)
- [ ] Document environment variable overrides
- [ ] Add validation for configuration values
- [ ] Update documentation with new properties

---

## Implementation Priority

1. **High**: Production-Ready LLM Configuration (handles real-world usage)
2. **Medium**: Dynamic Model Discovery (user convenience)
3. **Low**: Environment Variable Configuration Enhancements (incremental improvement)

## Dependencies

- **Production-Ready LLM Configuration**: Can be implemented independently
- **Dynamic Model Discovery**: Can benefit from error handling in Production-Ready config
- **Environment Variable Configuration**: Can be implemented anytime, configuration-only change

## References

- ADR: `adr-chat-model-setup-for-llama-cpp.md`
- Original Plan: `ollama-term-refactor.md`
- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/api/openai.html
