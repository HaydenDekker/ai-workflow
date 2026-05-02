package com.hdekker.ai_workflow.application.agent.port;

import reactor.core.publisher.Mono;

/**
 * Port interface for LLM endpoint health checking.
 * <p>
 * Declares the health check contract the application layer needs from
 * LLM infrastructure adapters. Implementations contact OpenAI-compatible
 * endpoints to verify connectivity and model availability.
 */
public interface LLMHealthPort {

    /**
     * Check the health of an OpenAI-compatible endpoint.
     *
     * @param endpoint        the endpoint URL (e.g., http://localhost:8080)
     * @param configuredModel the expected model name
     * @return a Mono emitting the current health status
     */
    Mono<LLMStatus> checkHealth(String endpoint, String configuredModel);

    /**
     * Health status returned by the LLM health port.
     *
     * @param endpoint         the checked endpoint
     * @param configuredModel  the expected model
     * @param status           the health status (UP, DOWN, or WARN)
     * @param lastChecked      timestamp of the check
     * @param modelCount       number of available models
     * @param modelNames       list of available model names
     * @param errorMessage     error message if status is DOWN or WARN
     */
    record LLMStatus(
            String endpoint,
            String configuredModel,
            HealthStatus status,
            java.time.LocalDateTime lastChecked,
            int modelCount,
            java.util.List<String> modelNames,
            String errorMessage
    ) {
        /**
         * Health status enum.
         */
        public enum HealthStatus {
            UP, DOWN, WARN
        }
    }
}
