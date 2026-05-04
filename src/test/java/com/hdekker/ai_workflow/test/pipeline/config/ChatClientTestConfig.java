package com.hdekker.ai_workflow.test.pipeline.config;

import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;
import com.hdekker.ai_workflow.test.pipeline.mock.MockResponseProvider;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

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
     * Convenience method for creating generic mock.
     */
    public ChatClient createMock(String... responses) {
        return ChatClientMockBuilder.createMock(responses);
    }

    /**
     * Convenience method for creating generic mock with list.
     */
    public ChatClient createMock(java.util.List<String> responses) {
        return ChatClientMockBuilder.createMock(responses, null);
    }

}