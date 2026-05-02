package com.hdekker.ai_workflow.application.agent.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Port interface for persisting and retrieving LLM health status records.
 * <p>
 * The application layer uses this port to persist health check results
 * and query historical status. Infrastructure adapters (JPA, etc.) implement this port.
 */
public interface LLMStatusRepository {

    /**
     * Save an LLM health status record.
     *
     * @param endpoint      the checked endpoint
     * @param configuredModel the expected model
     * @param status        the health status (UP, DOWN, WARN)
     * @param lastChecked   timestamp of the check
     * @param modelCount    number of available models
     * @param modelNames    comma-separated model names
     * @param errorMessage  error message if status is DOWN or WARN
     */
    void save(String endpoint, String configuredModel, String status,
              LocalDateTime lastChecked, int modelCount, String modelNames,
              String errorMessage);

    /**
     * Find the most recent status for an endpoint.
     *
     * @param endpoint the endpoint URL
     * @return the status record if found
     */
    Optional<LLMStatusRecord> findByEndpoint(String endpoint);

    /**
     * List all status records.
     *
     * @return all status records
     */
    List<LLMStatusRecord> findAll();

    /**
     * Delete a status record by endpoint.
     *
     * @param endpoint the endpoint URL
     */
    void deleteByEndpoint(String endpoint);

    /**
     * LLM status record returned by the repository port.
     */
    record LLMStatusRecord(
            String endpoint,
            String configuredModel,
            String status,
            LocalDateTime lastChecked,
            int modelCount,
            String modelNames,
            String errorMessage
    ) {
    }
}
