package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

public class MapAgentLLMAdapter implements LLMAdapter {

    private static final Logger log = LoggerFactory.getLogger(MapAgentLLMAdapter.class);

    private final ChatClient chatClient;
    private final AgentDefinition agentDefinition;

    public MapAgentLLMAdapter(ChatClient chatClient, AgentDefinition agentDefinition) {
        this.chatClient = chatClient;
        this.agentDefinition = agentDefinition;
    }

    @Override
    public Flux<PromptResponse> call(Flux<com.hdekker.ai_workflow.domain.prompt.PromptRequest> request) {
        return request.flatMap(fpe -> {
            log.info("Sending prompt to LLM for file: {}", fpe.fileURL());
            return chatClient.prompt(agentDefinition.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + agentDefinition.outputStructure())
                        .stream()
                        .content()
                        .reduce((a, b) -> a + b)
                        .map(s -> new PromptResponse(agentDefinition, fpe.fileURL(), fpe.file(), s));
        });
    }
}
