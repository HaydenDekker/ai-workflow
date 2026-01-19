package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Pattern pattern = Pattern.compile("---\\s*(?<key>[^\\n]+)\\s*---", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(fullResponse);
        List<PromptResponse> responses = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        positions.add(0);

        while (matcher.find()) {
            positions.add(matcher.start());
            positions.add(matcher.end());
            keys.add(matcher.group("key").trim());
        }
        positions.add(fullResponse.length());

        for (int i = 0; i < keys.size(); i++) {
            int start = positions.get(2 * i + 2);
            int end = positions.get(2 * i + 3);
            String content = fullResponse.substring(start, end).trim();
            if (!content.isEmpty()) {
                String key = keys.get(i).replaceAll("\\s+", "_").toUpperCase();
                String modifiedFileName = fpe.fileURL() + "-" + key;
                responses.add(new PromptResponse(agentDefinition, modifiedFileName, fpe.file(), content));
            }
        }

        return Flux.fromIterable(responses);
    }
}