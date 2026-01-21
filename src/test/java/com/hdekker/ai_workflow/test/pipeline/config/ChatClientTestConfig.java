package com.hdekker.ai_workflow.test.pipeline.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;
import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;

/**
 * Test configuration for dynamic ChatClient mock creation.
 * Provides prototype-scoped beans for flexible test-specific mock configuration.
 */
@Configuration
public class ChatClientTestConfig {

    /**
     * Prototype-scoped ChatClientMockBuilder for creating test-specific mocks.
     */
    @Bean
    @Scope("prototype")
    public ChatClientMockBuilder chatClientMockBuilder() {
        return new ChatClientMockBuilder();
    }

    /**
     * Prototype-scoped MockResponseProvider for accessing mock response data.
     */
    @Bean
    @Scope("prototype")
    public MockResponseProvider mockResponseProvider() {
        return new MockResponseProvider();
    }

    /**
     * Factory method for creating ChatClient mocks with specific configurations.
     * This allows tests to create mocks dynamically without Spring context limitations.
     */
    public ChatClient createChatClient(MockConfiguration config) {
        return ChatClientMockBuilder.withConfiguration(config);
    }

    /**
     * Convenience method for creating Map adapter mock.
     */
    public ChatClient createMapAdapterMock(String... responses) {
        return ChatClientMockBuilder.forMapAdapter(responses);
    }

    /**
     * Convenience method for creating Splitter adapter mock.
     */
    public ChatClient createSplitterAdapterMock(java.util.List<String> responses) {
        return ChatClientMockBuilder.forSplitterAdapter(responses);
    }

    /**
     * Convenience method for creating Reducer adapter mock.
     */
    public ChatClient createReducerAdapterMock(java.util.List<String> accumulatedResponses) {
        return ChatClientMockBuilder.forReducerAdapter(accumulatedResponses);
    }
}