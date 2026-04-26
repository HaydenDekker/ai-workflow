package com.hdekker.ai_workflow.usecases;

import com.hdekker.ai_workflow.database.llmstatus.LLMStatusEntity;
import com.hdekker.ai_workflow.database.llmstatus.LLMStatusRepository;
import com.hdekker.ai_workflow.llm.OpenAiHealthAdapter;
import com.hdekker.ai_workflow.observability.ObservabilityProperties;
import com.hdekker.ai_workflow.rest.dto.AdapterStatus;
import com.hdekker.ai_workflow.rest.dto.LLMStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case for managing LLM endpoint health status.
 *
 * Responsibilities:
 * - Poll configured endpoints on schedule
 * - Persist status to database
 * - Check WARN condition (stale data)
 * - Log warnings for DOWN/WARN states
 */
@Service
public class AgentStatusUsecase {

    private static final Logger log = LoggerFactory.getLogger(AgentStatusUsecase.class);

    private final LLMStatusRepository repository;
    private final OpenAiHealthAdapter healthAdapter;
    private final ObservabilityProperties observabilityProperties;

    @Value("${app.observability.warn-after-hours:1}")
    private long warnAfterHours;

    public AgentStatusUsecase(LLMStatusRepository repository,
                              OpenAiHealthAdapter healthAdapter,
                              ObservabilityProperties observabilityProperties) {
        this.repository = repository;
        this.healthAdapter = healthAdapter;
        this.observabilityProperties = observabilityProperties;
    }

    /**
     * Scheduled polling - runs at configured interval.
     * Default: 60000ms (1 minute)
     *
     * Polls all configured LLM endpoints and persists results.
     * Logs warnings for DOWN/WARN states.
     */
    @Scheduled(fixedRateString = "${app.observability.polling-interval:60000}")
    public void schedulePolling() {
        log.debug("Starting scheduled LLM health check...");

        String endpoint = observabilityProperties.getEndpoint();
        String model = observabilityProperties.getModel();

        if (endpoint == null || endpoint.isEmpty()) {
            log.warn("No observability endpoint configured - skipping health check");
            return;
        }

        LLMStatus status = healthAdapter.checkHealth(endpoint, model)
                .block();

        if (status == null) {
            log.error("Health check returned null status for endpoint: {}", endpoint);
            return;
        }

        LLMStatus finalStatus = checkWarnCondition(status);
        persistStatus(finalStatus);

        if (finalStatus.status() == AdapterStatus.DOWN) {
            log.warn("LLM endpoint DOWN: {} - {}",
                    finalStatus.endpoint(),
                    finalStatus.errorMessage());
        } else if (finalStatus.status() == AdapterStatus.WARN) {
            log.warn("LLM endpoint WARN: {} - No response for {}+ hours",
                    finalStatus.endpoint(),
                    warnAfterHours);
        } else {
            log.debug("LLM endpoint OK: {} - {} models",
                    finalStatus.endpoint(),
                    finalStatus.modelCount());
        }
    }

    /**
     * Check if status should be WARN (stale data).
     * If last successful check was more than warnAfterHours ago, set WARN.
     */
    private LLMStatus checkWarnCondition(LLMStatus status) {
        if (status.status() == AdapterStatus.UP) {
            String endpoint = status.endpoint();
            final LLMStatus[] result = {status};
            
            repository.findByEndpoint(endpoint).ifPresent(previous -> {
                if (AdapterStatus.UP.name().equals(previous.getStatus())) {
                    LocalDateTime lastUp = previous.getLastChecked();
                    if (lastUp != null) {
                        long hoursSince = Duration.between(lastUp, LocalDateTime.now()).toHours();
                        if (hoursSince >= warnAfterHours) {
                            log.debug("Endpoint {} marked WARN - {} hours since last UP",
                                    endpoint, hoursSince);
                            result[0] = new LLMStatus(
                                    endpoint,
                                    status.configuredModel(),
                                    AdapterStatus.WARN,
                                    LocalDateTime.now(),
                                    status.modelCount(),
                                    status.modelNames(),
                                    "No response for " + hoursSince + " hours"
                            );
                        }
                    }
                }
            });
            
            return result[0];
        }
        return status;
    }

    /**
     * Persist status to database.
     */
    private void persistStatus(LLMStatus status) {
        LLMStatusEntity entity = new LLMStatusEntity(
                status.endpoint(),
                status.configuredModel(),
                status.status().name(),
                status.lastChecked(),
                status.modelCount(),
                status.modelNames() != null ? String.join(",", status.modelNames()) : "",
                status.errorMessage()
        );
        repository.save(entity);
    }

    /**
     * Get current status for the configured endpoint from database.
     * Only returns the endpoint currently set in app.observability.endpoint.
     * Stale entries for other endpoints are removed.
     */
    public List<LLMStatus> getCurrentStatus() {
        String configuredEndpoint = observabilityProperties.getEndpoint();
        if (configuredEndpoint == null || configuredEndpoint.isEmpty()) {
            return List.of();
        }

        // Remove any stale entries not matching the configured endpoint
        List<LLMStatusEntity> allEntities = repository.findAll();
        List<LLMStatusEntity> stale = allEntities.stream()
                .filter(e -> !e.getEndpoint().equals(configuredEndpoint))
                .toList();
        stale.forEach(repository::delete);

        // Return only the configured endpoint
        return repository.findByEndpoint(configuredEndpoint)
                .map(List::of)
                .orElse(List.of())
                .stream()
                .map(this::entityToDto)
                .toList();
    }

    /**
     * Manually trigger polling (for immediate refresh).
     */
    public List<LLMStatus> triggerPoll() {
        log.info("Manual LLM health check triggered");
        List<LLMStatus> statuses = new ArrayList<>();

        String endpoint = observabilityProperties.getEndpoint();
        String model = observabilityProperties.getModel();

        if (endpoint == null || endpoint.isEmpty()) {
            log.warn("No observability endpoint configured - cannot trigger poll");
            return statuses;
        }

        LLMStatus status = healthAdapter.checkHealth(endpoint, model)
                .block();

        if (status != null) {
            LLMStatus finalStatus = checkWarnCondition(status);
            persistStatus(finalStatus);
            statuses.add(finalStatus);
        }

        return statuses;
    }

    /**
     * Convert entity to DTO.
     */
    private LLMStatus entityToDto(LLMStatusEntity entity) {
        List<String> modelNames = entity.getModelNames() != null && !entity.getModelNames().isEmpty()
                ? List.of(entity.getModelNames().split(","))
                : List.of();

        return new LLMStatus(
                entity.getEndpoint(),
                entity.getConfiguredModel(),
                AdapterStatus.valueOf(entity.getStatus()),
                entity.getLastChecked(),
                entity.getModelCount(),
                modelNames,
                entity.getErrorMessage()
        );
    }
}
