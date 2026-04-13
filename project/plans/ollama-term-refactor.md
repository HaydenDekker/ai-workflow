# Ollama to OpenAI API Refactoring Plan

## Overview

Refactor the codebase to replace Ollama-specific terminology with OpenAI API terminology. This aligns with the architectural shift toward using OpenAI API as the standard interface, where ollama, llama, or vllm hosts will be connected via adapters.

## Naming Recommendation

**Use `OpenAiChatModel`** - This is the correct Spring AI terminology as documented in the Spring AI reference documentation. The Spring AI project uses:
- `OpenAiChatModel` for the chat model implementation
- `OpenAiApi` for the low-level API client
- `OpenAiChatOptions` for configuration options
- `spring.ai.openai.*` for configuration properties

This is consistent across all OpenAI-compatible servers (Ollama, vLLM, etc.) when using the OpenAI API specification.

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

## Implementation Steps

### Phase 1: Preparation
- [ ] Verify Spring AI version compatibility (current: 1.0.3)
- [ ] Confirm `spring-ai-starter-model-openai` artifact exists in same version
- [ ] Create backup branch: `git checkout -b refactor/ollama-to-openai`

### Phase 2: Dependency Update
- [ ] Update `pom.xml` - Replace Ollama starter with OpenAI starter
- [ ] Run `./mvnw clean compile` to verify new dependencies resolve

### Phase 3: Package Renaming
- [ ] Rename `src/main/java/com/hdekker/ai_workflow/ollama/` ? `llm/`
- [ ] Rename `src/test/java/com/hdekker/ai_workflow/ollama/` ? `llm/`
- [ ] Update package declarations in all moved files

### Phase 4: Create Model Listing Utility (NEW)
- [ ] Create `OpenAiModelListUtils.java`:
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

### Phase 5: Source Code Refactoring
- [ ] Update `OllamaInstanceConfiguration.java` ? `OpenAiInstanceConfiguration.java`
  - Update imports from Ollama to OpenAI
  - Update bean names and method names
- [ ] Update `OllamaInstanceAdapterUtils.java` ? `OpenAiInstanceAdapterUtils.java`
  - Replace `OllamaApi` with `OpenAiApi`
  - Replace `OllamaChatModel` with `OpenAiChatModel`
  - Replace `OllamaOptions` with `OpenAiChatOptions`
  - Update `getModel()` to use new `OpenAiModelListUtils.listModels()`
- [ ] Update `OllamaInstanceConfigurationProperties.java` ? `OpenAiInstanceConfigurationProperties.java`
  - Rename `endpoint` field to `baseUrl`
  - Add `api-key` field (optional)
  - Update getters/setters
- [ ] Update `WebClientConfig.java` bean and comment references
- [ ] Update test class `OllamaAdapterTest.java` ? `OpenAiInstanceAdapterTest.java`
  - Update all type references
  - Update test constants and method names

### Phase 6: Configuration Updates
- [ ] Update `application.yml`:
  ```yaml
  app:
    ai:
      base-url: http://192.168.2.108:11434/v1  # Note: /v1 path for OpenAI compatibility
      model: gemma3:27b
      api-key: ${OPENAI_API_KEY:}  # Optional - empty for local servers
  ```
  - Update auto-configuration exclusions
- [ ] Update test profile configurations if needed

### Phase 7: Verification
- [ ] Run `./mvnw clean install` to compile and test
- [ ] Fix any compilation errors
- [ ] Run tests: `./mvnw test -q`
- [ ] Verify application starts with `./mvnw spring-boot:run`
- [ ] Test model listing functionality with actual endpoint

## API Mapping Reference

| Ollama Type | OpenAI Type |
|-------------|-------------|
| `org.springframework.ai.ollama.OllamaChatModel` | `org.springframework.ai.openai.OpenAiChatModel` |
| `org.springframework.ai.ollama.api.OllamaApi` | `org.springframework.ai.openai.api.OpenAiApi` |
| `org.springframework.ai.ollama.api.OllamaOptions` | `org.springframework.ai.openai.OpenAiChatOptions` |
| `OllamaApi.Model` (from listModels) | Custom `ModelData` record (from /v1/models) |

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

```yaml
spring:
  ai:
    openai:
      base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
      api-key: ${OPENAI_API_KEY:}  # Optional for local servers
      chat:
        options:
          model: ${LLM_MODEL:gemma3:27b}

app:
  ai:
    base-url: ${LLM_BASE_URL:http://localhost:11434/v1}
    model: ${LLM_MODEL:gemma3:27b}
    api-key: ${OPENAI_API_KEY:}  # Optional
```

Note: The `/v1` path is typically required for OpenAI-compatible servers.

## Rollback Plan

If issues arise:
1. Revert feature branch to main
2. Investigate and fix issues separately
3. Re-attempt migration with fixes
4. Keep changes isolated to feature branch until verified
