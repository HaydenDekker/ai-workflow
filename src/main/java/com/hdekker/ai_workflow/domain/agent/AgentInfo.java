package com.hdekker.ai_workflow.domain.agent;

import java.time.LocalDateTime;

/**
 * Read-only view of an agent's runtime state.
 * <p>
 * Used by application services to return agent information.
 * REST and UI adapters convert this to their own DTO representations.
 */
public record AgentInfo(
        String id,
        AgentDefinition definition,
        LocalDateTime createdAt,
        boolean active,
        String source
) {
}
