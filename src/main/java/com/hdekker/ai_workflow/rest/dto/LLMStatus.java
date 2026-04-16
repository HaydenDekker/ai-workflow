package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for LLM endpoint status.
 * Returned by REST API and used by UI components.
 */
public record LLMStatus(
    String endpoint,
    String configuredModel,
    AdapterStatus status,
    LocalDateTime lastChecked,
    Integer modelCount,
    List<String> modelNames,
    String errorMessage
) {}