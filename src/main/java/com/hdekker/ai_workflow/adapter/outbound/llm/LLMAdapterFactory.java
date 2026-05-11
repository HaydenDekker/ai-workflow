package com.hdekker.ai_workflow.adapter.outbound.llm;

import com.hdekker.ai_workflow.application.pipeline.port.LLMAdapter;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class LLMAdapterFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMAdapterFactory.class);

    public static LLMAdapter create(ChatClient chatClient, AgentDefinition agentDefinition) {
        String type = agentDefinition.agentType();
        if (type != null && "Reduction".equals(type)) {
            log.info("Created LLMReducerAdapter for agent {} (agentType={})", agentDefinition.title(), type);
            return new LLMReducerAdapter(chatClient, agentDefinition);
        } else if (type != null && "Split".equals(type)) {
            log.info("Created SplitterLLMAdapter for agent {} (agentType={})", agentDefinition.title(), type);
            return new SplitterLLMAdapter(chatClient, agentDefinition);
        } else {
            log.info("Created MapAgentLLMAdapter for agent {} (agentType={}, defaulting because type did not match 'Reduction' or 'Split')",
                    agentDefinition.title(), type);
            return new MapAgentLLMAdapter(chatClient, agentDefinition);
        }
    }
}