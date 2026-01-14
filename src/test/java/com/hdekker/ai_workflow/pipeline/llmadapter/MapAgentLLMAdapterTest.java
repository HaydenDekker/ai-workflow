package com.hdekker.ai_workflow.pipeline.llmadapter;

import com.hdekker.ai_workflow.llm.Prompter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import reactor.core.publisher.Flux;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

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

        Prompter mockPrompter = s -> Flux.just(STUB_RESPONSE);
        MapAgentLLMAdapter adapter = new MapAgentLLMAdapter(mockPrompter, def);

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
