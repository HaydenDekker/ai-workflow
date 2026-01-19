package com.hdekker.ai_workflow.pipeline.llmadapter;

import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.pipeline.LLMAdapter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

public class LLMAdapterFactory {

    public static LLMAdapter create(ChatClient chatClient, AgentDefinition agentDefinition) {
        String type = agentDefinition.agentType();
        if ("Reduction".equals(type)) {
            return new LLMReducerAdapter(chatClient, agentDefinition);
        } else if ("Split".equals(type)) {
            return new SplitterLLMAdapter(chatClient, agentDefinition);
        } else {
            return new MapAgentLLMAdapter(chatClient, agentDefinition);
        }
    }
}