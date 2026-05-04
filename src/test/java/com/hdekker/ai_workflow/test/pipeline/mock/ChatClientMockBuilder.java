package com.hdekker.ai_workflow.test.pipeline.mock;

import java.time.Duration;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Builder for creating ChatClient mocks tailored to different LLM adapter types.
 * Provides fluent API for configuring mock behavior and responses.
 */
public class ChatClientMockBuilder {
    private static final Logger log = LoggerFactory.getLogger(ChatClientMockBuilder.class);

    public ChatClientMockBuilder() {
        // Public constructor for Spring bean creation
    }

/**
     * Create a generic ChatClient mock with the specified responses.
     * This method replaces the adapter-specific methods (forMapAdapter, forSplitterAdapter, forReducerAdapter).
     * 
     * @param responses List of responses to return from the mock
     * @param promptCaptureList Optional list to capture prompts (can be null)
     * @return Configured ChatClient mock
     */
    public static ChatClient createMock(List<String> responses, List<String> promptCaptureList) {
        MockConfiguration config = MockConfiguration.builder()
            .responses(responses)
            .capturePrompts(promptCaptureList)
            .build();
        return createMock(config);
    }

    /**
     * Create a generic ChatClient mock with the specified responses (varargs version).
     * 
     * @param responses Variable arguments for responses
     * @return Configured ChatClient mock
     */
    public static ChatClient createMock(String... responses) {
        return createMock(List.of(responses), null);
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
    	
        ChatClient mock = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);

        // Instance-based call counter for this mock
        final int[] callCounter = {0};

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
                    return createSuccessFluxForCall(config, callCounter);
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


    private static Flux<String> createSuccessFluxForCall(MockConfiguration config, int[] callCounter) {
        List<String> responses = config.getResponses();
        if (responses.isEmpty()) {
            return Flux.just("");
        }

        // Get the current response based on call counter
        String response;
        if (callCounter[0] >= responses.size()) {
            // Loop back to first response if we've used all responses
            response = responses.get(callCounter[0] % responses.size());
        } else {
            response = responses.get(callCounter[0]);
        }
        
        // Increment counter for next call
        callCounter[0]++;
        
        log.info("Mock LLM call #{}, response: {}", callCounter[0], response.substring(0, Math.min(50, response.length())));
        
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