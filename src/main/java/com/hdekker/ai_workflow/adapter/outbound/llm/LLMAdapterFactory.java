package com.hdekker.ai_workflow.adapter.outbound.llm;

import com.hdekker.ai_workflow.application.pipeline.port.LLMAdapter;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class LLMAdapterFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMAdapterFactory.class);

    public static LLMAdapter create(ChatClient chatClient, AgentDefinition agentDefinition) {
        com.hdekker.ai_workflow.domain.agent.AgentType type = agentDefinition.agentType();
        switch (type) {
            case REDUCTION -> {
                log.info("Created LLMReducerAdapter for agent {} (agentType={})", agentDefinition.title(), type);
                return new LLMReducerAdapter(chatClient, agentDefinition);
            }
            case SPLIT -> {
                log.info("Created SplitterLLMAdapter for agent {} (agentType={})", agentDefinition.title(), type);
                return new SplitterLLMAdapter(chatClient, agentDefinition);
            }
            case MAP -> {
                log.info("Created MapAgentLLMAdapter for agent {} (agentType={})", agentDefinition.title(), type);
                return new MapAgentLLMAdapter(chatClient, agentDefinition);
            }
            default -> {
                log.warn("Unknown agentType {} for agent {}, defaulting to MapAgentLLMAdapter",
                        type, agentDefinition.title());
                return new MapAgentLLMAdapter(chatClient, agentDefinition);
            }
        }
    }
}