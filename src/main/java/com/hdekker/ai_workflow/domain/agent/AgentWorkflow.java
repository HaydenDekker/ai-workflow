package com.hdekker.ai_workflow.domain.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Logical grouping of related agents in a workflow.
 * Agents are connected via file I/O contracts (regex filters matching output filename templates),
 * not code-level chaining.
 */
public record AgentWorkflow(
		@JsonAlias("chain") List<AgentDefinition> agents
		) {

}
