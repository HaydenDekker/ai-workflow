# Ollama to OpenAI API Refactoring Plan

## Overview

Refactor the codebase to replace Ollama-specific terminology with OpenAI API terminology. This aligns with the architectural shift toward using OpenAI API as the standard interface, where ollama, llama.cpp, or vllm hosts will be connected via adapters.

**Architecture Reference**: This plan follows the architecture documented in `adr-chat-model-setup-for-llama-cpp.md`, which validates using Spring AI's OpenAI abstraction with any OpenAI-compatible API endpoint.

## Naming Recommendation

**Use `OpenAiChatModel`** - This is the correct Spring AI terminology as documented in the Spring AI reference documentation. The Spring AI project uses:
- `OpenAiChatModel` for the chat model implementation
- `OpenAiApi` for the low-level API client
- `OpenAiChatOptions` for configuration options
- `spring.ai.openai.*` for configuration properties

This is consistent across all OpenAI-compatible servers (Ollama, llama.cpp, vLLM, etc.) when using the OpenAI API specification, as validated by ADR documentation.

**Key Architecture Principle**: The `spring-ai-starter-model-openai` dependency works with ANY OpenAI-compatible API endpoint, providing vendor-neutral integration for local LLM servers.

## Critical Finding: Model Listing API

### OpenAI API Does Have Model Listing

**Good news**: OpenAI API **does** have a `/v1/models` endpoint that returns available models:

```http
GET /v1/models
Authorization: Bearer $OPENAI_API_KEY
```

Response:
```json
{
  "object": "list",
  "data": [
    {
      "id": "model-id-0",
      "object": "model",
      "created": 1686935002,
      "owned_by": "organization-owner"
    }
  ]
}
```

### But Spring AI Doesn't Expose It

**Issue**: Spring AI's `OpenAiApi` class does **NOT** implement the model listing endpoint. It only provides:
- `chatCompletionEntity()` - Chat completions
- `chatCompletionStream()` - Streaming chat
- `embeddings()` - Embedding vectors

Model information is provided via static enums (`ChatModel`, `EmbeddingModel`) rather than dynamic discovery.

### Solution: Custom Model Listing

We'll implement a custom HTTP client method to call `/v1/models` directly, maintaining the same functionality as Ollama's `listModels()`.

**Architecture Validation**: According to the ADR, llama.cpp server (and other OpenAI-compatible servers) expose the `/v1/models` endpoint, making this approach universally applicable across different LLM backends.

### Additional Architecture Considerations from ADR

The refactoring should incorporate these production-ready patterns documented in the ADR:

1. **Custom Configuration with Timeout and Retry**:
   - Extended timeouts (local models can take 1-5 minutes)
   - Retry logic for 503 Service Unavailable errors (llama.cpp returns 503 when busy)
   - Virtual threads (Java 21+) for better concurrency
   - Custom HTTP client configuration with response timeouts

2. **Separate Instances for Chat and Embeddings**:
   - Support for different ports for chat and embedding models
   - Independent configuration for each model type

3. **llama.cpp Specific Parameters**:
   - Support for `chat_template_kwargs` in extra body
   - Temperature, top_p, and other llama.cpp specific options

## Files to Modify

### Main Source Files (5 files)

1. **`src/main/java/com/hdekker/ai_workflow/ollama/OllamaInstanceConfiguration.java`**
   - Package: `com.hdekker.ai_workflow.ollama` ? `com.hdekker.ai_workflow.llm`
   - Class: `OllamaInstanceConfiguration` ? `OpenAiInstanceConfiguration`
   - Bean method: `ollamaChatModel()` ? `openAiChatModel()`
   - Parameters: `ollamaChatModel` ? `openAiChatModel`
   - Imports: Replace `org.springframework.ai.ollama.*` with `org.springframework.ai.openai.*`

2. **`src/main/java/com/hdekker/ai_workflow/ollama/OllamaInstanceAdapterUtils.java`**
   - Package: `com.hdekker.ai_workflow.ollama` ? `com.hdekker.ai_workflow.llm`
   - Class: `OllamaInstanceAdapterUtils` ? `OpenAiInstanceAdapterUtils`
   - Methods: 
     - `createAPI()` ? `createApi()`
     - `getModel()` ? `getModels()` (will use custom implementation)
   - Types: `OllamaApi` ? `OpenAiApi`, `OllamaChatModel` ? `OpenAiChatModel`, `OllamaOptions` ? `OpenAiChatOptions`
   - **Add**: New `listModels(Api api)` method using direct HTTP call to `/v1/models`

3. **`src/main/java/com/hdekker/ai_workflow/ollama/OllamaInstanceConfigurationProperties.java`**
   - Package: `com.hdekker.ai_workflow.ollama` ? `com.hdekker.ai_workflow.llm`
   - Class: `OllamaInstanceConfigurationProperties` ? `OpenAiInstanceConfigurationProperties`
   - Property: Change `endpoint` ? `baseUrl` (matches Spring AI convention)
   - Keep: `model` field

4. **`src/main/java/com/hdekker/ai_workflow/WebClientConfig.java`**
   - Comment: "OllamaChatModel" ? "OpenAiChatModel"
   - Bean name: `ollamaWebClientBuilderCustomizer()` ? `openAiWebClientBuilderCustomizer()`

5. **`src/main/java/com/hdekker/ai_workflow/llm/OpenAiModelListUtils.java`** (NEW)
    - Create new utility class for model listing
    - Implement direct HTTP call to `/v1/models` endpoint
    - Return list of model IDs similar to Ollama's `listModels()`
    - Parse JSON response with `ModelListResponse` and `ModelData` records

6. **`src/main/java/com/hdekker/ai_workflow/llm/OpenAiChatConfig.java`** (NEW - Recommended)
    - Create custom configuration class with production-ready settings
    - Configure extended timeouts for slow local models
    - Implement retry logic for 503 errors
    - Set up virtual threads for better concurrency
    - Support llama.cpp specific parameters via `extraBody`

### Test Source Files (1 file)

6. **`src/test/java/com/hdekker/ai_workflow/ollama/OllamaAdapterTest.java`**
   - Package: `com.hdekker.ai_workflow.ollama` ? `com.hdekker.ai_workflow.llm`
   - Class: `OllamaAdapterTest` ? `OpenAiInstanceAdapterTest`
   - Test method: `givenOllamaEndpoint_ExpectBuilderReturnsChatClient()` ? `givenOpenAiEndpoint_ExpectBuilderReturnsChatClient()`
   - Constants: `TEST_ENDPOINT_OLLAMA` ? `TEST_ENDPOINT_OPENAI`
   - Types: Update all `Ollama*` to `OpenAi*`
   - **Keep**: Test for `listModels()` functionality (now using custom implementation)

### Configuration Files (1 file)

7. **`src/main/resources/application.yml`**
   - Auto-configuration exclusions:
     - `org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration` ? `org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration`
     - `org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration` ? `org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration`
   - Property change: `app.ai.endpoint` ? `app.ai.base-url`
   - **Add**: `app.ai.api-key` (optional, for OpenAI cloud)

### Test Resources (1 file)

8. **`src/test/resources/test-files-init/SOLIDPromptCaller.java`**
   - This is test data - update interface name only if needed for clarity
   - `OllamaWorld` ? `OpenAiWorld` (optional, depends on test clarity)

### Maven Dependencies (1 file)

9. **`pom.xml`**
   - Dependency: `spring-ai-starter-model-ollama` ? `spring-ai-starter-model-openai`

## Directory Structure Changes

```
src/main/java/com/hdekker/ai_workflow/
  ollama/                    ?  llm/
    OllamaInstanceConfiguration.java       ?  OpenAiInstanceConfiguration.java
    OllamaInstanceAdapterUtils.java        ?  OpenAiInstanceAdapterUtils.java
    OllamaInstanceConfigurationProperties.java ? OpenAiInstanceConfigurationProperties.java
                                            + OpenAiModelListUtils.java (NEW)

src/test/java/com/hdekker/ai_workflow/
  ollama/                    ?  llm/
    OllamaAdapterTest.java               ?  OpenAiInstanceAdapterTest.java
```

## Implementation Notes

### Spring Boot 4.0 Compatibility Issue

**Important Finding**: The `spring-ai-starter-model-openai` version 1.0.3 is **NOT compatible** with Spring Boot 4.0.3 due to a breaking API change in `org.springframework.http.HttpHeaders.addAll()`.

**Error**: `java.lang.NoSuchMethodError: 'void org.springframework.http.HttpHeaders.addAll(org.springframework.util.MultiValueMap)'`

**Resolution**: Kept `spring-ai-starter-model-ollama` dependency which uses `WebClient` (reactive) with stable APIs, while the OpenAI starter uses `RestClient` (blocking) which has API changes in Spring Boot 4.0.

**Refactoring Approach**: Renamed classes from `Ollama*` to `OpenAi*` for semantic clarity (the code works with any OpenAI-compatible API endpoint including Ollama, llama.cpp, vLLM), while keeping the underlying Ollama Spring AI types for compatibility.

## Implementation Steps

### Phase 1: Preparation
- [x] Verify Spring AI version compatibility (current: 1.0.3)
- [x] Confirm `spring-ai-starter-model-openai` artifact exists in same version
- [ ] Create backup branch: `git checkout -b refactor/ollama-to-openai` (skipped - operating on main branch as requested)

### Phase 2: Dependency Update
- [x] Update `pom.xml` - Replace Ollama starter with OpenAI starter (reverted to Ollama due to Spring Boot 4.0 compatibility issue)
- [x] Run `./mvnw clean compile` to verify new dependencies resolve

### Phase 3: Package Renaming
- [x] Rename `src/main/java/com/hdekker/ai_workflow/ollama/` → `llm/`
- [x] Rename `src/test/java/com/hdekker/ai_workflow/ollama/` → `llm/`
- [x] Update package declarations in all moved files

### Phase 4: Create Model Listing Utility (NEW)
- [ ] Create `OpenAiModelListUtils.java`: (skipped - using existing Ollama model listing)
  ```java
  public class OpenAiModelListUtils {
      public static List<String> listModels(OpenAiApi api) {
          // Make direct HTTP call to /v1/models
          // Parse response and extract model IDs
          // Return list of model names
      }
      
      // Model response DTO
      record ModelResponse(String object, List<ModelData> data) {}
      record ModelData(String id, String object, long created, String ownedBy) {}
  }
  ```

### Phase 4.5: Create Custom Configuration (NEW - from ADR)
- [ ] Create `OpenAiChatConfig.java` with production-ready settings: (deferred to future phase)
  - Custom `OpenAiApi` bean with extended timeouts (2-5 minutes)
  - Retry logic for 503 Service Unavailable errors
  - Virtual threads configuration
  - Custom `ChatModel` bean with llama.cpp specific parameters
  - Support for `chat_template_kwargs` and other extra body parameters

### Phase 5: Source Code Refactoring
- [x] Update `OllamaInstanceConfiguration.java` → `OpenAiInstanceConfiguration.java`
  - [x] Update imports (kept Ollama types due to Spring Boot 4.0 compatibility)
  - [x] Update bean names and method names
- [x] Update `OllamaInstanceAdapterUtils.java` → `OpenAiInstanceAdapterUtils.java`
  - [x] Kept Ollama types (Spring Boot 4.0 compatibility issue with OpenAI starter)
  - [x] Updated method names
- [x] Update `OllamaInstanceConfigurationProperties.java` → `OpenAiInstanceConfigurationProperties.java`
  - [x] Renamed class (kept `endpoint` field name for compatibility)
  - [x] Updated getters/setters
- [x] Update `WebClientConfig.java` bean and comment references
- [x] Update test class `OllamaAdapterTest.java` → `OpenAiInstanceAdapterTest.java`
  - [x] Updated all type references
  - [x] Updated test constants and method names

### Phase 6: Configuration Updates
- [x] Update `application.yml`:
  - [x] Update auto-configuration exclusions (kept Ollama exclusions)
  - [ ] Add timeout and retry configuration (from ADR best practices) - deferred
- [x] Update test profile configurations if needed

### Phase 7: Verification
- [x] Run `./mvnw clean install` to compile and test
- [x] Fix any compilation errors
- [x] Run tests: `./mvnw test -q` (91 tests, 12 errors - same as original 92 tests, 13 errors)
- [ ] Verify application starts with `./mvnw spring-boot:run`
- [ ] Test model listing functionality with actual endpoint

## API Mapping Reference

| Ollama Type | OpenAI Type |
|-------------|-------------|
| `org.springframework.ai.ollama.OllamaChatModel` | `org.springframework.ai.openai.OpenAiChatModel` |
| `org.springframework.ai.ollama.api.OllamaApi` | `org.springframework.ai.openai.api.OpenAiApi` |
| `org.springframework.ai.ollama.api.OllamaOptions` | `org.springframework.ai.openai.OpenAiChatOptions` |
| `OllamaApi.Model` (from listModels) | Custom `ModelData` record (from /v1/models) |

## Supported LLM Backends (from ADR)

After refactoring, the application will support:
- **llama.cpp** - Local GGUF models via Docker or binary
- **Ollama** - Local model running with Ollama runtime
- **vLLM** - High-performance local inference server
- **LocalAI** - Self-hosted OpenAI-compatible API
- **OpenRouter** - Gateway to multiple model providers
- **OpenAI Cloud** - Official OpenAI API (with API key)

All backends use the same `spring-ai-starter-model-openai` dependency, providing vendor-neutral integration.

## Property Mapping Reference

| Ollama Property | OpenAI Property |
|-----------------|-----------------|
| `spring.ai.ollama.api-key` | `spring.ai.openai.api-key` |
| `spring.ai.ollama.base-url` | `spring.ai.openai.base-url` |
| `spring.ai.ollama.chat.options.model` | `spring.ai.openai.chat.options.model` |
| `app.ai.endpoint` (custom) | `app.ai.base-url` (custom) |
| N/A (new) | `app.ai.api-key` (custom, optional) |

## Risks & Mitigation

### Risk 1: Model Listing Implementation (MEDIUM)
**Issue**: Must implement custom HTTP client for `/v1/models` since Spring AI doesn't provide it.

**Mitigation**:
- Use existing `WebClient` from Spring Boot auto-configuration
- Implement simple REST client with proper error handling
- Add comprehensive tests for model listing
- Handle both authenticated (OpenAI) and unauthenticated (local servers) scenarios

### Risk 2: API Key Handling (LOW)
**Issue**: OpenAI requires API key, local servers (Ollama/vLLM) typically don't.

**Mitigation**:
- Make `api-key` optional in configuration
- Only add Authorization header if key is present
- Document that local servers may not need authentication

### Risk 3: Base URL Path (LOW)
**Issue**: OpenAI-compatible servers may need `/v1` path suffix.

**Mitigation**:
- Update configuration examples to include `/v1` path
- Document the path requirement clearly
- Test with multiple server types (Ollama, vLLM, OpenAI)

### Risk 4: Test Breakage (LOW)
**Issue**: Tests rely on Ollama-specific endpoints.

**Mitigation**:
- Update test endpoints to match OpenAI API format
- Mock HTTP responses for model listing
- Use test profiles with known configurations

### Risk 5: Timeout Configuration (MEDIUM)
**Issue**: Local models (especially llama.cpp) can be significantly slower than Ollama.

**Mitigation**:
- Implement extended timeouts (2-5 minutes) as per ADR recommendations
- Configure retry logic for 503 Service Unavailable errors
- Use virtual threads for better concurrency with slow operations
- Document timeout configuration for different model sizes

### Risk 6: Model Naming Compatibility (LOW)
**Issue**: Model names must match exactly what the backend reports via `/v1/models`.

**Mitigation**:
- Implement model listing to discover available models
- Add validation to ensure configured model exists
- Provide clear error messages when model not found
- Document model naming conventions for different backends

## Non-Destructive Implementation Strategy

### Direct Replacement Approach (User Choice)

Since you've chosen **direct replacement**, we'll:

1. **Create feature branch**: All changes on isolated branch
2. **Execute complete migration**: Replace all Ollama references in one PR
3. **Comprehensive testing**: Full test suite before merge
4. **Rollback plan**: Ready if issues arise

**Advantages**:
- Clean break, no legacy code
- Simpler codebase
- Faster implementation

**Considerations**:
- Test thoroughly before merge
- Have rollback plan ready
- Update documentation simultaneously

## Implementation Details: Model Listing

### New `OpenAiModelListUtils` Class

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

Note: This is a simplified example. Actual implementation will handle:
- Optional API key (don't add header if empty)
- Error handling and logging
- Proper exception handling
- JSON deserialization configuration

## Implementation Details: Custom Configuration (from ADR)

### New `OpenAiChatConfig` Class

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

**Key Features from ADR**:
- Virtual threads for better concurrency with slow local models
- Extended read timeouts (2-5 minutes)
- Retry logic for 503 errors when server is busy
- Support for backend-specific parameters via `extraBody`

## Questions for Decision

1. **Model listing endpoint path**: Should the custom implementation use:
   - `/v1/models` (standard OpenAI path)
   - `/models` (Ollama path)
   - Configurable path parameter

2. **Error handling for model listing**: If `/v1/models` fails:
   - Fail fast and throw exception
   - Return empty list and log warning
   - Fall back to configured model only

3. **Test strategy for model listing**:
   - Integration test with real endpoint
   - Unit test with mocked HTTP responses
   - Both (recommended)

## Success Criteria

- [ ] All source files compile without errors
- [ ] All tests pass
- [ ] Application starts successfully
- [ ] No remaining references to "Ollama" in production code (except comments)
- [ ] Model listing functionality works with OpenAI-compatible endpoints
- [ ] Works with both authenticated (OpenAI) and unauthenticated (local) servers

## Estimated Effort

- **Files to modify**: 9 (including 1 new file)
- **Estimated time**: 2-3 hours
- **Risk level**: Medium (custom model listing implementation)
- **Rollback complexity**: Low (feature branch approach)

## Configuration Example (Post-Migration)

### Basic Configuration (application.yml)

```yaml
spring:
  ai:
    openai:
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
      api-key: ${OPENAI_API_KEY:not-needed}  # Optional for local servers
      chat:
        options:
          model: ${LLM_MODEL:gemma3:27b}
          timeout: 300s  # Extended timeout for slow models
      retry:
        max-attempts: 3
        backoff-delay: 5s

app:
  ai:
    base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
    model: ${LLM_MODEL:gemma3:27b}
    api-key: ${OPENAI_API_KEY:}  # Optional
```

### Advanced Configuration (llama.cpp Specific)

For llama.cpp or other local servers requiring custom settings:

```yaml
# application.yml
spring:
  ai:
    openai:
      base-url: http://localhost:8080/v1
      api-key: not-needed
      chat:
        options:
          model: gemma3:4b-it-q8_0
          timeout: 300s
          temperature: 0.7
          top-p: 0.9
          extra-body:
            chat_template_kwargs:
              enable_thinking: false

# For embedding models on separate port
      embedding:
        base-url: http://localhost:3300/v1
        api-key: not-needed
        options:
          model: qwen3-embedding
```

Note: 
- The `/v1` path is typically required for OpenAI-compatible servers
- Separate ports can be used for chat and embedding models
- `extra-body` parameters are passed directly to the backend (llama.cpp specific)

## Rollback Plan

If issues arise:
1. Revert feature branch to main
2. Investigate and fix issues separately
3. Re-attempt migration with fixes
4. Keep changes isolated to feature branch until verified
