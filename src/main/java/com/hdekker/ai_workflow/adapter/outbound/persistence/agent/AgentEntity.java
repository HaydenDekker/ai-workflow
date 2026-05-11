package com.hdekker.ai_workflow.adapter.outbound.persistence.agent;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentSource;

/**
 * Entity for storing agent configuration and state.
 * Persists to application.db table: agent
 */
@Entity
@Table(name = "agent")
public class AgentEntity {

	@Id
	private String id;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = AgentDefinitionConverter.class)
	private AgentDefinition agentDefinitionJson;

	private String title;

	@Enumerated(EnumType.STRING)
	private AgentSource source;

	private LocalDateTime createdAt;

	private LocalDateTime lastStartedAt;



	private boolean active = true;

	public AgentEntity() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public AgentDefinition getAgentDefinitionJson() {
		return agentDefinitionJson;
	}

	public void setAgentDefinitionJson(AgentDefinition agentDefinitionJson) {
		this.agentDefinitionJson = agentDefinitionJson;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public AgentSource getSource() {
		return source;
	}

	public void setSource(AgentSource source) {
		this.source = source;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getLastStartedAt() {
		return lastStartedAt;
	}

	public void setLastStartedAt(LocalDateTime lastStartedAt) {
		this.lastStartedAt = lastStartedAt;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
