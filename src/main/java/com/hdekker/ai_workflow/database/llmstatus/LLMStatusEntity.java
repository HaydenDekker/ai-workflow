package com.hdekker.ai_workflow.database.llmstatus;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/**
 * Entity for storing LLM endpoint health status.
 * Persists to application.db table: llm_status
 */
@Entity
public class LLMStatusEntity {
	
	@Id
	private String endpoint;
	
	private String configuredModel;
	private String status;
	private LocalDateTime lastChecked;
	private Integer modelCount;
	private String modelNames;
	private String errorMessage;
	
	public LLMStatusEntity() {
	}
	
	public LLMStatusEntity(String endpoint, String configuredModel, String status,
			LocalDateTime lastChecked, Integer modelCount,
			String modelNames, String errorMessage) {
		this.endpoint = endpoint;
		this.configuredModel = configuredModel;
		this.status = status;
		this.lastChecked = lastChecked;
		this.modelCount = modelCount;
		this.modelNames = modelNames;
		this.errorMessage = errorMessage;
	}
	
	public String getEndpoint() {
		return endpoint;
	}
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}
	
	public String getConfiguredModel() {
		return configuredModel;
	}
	public void setConfiguredModel(String configuredModel) {
		this.configuredModel = configuredModel;
	}
	
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	
	public LocalDateTime getLastChecked() {
		return lastChecked;
	}
	public void setLastChecked(LocalDateTime lastChecked) {
		this.lastChecked = lastChecked;
	}
	
	public Integer getModelCount() {
		return modelCount;
	}
	public void setModelCount(Integer modelCount) {
		this.modelCount = modelCount;
	}
	
	public String getModelNames() {
		return modelNames;
	}
	public void setModelNames(String modelNames) {
		this.modelNames = modelNames;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	
}
