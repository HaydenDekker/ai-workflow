package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;

public record PipelineInfo(
    String id,
    String title,
    String agentType,
    LocalDateTime createdAt,
    boolean active,
    String source  // "YAML" or "DYNAMIC"
) {}