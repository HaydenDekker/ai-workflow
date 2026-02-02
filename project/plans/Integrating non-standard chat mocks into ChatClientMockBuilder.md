# Integrating Non-Standard ChatClient Mocks into ChatClientMockBuilder

## Overview

This document outlines the integration of non-standard ChatClient mock implementations from `PromptPipelineTestConfig` and `LLMReducerAdapterTest` into the unified `ChatClientMockBuilder`. The goal is to eliminate custom mock creation logic and standardize all test ChatClient mocks through the builder pattern.

## Current State Analysis

### ChatClientMockBuilder
- Located: `src/test/java/com/hdekker/ai_workflow/test/pipeline/mock/ChatClientMockBuilder.java`
- Provides static factory methods: `forMapAdapter()`, `forSplitterAdapter()`, `forReducerAdapter()`
- Supports error/timeout behaviors
- Uses `MockConfiguration` for setup
- **Limitations**: No dynamic response configuration, no prompt capturing

### PromptPipelineTestConfig
- Located: `src/test/java/com/hdekker/ai_workflow/pipeline/PromptPipelineTestConfig.java`
- Features:
  - Dynamic response management (`setMockResponses()`, `setMockResponse()`, `resetResponses()`)
  - Prompter call tracking (`setPrompterCalled()`)
  - Default stub response
  - Parameterized testing support
- **Issues**: Manual mock creation, not reusable, marked for removal in project plans

### LLMReducerAdapterTest
- Located: `src/test/java/com/hdekker/ai_workflow/pipeline/llmadapter/LLMReducerAdapterTest.java`
- Features:
  - Manual prompt capturing (stores prompts in `List<String> prompts`)
  - Single stub response
  - TODO comment: "how to bring this into chatClientMockBuilder ???"

## Integration Strategy

### 1. Extend ChatClientMockBuilder for Dynamic Configuration

Add a new nested class `ConfigurableChatClientMock` to support dynamic response management similar to `PromptPipelineTestConfig`:

```java
public class ChatClientMockBuilder {
    // Existing static methods...
    
    /**
     * Create a configurable ChatClient mock for parameterized testing.
     * Allows setting responses dynamically like PromptPipelineTestConfig.
     */
    public static ConfigurableChatClientMock forParameterizedTesting(String defaultResponse) {
        return new ConfigurableChatClientMock(defaultResponse);
    }
    
    public static class ConfigurableChatClientMock {
        private List<String> responses = new ArrayList<>();
        private int currentIndex = 0;
        private boolean prompterCalled = false;
        private List<String> capturedPrompts = new ArrayList<>();
        
        public ConfigurableChatClientMock(String defaultResponse) {
            responses.add(defaultResponse);
        }
        
        public void setMockResponses(List<String> responses) {
            this.responses = new ArrayList<>(responses);
            reset();
        }
        
        public void setMockResponses(String[] responses) {
            setMockResponses(Arrays.asList(responses));
        }
        
        public void setMockResponse(String response) {
            setMockResponses(List.of(response));
        }
        
        public void reset() {
            currentIndex = 0;
            prompterCalled = false;
            capturedPrompts.clear();
        }
        
        public boolean wasPrompterCalled() {
            return prompterCalled;
        }
        
        public List<String> getCapturedPrompts() {
            return new ArrayList<>(capturedPrompts);
        }
        
        public ChatClient build() {
            return createConfigurableMock(this);
        }
    }
    
    private static ChatClient createConfigurableMock(ConfigurableChatClientMock config) {
        ChatClient mock = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

        Mockito.when(mock.prompt(Mockito.anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            config.capturedPrompts.add(prompt);
            config.prompterCalled = true;
            return requestSpec;
        });
        Mockito.when(requestSpec.stream()).thenReturn(streamSpec);
        
        Mockito.when(streamSpec.content()).thenAnswer(invocation -> {
            if (config.responses.isEmpty()) {
                return Flux.just("");
            }
            String response = config.responses.get(config.currentIndex % config.responses.size());
            config.currentIndex++;
            config.prompterCalled = true;
            return Flux.just(response);
        });

        return mock;
    }
}
```

### 2. Add Prompt Capturing to Standard Builder Methods

Extend existing static methods to optionally capture prompts by adding overloaded versions:

```java
public static ChatClient forMapAdapter(String... responses) {
    return forMapAdapter(Arrays.asList(responses), null);
}

public static ChatClient forMapAdapter(List<String> responses, List<String> promptCaptureList) {
    MockConfiguration config = MockConfiguration.builder()
        .responses(responses)
        .property("capturePrompts", promptCaptureList != null)
        .property("promptList", promptCaptureList)
        .build();
    return createMock(config);
}

// Similar overloads for forSplitterAdapter and forReducerAdapter...
```

Update `MockConfiguration` to support prompt capturing:

```java
public static class Builder {
    // Existing fields...
    private boolean capturePrompts = false;
    private List<String> promptCaptureList = null;

    public Builder capturePrompts(List<String> promptList) {
        this.capturePrompts = true;
        this.promptCaptureList = promptList;
        return this;
    }
    
    // Update build() to include these in properties...
}
```

### 3. Update Mock Creation Logic

Modify the `createMock` method in `ChatClientMockBuilder` to handle prompt capturing:

```java
private static ChatClient createMock(MockConfiguration config) {
    // ... existing setup ...
    
    if ((Boolean) config.getProperties().getOrDefault("capturePrompts", false)) {
        Mockito.when(mock.prompt(Mockito.anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            @SuppressWarnings("unchecked")
            List<String> promptList = (List<String>) config.getProperties().get("promptList");
            if (promptList != null) {
                promptList.add(prompt);
            }
            return requestSpec;
        });
    } else {
        Mockito.when(mock.prompt(Mockito.anyString())).thenReturn(requestSpec);
    }
    
    // ... rest of setup remains the same ...
}
```

### 4. Refactor LLMReducerAdapterTest

Replace manual mock setup with builder-based implementation:

```java
@Test
public void reducerAdapterPreservesAndUsesLatestResponse() {
    // Arrange
    AgentDefinition def = new AgentDefinition(
            ".*\\.txt", // fileInputRegex
            "Test", // title
            "prompt body", // body
            null, // agentType
            "output structure", // outputStructure
            "out-${title}.txt" // outputFilenameTemplate
    );

    List<String> capturedPrompts = new ArrayList<>();
    ChatClient mockChatClient = ChatClientMockBuilder.forReducerAdapter(
        List.of(STUB_RESPONSE), 
        capturedPrompts  // Enable prompt capturing
    );

    LLMReducerAdapter adapter = new LLMReducerAdapter(mockChatClient, def);

    Flux<PromptRequest> reqFlux = Flux.just(
            new PromptRequest("content1", "file1.txt"),
            new PromptRequest("content2", "file2.txt")
    );

    // Act
    var result = adapter.call(reqFlux).collectList().block();

    // Assert
    assertThat(result).hasSize(2);
    var resp1 = result.get(0);
    var resp2 = result.get(1);

    // responses should be the stub response
    assertThat(resp1.response()).isEqualTo(STUB_RESPONSE);
    assertThat(resp2.response()).isEqualTo(STUB_RESPONSE);

    // file names are preserved
    assertThat(resp1.fileName()).isEqualTo("file1.txt");
    assertThat(resp2.fileName()).isEqualTo("file2.txt");

    // prompts collected
    assertThat(capturedPrompts).hasSize(2);
    // first prompt should contain the original content but NOT the snapshot header
    assertThat(capturedPrompts.get(0)).contains("content1");
    assertThat(capturedPrompts.get(0)).doesNotContain("Current Snapshot:");
    // second prompt should contain the snapshot of the first response
    assertThat(capturedPrompts.get(1)).contains("Current Snapshot:");
    assertThat(capturedPrompts.get(1)).contains(STUB_RESPONSE);
    // also should contain the second file content
    assertThat(capturedPrompts.get(1)).contains("content2");
}
```

### 5. Deprecate PromptPipelineTestConfig

Since `PromptPipelineTestConfig` is marked for removal in project plans and appears to have no active usage, mark it as deprecated and provide migration guidance:

```java
@Deprecated
@TestConfiguration
public class PromptPipelineTestConfig {
    // ... existing code ...
    
    /**
     * @deprecated Use {@link ChatClientMockBuilder.ConfigurableChatClientMock} instead
     */
    // ... methods marked as deprecated ...
}
```

### 6. Migration Guide for Tests Using PromptPipelineTestConfig

For any tests currently using `PromptPipelineTestConfig`, provide this migration pattern:

```java
// Old approach with PromptPipelineTestConfig
@Autowired
PromptPipelineTestConfig config;

@BeforeEach
void setup() {
    config.setMockResponses(responses);
}

// New approach with ChatClientMockBuilder
ChatClientMockBuilder.ConfigurableChatClientMock mockBuilder;

@BeforeEach
void setup() {
    mockBuilder = ChatClientMockBuilder.forParameterizedTesting(defaultResponse);
    mockBuilder.setMockResponses(responses);
    ChatClient chatClient = mockBuilder.build();
    // Use chatClient in test
}
```

## Implementation Steps

### Phase 1: Core Builder Extensions
1. Add `ConfigurableChatClientMock` nested class to `ChatClientMockBuilder`
2. Implement `createConfigurableMock()` method
3. Add prompt capturing support to `MockConfiguration`
4. Update `createMock()` to handle prompt capturing

### Phase 2: Test Refactoring
1. Refactor `LLMReducerAdapterTest` to use builder with prompt capturing
2. Deprecate `PromptPipelineTestConfig`
3. Run tests to ensure compatibility: `./mvnw test -q -Dtest=LLMReducerAdapterTest`

### Phase 3: Cleanup
1. Remove `PromptPipelineTestConfig` usage from any remaining tests
2. Update documentation to reflect new capabilities
3. Consider removing deprecated class in future release

## Benefits

1. **Consistency**: All ChatClient mocks follow the same builder pattern
2. **Reusability**: Dynamic configuration supports complex test scenarios
3. **Maintainability**: Centralized mock logic reduces code duplication
4. **Testability**: Prompt capturing enables verification of adapter prompt construction
5. **Extensibility**: Builder pattern allows easy addition of new mock behaviors

## Testing Strategy

- Unit tests for new `ConfigurableChatClientMock` functionality
- Integration tests to verify prompt capturing works correctly
- Regression tests to ensure existing builder methods remain unaffected
- End-to-end tests for refactored test classes

## Risk Assessment

- **Low Risk**: Changes are additive, existing APIs remain unchanged
- **Medium Risk**: Prompt capturing may affect performance in high-volume tests
- **Low Risk**: Deprecation provides clear migration path

## Success Criteria

- All ChatClient mocks use `ChatClientMockBuilder`
- `LLMReducerAdapterTest` successfully captures prompts using builder
- No regression in existing test functionality
- `PromptPipelineTestConfig` marked as deprecated with clear migration guide</content>
<parameter name="filePath">plans/Integrating non-standard chat mocks into ChatClientMockBuilder.md