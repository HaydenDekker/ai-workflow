package com.hdekker.ai_workflow.llm;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;

import reactor.core.publisher.Mono;

/**
 * Adapter for checking OpenAI-compatible endpoint health.
 * Uses listModels() API which does NOT consume tokens or affect context.
 * 
 * Health check strategy:
 * - listModels() verifies connectivity AND that models are loaded
 * - No prompts are sent, so no tokens consumed
 * - No conversation context is affected
 */
public class OpenAiHealthAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAiHealthAdapter.class);
    
    private final int timeoutMs;
    
    /**
     * Create OpenAiHealthAdapter with configurable timeout.
     * 
     * @param timeoutMs Timeout in milliseconds for health check operations
     */
    public OpenAiHealthAdapter(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
    
    /**
     * Health check using listModels - does NOT consume tokens or affect context.
     * Returns status with model information.
     * 
     * @param endpoint OpenAI-compatible endpoint URL (e.g., http://localhost:8080)
     * @param configuredModel Expected model name
     * @return Mono emitting LLMStatus with current health state
     */
    public Mono<LLMStatus> checkHealth(String endpoint, String configuredModel) {
        log.debug("Starting health check for endpoint: {}", endpoint);
        
        OpenAiHealthClient client = new OpenAiHealthClient(endpoint, timeoutMs);
        
        return client.listModels()
            .map(modelNames -> {
                log.debug("Health check OK for {}: {} models available", endpoint, modelNames.size());
                
                return new LLMStatus(
                    endpoint,
                    configuredModel,
                    AdapterStatus.UP,
                    LocalDateTime.now(),
                    modelNames.size(),
                    modelNames,
                    null
                );
            })
            .onErrorResume(e -> {
                log.warn("Health check FAILED for {}: {}", endpoint, e.getMessage());
                
                return Mono.just(new LLMStatus(
                    endpoint,
                    configuredModel,
                    AdapterStatus.DOWN,
                    LocalDateTime.now(),
                    0,
                    List.of(),
                    e.getMessage()
                ));
            })
            .timeout(Duration.ofMillis(timeoutMs))
            .onErrorResume(timeoutEx -> {
                log.warn("Health check TIMEOUT for {} after {}ms", endpoint, timeoutMs);
                
                return Mono.just(new LLMStatus(
                    endpoint,
                    configuredModel,
                    AdapterStatus.DOWN,
                    LocalDateTime.now(),
                    0,
                    List.of(),
                    "Timeout after " + timeoutMs + "ms"
                ));
            });
    }
}
