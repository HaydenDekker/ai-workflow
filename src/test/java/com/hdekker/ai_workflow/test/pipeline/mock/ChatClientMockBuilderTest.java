package com.hdekker.ai_workflow.test.pipeline.mock;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Unit tests for ChatClientMockBuilder.
 */
public class ChatClientMockBuilderTest {

    @Test
    public void testCreateMock() {
        ChatClient mock = ChatClientMockBuilder.createMock("test response");

        assertThat(mock).isNotNull();
        // Additional assertions can be added for mock behavior
    }

    @Test
    public void testWithConfiguration() {
        MockConfiguration config = MockConfiguration.builder()
            .response("custom response")
            .build();

        ChatClient mock = ChatClientMockBuilder.withConfiguration(config);

        assertThat(mock).isNotNull();
    }
}