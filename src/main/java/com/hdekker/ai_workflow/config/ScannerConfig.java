package com.hdekker.ai_workflow.config;

import com.hdekker.ai_workflow.files.EmissionDelayConfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the AI scanner subsystem.
 */
@Configuration
public class ScannerConfig {

    @Bean
    @ConfigurationProperties(prefix = "ai-scanner")
    public EmissionDelayConfig emissionDelayConfig() {
        return new EmissionDelayConfig();
    }
}
