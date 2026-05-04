package com.hdekker.ai_workflow.adapter.outbound.persistence.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;


import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Unit tests for AgentEntity — verifies field accessors and JSON serialization round-trip.
 */
public class AgentEntityTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Test
	public void givenEntityFields_whenSetAndRetrieve_thenReturnCorrectValues() {
		// Arrange
		AgentEntity entity = new AgentEntity();
		LocalDateTime now = LocalDateTime.now();

		// Act
		entity.setId("test-agent-id");
		entity.setAgentDefinitionJson("{\"title\":\"Test\"}");
		entity.setTitle("Test Agent");
		entity.setSource("YAML");
		entity.setCreatedAt(now);
		entity.setLastStartedAt(now);
		entity.setActive(true);

		// Assert
		assertThat(entity.getId()).isEqualTo("test-agent-id");
		assertThat(entity.getAgentDefinitionJson()).isEqualTo("{\"title\":\"Test\"}");
		assertThat(entity.getTitle()).isEqualTo("Test Agent");
		assertThat(entity.getSource()).isEqualTo("YAML");
		assertThat(entity.getCreatedAt()).isEqualTo(now);
		assertThat(entity.getLastStartedAt()).isEqualTo(now);
		assertThat(entity.isActive()).isTrue();
	}

	@Test
	public void givenEntity_whenDeserializedFromJson_thenReturnCorrectObject() throws JsonProcessingException {
		// Arrange
		String json = "{\"id\":\"agent-1\",\"agentDefinitionJson\":\"{\\\"title\\\":\\\"Test\\\"}\","
				+ "\"title\":\"Test\",\"source\":\"YAML\",\"createdAt\":\"2026-01-01T00:00:00\","
				+ "\"lastStartedAt\":\"2026-01-02T00:00:00\",\"active\":true}";

		// Act
		AgentEntity entity = objectMapper.readValue(json, AgentEntity.class);

		// Assert
		assertThat(entity.getId()).isEqualTo("agent-1");
		assertThat(entity.getTitle()).isEqualTo("Test");
		assertThat(entity.getSource()).isEqualTo("YAML");
		assertThat(entity.isActive()).isTrue();
	}

	@Test
	public void givenEntity_whenSerializedToJson_thenReturnValidJson() throws JsonProcessingException {
		// Arrange
		AgentEntity entity = new AgentEntity();
		entity.setId("agent-1");
		entity.setTitle("Test Agent");
		entity.setSource("DYNAMIC");
		entity.setActive(false);

		// Act
		String json = objectMapper.writeValueAsString(entity);

		// Assert
		assertThat(json).contains("\"id\":\"agent-1\"");
		assertThat(json).contains("\"title\":\"Test Agent\"");
		assertThat(json).contains("\"source\":\"DYNAMIC\"");
		assertThat(json).contains("\"active\":false");
	}

	@Test
	public void givenEntity_defaultActiveIsTrue() {
		AgentEntity entity = new AgentEntity();
		assertThat(entity.isActive()).isTrue();
	}

	@Test
	public void givenEntity_whenActiveSetToFalse_thenReflectsInEntity() {
		AgentEntity entity = new AgentEntity();
		entity.setActive(false);
		assertThat(entity.isActive()).isFalse();
	}
}
