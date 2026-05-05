package com.hdekker.ai_workflow.adapter.outbound.llm;

import com.hdekker.ai_workflow.config.ObservabilityProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAI health checking components.
 */
@Configuration
public class OpenAiHealthConfiguration {
    
    @Autowired
    private ObservabilityProperties observabilityProperties;
    
    @Bean
    public OpenAiHealthAdapter openAiHealthAdapter() {
        return new OpenAiHealthAdapter(observabilityProperties.getHealthTimeout());
    }
}
