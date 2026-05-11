package com.hdekker.ai_workflow.adapter.outbound.llm;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;


public class MapAgentLLMAdapterTest {

    private static final String STUB_RESPONSE = "Just a simple test response as if its from the raw output of the LLM \"\n\n```json \n[ \n{ \n";

    @Test
    public void mapAdapterProducesResponseUsingPrompter() {
        // Arrange
        AgentDefinition def = new AgentDefinition(
                ".*\\.txt", // fileInputRegex
                "Test", // title
                "prompt body", // body
                AgentType.MAP, // agentType
                "output structure", // outputStructure
                "out-${title}.txt", // outputFilenameTemplate
                "/tmp/test-dir" // targetDirectory
        );

        ChatClient mockChatClient = ChatClientMockBuilder.createMock(STUB_RESPONSE);

        MapAgentLLMAdapter adapter = new MapAgentLLMAdapter(mockChatClient, def);

        // Act
        Flux<PromptResponse> resultFlux = adapter.call(Flux.just(new PromptRequest("content", "test.txt")));
        var resultList = resultFlux.collectList().block();

        // Assert
        assertThat(resultList).hasSize(1);
        PromptResponse resp = resultList.get(0);
        assertThat(resp.response()).isEqualTo(STUB_RESPONSE);
        assertThat(resp.prompt()).isEqualTo(def);
        assertThat(resp.fileName()).isEqualTo("test.txt");
    }
}
