package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import reactor.core.publisher.Flux;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class MapAgentLLMAdapterTest {

    private static final String STUB_RESPONSE = "Just a simple test response as if its from the raw output of the LLM \"\n\n```json \n[ \n{ \n";

    @Test
    public void mapAdapterProducesResponseUsingPrompter() {
        // Arrange
        AgentDefinition def = new AgentDefinition(
                ".*\\.txt", // fileInputRegex
                "Test", // title
                "prompt body", // body
                null, // agentType
                "output structure", // outputStructure
                "out-${title}.txt" // outputFilenameTemplate
        );

        ChatClient mockChatClient = mock(ChatClient.class);
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);

        when(mockChatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just(STUB_RESPONSE));

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
