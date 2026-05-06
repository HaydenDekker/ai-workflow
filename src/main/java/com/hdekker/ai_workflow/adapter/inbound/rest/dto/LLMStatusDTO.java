package com.hdekker.ai_workflow.adapter.inbound.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for LLM endpoint status.
 * Returned by REST API and used by UI components.
 */
public record LLMStatusDTO(
    String endpoint,
    String configuredModel,
    AdapterStatusDTO status,
    LocalDateTime lastChecked,
    Integer modelCount,
    List<String> modelNames,
    String errorMessage
) {}