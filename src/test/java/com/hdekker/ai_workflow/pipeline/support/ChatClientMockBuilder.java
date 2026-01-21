package com.hdekker.ai_workflow.pipeline.support;

import java.time.Duration;
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
        MockConfiguration config = MockConfiguration.builder()
            .responses(List.of(responses))
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock for SplitterLLMAdapter testing.
     * Split adapter parses responses with --- ItemKey --- tokens.
     */
    public static ChatClient forSplitterAdapter(List<String> responses) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(responses)
            .build();
        return createMock(config);
    }

    /**
     * Create a ChatClient mock for ReducerLLMAdapter testing.
     * Reducer adapter maintains state across multiple inputs.
     */
    public static ChatClient forReducerAdapter(List<String> accumulatedResponses) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(accumulatedResponses)
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

    private static ChatClient createMock(MockConfiguration config) {
        // Reset counters for new mock instance
        callCounter = 0;
        currentConfig = config;
        
        ChatClient mock = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

        Mockito.when(mock.prompt(Mockito.anyString())).thenReturn(requestSpec);
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