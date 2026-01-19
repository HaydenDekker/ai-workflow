package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import reactor.core.publisher.Flux;

public class MapAgentLLMAdapter implements LLMAdapter {

    private final ChatClient chatClient;
    private final AgentDefinition agentDefinition;

    public MapAgentLLMAdapter(ChatClient chatClient, AgentDefinition agentDefinition) {
        this.chatClient = chatClient;
        this.agentDefinition = agentDefinition;
    }

    @Override
    public Flux<PromptResponse> call(Flux<com.hdekker.ai_workflow.prompt.PromptRequest> request) {
        return request.flatMap(fpe ->
                chatClient.prompt(agentDefinition.body() + "\n\r" + "`" + "``code" + fpe.file() + "\n\r" + "`" + "``" + "\n\r" + agentDefinition.outputStructure())
                        .stream()
                        .content()
                        .reduce((a, b) -> a + b)
                        .map(s -> new PromptResponse(agentDefinition, fpe.fileURL(), fpe.file(), s))
        );
    }
}
