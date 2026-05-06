package com.hdekker.ai_workflow.adapter.inbound.rest.dto;

import java.time.LocalDateTime;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;

public record AgentInfoDTO(
    String id,
    AgentDefinition definition,
    LocalDateTime createdAt,
    boolean active,
    String source
) {}
