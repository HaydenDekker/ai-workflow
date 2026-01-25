package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

public record PipelineInfo(
    String id,
    AgentDefinition agentDefinition,
    LocalDateTime createdAt,
    boolean active,
    String source  // "YAML" or "DYNAMIC"
) {}