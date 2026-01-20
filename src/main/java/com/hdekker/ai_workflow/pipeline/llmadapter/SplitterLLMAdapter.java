package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class SplitterLLMAdapter implements LLMAdapter {

    private final ChatClient chatClient;
    private final AgentDefinition agentDefinition;

    public SplitterLLMAdapter(ChatClient chatClient, AgentDefinition agentDefinition) {
        this.chatClient = chatClient;
        this.agentDefinition = agentDefinition;
    }

    @Override
    public Flux<PromptResponse> call(Flux<com.hdekker.ai_workflow.prompt.PromptRequest> request) {
        return request.flatMap(fpe ->
                chatClient.prompt(agentDefinition.body() + "\n\r" + "```code" + fpe.file() + "\n\r" + "```" + "\n\r" + agentDefinition.outputStructure())
                        .stream()
                        .content()
                        .reduce((a, b) -> a + b)
                        .flatMapMany(fullResponse -> parseAndEmitResponses(fpe, fullResponse))
        );
    }

    private Flux<PromptResponse> parseAndEmitResponses(com.hdekker.ai_workflow.prompt.PromptRequest fpe, String fullResponse) {
        List<PromptResponse> responses = new ArrayList<>();
        
        // Debug: log the input to see what we're working with
        System.out.println("=== SPLITTER DEBUG ===");
        System.out.println("Input length: " + fullResponse.length());
        System.out.println("Input content:\n" + fullResponse);
        
        // Use a simple approach: split the entire response by the --- KEY --- markers
        String[] parts = fullResponse.split("---");
        
        if (parts.length < 3) {
            System.out.println("Not enough parts found, expected at least 3 (empty, key, content)");
            return Flux.fromIterable(responses);
        }
        
        // Process pairs: parts[1]=key marker, parts[2]=content, parts[3]=next key marker, parts[4]=next content, etc.
        for (int i = 1; i < parts.length - 1; i += 2) {
            String keyMarker = parts[i].trim();
            String content = parts[i + 1].trim();
            
            // Extract just the key part from the marker (remove any leading/trailing dashes and spaces)
            String key = keyMarker.replaceAll("-+", "").trim();
            
            System.out.println("Found split - Key: '" + key + "', Content: '" + content + "'");
            
            if (!content.isEmpty()) {
                String normalizedKey = key.replaceAll("\\s+", "_").toUpperCase();
                String modifiedFileName = fpe.fileURL() + "-" + normalizedKey;
                PromptResponse response = new PromptResponse(agentDefinition, modifiedFileName, fpe.file(), content);
                responses.add(response);
                System.out.println("Created response: " + modifiedFileName);
            }
        }
        
        System.out.println("Total responses created: " + responses.size());
        
        return Flux.fromIterable(responses);
    }
}