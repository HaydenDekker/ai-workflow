package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptRequest;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

import reactor.core.publisher.Flux;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

public class SplitterLLMAdapterTest {

    private static final String STUB_RESPONSE = "--- SOLID_PRINCIPLES ---\nViolations in SOLID principles here.\n--- DEPENDENCY_INJECTION ---\nSuggestions for dependency injection.";

    @Test
    public void splitterAdapterProducesMultipleResponsesUsingPrompter() {
        // Arrange
        AgentDefinition def = new AgentDefinition(
                ".*\\.java", // fileInputRegex
                "SOLID_NON_COMPLIANCE", // title
                "prompt body", // body
                "Split", // agentType
                "output structure", // outputStructure
                "out-${name}.md", // outputFilenameTemplate
                "/tmp/test-dir" // targetDirectory
        );

        ChatClient mockChatClient = ChatClientMockBuilder.createMock(List.of(STUB_RESPONSE), null);

        SplitterLLMAdapter adapter = new SplitterLLMAdapter(mockChatClient, def);

        // Act
        Flux<PromptResponse> resultFlux = adapter.call(Flux.just(new PromptRequest("content", "TestClass.java")));
        var resultList = resultFlux.collectList().block();

        // Assert
        assertThat(resultList).hasSize(2);
        PromptResponse resp1 = resultList.get(0);
        assertThat(resp1.response()).isEqualTo("Violations in SOLID principles here.");
        assertThat(resp1.prompt()).isEqualTo(def);
        assertThat(resp1.fileName()).isEqualTo("TestClass.java-SOLID_PRINCIPLES");
        assertThat(resp1.fileContents()).isEqualTo("content");

        PromptResponse resp2 = resultList.get(1);
        assertThat(resp2.response()).isEqualTo("Suggestions for dependency injection.");
        assertThat(resp2.prompt()).isEqualTo(def);
        assertThat(resp2.fileName()).isEqualTo("TestClass.java-DEPENDENCY_INJECTION");
        assertThat(resp2.fileContents()).isEqualTo("content");
    }
}
