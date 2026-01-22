package com.hdekker.ai_workflow.test.pipeline.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import reactor.core.publisher.Flux;

/**
 * Builder for creating ChatClient mocks tailored to different LLM adapter types.
 * Provides fluent API for configuring mock behavior and responses.
 */
public class ChatClientMockBuilder {
    private static final Logger log = LoggerFactory.getLogger(ChatClientMockBuilder.class);
    
    // Static counter to track response progression across ChatClient calls
    private static int callCounter = 0;
    private static MockConfiguration currentConfig = null;

    public ChatClientMockBuilder() {
        // Public constructor for Spring bean creation
    }

    /**
     * Create a ChatClient mock for MapAgentLLMAdapter testing.
     * Map adapter provides 1:1 transformation from input to output.
     */
    public static ChatClient forMapAdapter(String... responses) {
        return forMapAdapter(List.of(responses), null);
    }

    public static ChatClient forMapAdapter(List<String> responses, List<String> promptCaptureList) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(responses)
            .capturePrompts(promptCaptureList)
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock for SplitterLLMAdapter testing.
     * Split adapter parses responses with --- ItemKey --- tokens.
     */
    public static ChatClient forSplitterAdapter(List<String> responses) {
        return forSplitterAdapter(responses, null);
    }

    public static ChatClient forSplitterAdapter(List<String> responses, List<String> promptCaptureList) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(responses)
            .capturePrompts(promptCaptureList)
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock for ReducerLLMAdapter testing.
     * Reducer adapter maintains state across multiple inputs.
     */
    public static ChatClient forReducerAdapter(List<String> accumulatedResponses) {
        return forReducerAdapter(accumulatedResponses, null);
    }

    public static ChatClient forReducerAdapter(List<String> accumulatedResponses, List<String> promptCaptureList) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(accumulatedResponses)
            .capturePrompts(promptCaptureList)
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock with error behavior for testing error scenarios.
     */
    public static ChatClient withErrorBehavior(Class<? extends Exception> errorType) {
        MockConfiguration config = MockConfiguration.builder()
            .behavior(MockConfiguration.MockBehavior.ERROR)
            .property("errorType", errorType)
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock with timeout behavior for testing timeout scenarios.
     */
    public static ChatClient withTimeoutBehavior(long timeoutMs) {
        MockConfiguration config = MockConfiguration.builder()
            .behavior(MockConfiguration.MockBehavior.TIMEOUT)
            .property("timeoutMs", timeoutMs)
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock with custom configuration.
     */
    public static ChatClient withConfiguration(MockConfiguration config) {
        return createMock(config);
    }

    /**
     * Create a configurable ChatClient mock for parameterized testing.
     * Allows setting responses dynamically like PromptPipelineTestConfig.
     */
    public static ConfigurableChatClientMock forParameterizedTesting(String defaultResponse) {
        return new ConfigurableChatClientMock(defaultResponse);
    }

    /**
     * Nested class for configurable ChatClient mock with dynamic response management.
     */
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

    private static ChatClient createMock(MockConfiguration config) {
        // Reset counters for new mock instance
        callCounter = 0;
        currentConfig = config;
        
        ChatClient mock = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

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
        Mockito.when(requestSpec.stream()).thenReturn(streamSpec);

        switch (config.getBehavior()) {
            case SUCCESS:
                // Create a flux that advances through responses on each subscription
                Mockito.when(streamSpec.content()).thenAnswer(invocation -> {
                    return createSuccessFluxForCall();
                });
                break;
            case ERROR:
                Class<? extends Exception> errorType = (Class<? extends Exception>) config.getProperties().get("errorType");
                Mockito.when(streamSpec.content()).thenReturn(Flux.<String>error(new RuntimeException("Mock error", createException(errorType))));
                break;
            case TIMEOUT:
                long timeoutMs = (Long) config.getProperties().get("timeoutMs");
                Mockito.when(streamSpec.content()).thenReturn(Flux.<String>never().timeout(Duration.ofMillis(timeoutMs)));
                break;
            case EMPTY_RESPONSE:
                Mockito.when(streamSpec.content()).thenReturn(Flux.<String>empty());
                break;
        }

        return mock;
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

    private static Flux<String> createSuccessFluxForCall() {
        List<String> responses = currentConfig.getResponses();
        if (responses.isEmpty()) {
            return Flux.just("");
        }

        // Get the current response based on call counter
        String response;
        if (callCounter >= responses.size()) {
            // Loop back to first response if we've used all responses
            response = responses.get(callCounter % responses.size());
        } else {
            response = responses.get(callCounter);
        }
        
        // Increment counter for next call
        callCounter++;
        
        log.info("Mock LLM call #{}, response: {}", callCounter, response.substring(0, Math.min(50, response.length())));
        
        return Flux.just(response);
    }

    private static Exception createException(Class<? extends Exception> errorType) {
        try {
            return errorType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return new RuntimeException("Failed to create exception of type " + errorType.getSimpleName());
        }
    }
}