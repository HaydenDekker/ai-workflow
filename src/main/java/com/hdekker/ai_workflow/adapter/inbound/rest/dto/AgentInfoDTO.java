package com.hdekker.ai_workflow.adapter.inbound.rest.dto;

import java.time.LocalDateTime;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentSource;

public record AgentInfoDTO(
    String id,
    AgentDefinition definition,
    LocalDateTime createdAt,
    boolean active,
    AgentSource source
) {}
