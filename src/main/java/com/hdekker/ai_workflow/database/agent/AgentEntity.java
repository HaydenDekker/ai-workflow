package com.hdekker.ai_workflow.database.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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
	private String agentDefinitionJson;

	private String title;

	private String source; // "YAML" or "DYNAMIC"

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

	public String getAgentDefinitionJson() {
		return agentDefinitionJson;
	}

	public void setAgentDefinitionJson(String agentDefinitionJson) {
		this.agentDefinitionJson = agentDefinitionJson;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
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
