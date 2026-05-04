package com.hdekker.ai_workflow.rest.dto;

import java.time.LocalDateTime;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

public record AgentInfo(
    String id,
    AgentDefinition definition,
    LocalDateTime createdAt,
    boolean active,
    String source
) {}
