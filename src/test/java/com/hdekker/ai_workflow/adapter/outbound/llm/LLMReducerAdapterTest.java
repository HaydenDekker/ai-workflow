package com.hdekker.ai_workflow.adapter.outbound.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Test for {@link LLMReducerAdapter}. It verifies two main behaviors:
 *   1. The adapter produces a {@link PromptResponse} for each request.
 *   2. The second request includes the response of the first request in the prompt
 *      (i.e. the "latestResponse" snapshot is used).
 */
public class LLMReducerAdapterTest {

    private static final String STUB_RESPONSE = "Just a simple test response as if its from the raw output of the LLM \"\n\n```json \n[ \n{ \n";

    @Test
    public void reducerAdapterPreservesAndUsesLatestResponse() {
        // Arrange
        AgentDefinition def = new AgentDefinition(
                ".*\\.txt", // fileInputRegex
                "Test", // title
                "prompt body", // body
                null, // agentType
                "output structure", // outputStructure
                "out-${title}.txt", // outputFilenameTemplate
                "/tmp/test-dir" // targetDirectory
        );

        List<String> prompts = new ArrayList<>();
        ChatClient mockChatClient = ChatClientMockBuilder.createMock(
            List.of(STUB_RESPONSE),
            prompts  // Enable prompt capturing
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
        assertThat(prompts).hasSize(2);
        // first prompt should contain the original content but NOT the snapshot header
        assertThat(prompts.get(0)).contains("content1");
        assertThat(prompts.get(0)).doesNotContain("Current Snapshot:");
        // second prompt should contain the snapshot of the first response
        assertThat(prompts.get(1)).contains("Current Snapshot:");
        assertThat(prompts.get(1)).contains(STUB_RESPONSE);
        // also should contain the second file content
        assertThat(prompts.get(1)).contains("content2");
    }
}
