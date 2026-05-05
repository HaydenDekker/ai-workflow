package com.hdekker.ai_workflow.adapter.outbound.llm;

import java.util.ArrayList;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.prompt.PromptRequest;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

public class SplitterLLMAdapter implements LLMAdapter {

    private static final Logger log = LoggerFactory.getLogger(SplitterLLMAdapter.class);

    private final ChatClient chatClient;
    private final AgentDefinition agentDefinition;

    public SplitterLLMAdapter(ChatClient chatClient, AgentDefinition agentDefinition) {
        this.chatClient = chatClient;
        this.agentDefinition = agentDefinition;
    }

    @Override
    public Flux<PromptResponse> call(Flux<PromptRequest> request) {
        return request.flatMap(fpe -> {
            log.info("Sending prompt to LLM for file: {}", fpe.fileURL());
            return chatClient.prompt(agentDefinition.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + agentDefinition.outputStructure())
                        .stream()
                        .content()
                        .reduce((a, b) -> a + b)
                        .flatMapMany(fullResponse -> parseAndEmitResponses(fpe, fullResponse));
        });
    }

    private Flux<PromptResponse> parseAndEmitResponses(PromptRequest fpe, String fullResponse) {
        List<PromptResponse> responses = new ArrayList<>();

        // Use a simple approach: split the entire response by the --- KEY --- markers
        String[] parts = fullResponse.split("---");

        if (parts.length < 3) {
            log.warn("Not enough parts found for splitting, expected at least 3 parts (intro, key, content)");
            return Flux.fromIterable(responses);
        }

        // Process pairs: parts[1]=key marker, parts[2]=content, parts[3]=next key marker, parts[4]=next content, etc.
        for (int i = 1; i < parts.length - 1; i += 2) {
            String keyMarker = parts[i].trim();
            String content = parts[i + 1].trim();

            // Extract just the key part from the marker (remove any leading/trailing dashes and spaces)
            String key = keyMarker.replaceAll("-+", "").trim();

            if (!content.isEmpty()) {
                String normalizedKey = key.replaceAll("\\s+", "_");
                String modifiedFileName = fpe.fileURL() + "-" + normalizedKey;
                PromptResponse response = new PromptResponse(agentDefinition, modifiedFileName, fpe.file(), content);
                responses.add(response);
            }
        }

        return Flux.fromIterable(responses);
    }
}
