package com.hdekker.ai_workflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.test.pipeline.config.ChatClientTestConfig;
import com.hdekker.ai_workflow.test.pipeline.mock.MockConfiguration;

/**
 * Test demonstrating the new builder pattern for ChatClient mocking.
 * This replaces the profile-based approach with dynamic configuration.
 */
@SpringBootTest
@Import(ChatClientTestConfig.class)
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class BuilderPatternIntegrationTest {

    @Autowired
    private ChatClientTestConfig testConfig;

    @Test
    public void testMapAdapterWithBuilder() {
        // Create a ChatClient mock using the builder pattern
        ChatClient mockClient = testConfig.createMock("custom map response");

        assertThat(mockClient).isNotNull();
        // Verify the mock can be used in tests
    }

    @Test
    public void testCustomConfiguration() {
        MockConfiguration config = MockConfiguration.builder()
            .responses(List.of("response1", "response2"))
            .behavior(MockConfiguration.MockBehavior.SUCCESS)
            .build();

        ChatClient mockClient = testConfig.createChatClient(config);

        assertThat(mockClient).isNotNull();
    }
}