package com.hdekker.ai_workflow.database.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;

/**
 * Unit tests for AgentPersistenceUsecase — verifies entity↔domain mapping.
 * 
 * Uses @DataJpaTest with @Import to test the usecase with a real repository backed by H2.
 */
@DataJpaTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
@Import({ AgentPersistenceUsecase.class, AgentPersistenceServiceTest.TestConfig.class })
public class AgentPersistenceServiceTest {

	static class TestConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper()
					.registerModule(new JavaTimeModule())
					.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		}
	}

	@Autowired
	private AgentPersistenceUsecase persistenceService;

	@Autowired
	private AgentRepository agentRepository;

	@Test
	public void givenAgentDefinition_whenSaved_thenReturnEntityWithJson() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt",
				"Test Agent",
				"This is a test prompt body.",
				"Map",
				"Clean output required",
				"output/{filename}",
				"/tmp/test-dir");

		// Act
		AgentEntity entity = persistenceService.save("persist-1", def, "DYNAMIC");

		// Assert
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isEqualTo("persist-1");
		assertThat(entity.getTitle()).isEqualTo("Test Agent");
		assertThat(entity.getSource()).isEqualTo("DYNAMIC");
		assertThat(entity.getAgentDefinitionJson()).contains("Test Agent");
		assertThat(entity.getAgentDefinitionJson()).contains("fileInputRegex");
		assertThat(entity.isActive()).isTrue();
		assertThat(entity.getCreatedAt()).isNotNull();
	}

	@Test
	public void givenAgentId_whenGetDefinition_thenReturnAgentDefinition() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.md",
				"Markdown Agent",
				"Process markdown files",
				"Split",
				"Markdown structure",
				"md-output/{filename}",
				"/tmp/markdown-dir");
		persistenceService.save("getdef-1", def, "YAML");

		// Act
		Optional<AgentDefinition> found = persistenceService.getDefinition("getdef-1");

		// Assert
		assertThat(found).isPresent();
		AgentDefinition retrieved = found.get();
		assertThat(retrieved.title()).isEqualTo("Markdown Agent");
		assertThat(retrieved.fileInputRegex()).isEqualTo(".*\\.md");
		assertThat(retrieved.body()).isEqualTo("Process markdown files");
		assertThat(retrieved.agentType()).isEqualTo("Split");
	}

	@Test
	public void givenNonExistentId_whenGetDefinition_thenReturnEmpty() {
		// Act
		Optional<AgentDefinition> found = persistenceService.getDefinition("non-existent");

		// Assert
		assertThat(found).isEmpty();
	}

	@Test
	public void givenMultipleAgents_whenListAll_thenReturnAllOrdered() throws InterruptedException {
		// Arrange
		AgentDefinition def1 = new AgentDefinition(".*\\.txt", "Agent 1", "Body 1", "Map", "Out 1", "out1", "/tmp/dir1");
		AgentDefinition def2 = new AgentDefinition(".*\\.md", "Agent 2", "Body 2", "Split", "Out 2", "out2", "/tmp/dir2");
		persistenceService.save("list-1", def1, "YAML");
		// Ensure distinct createdAt timestamps to avoid non-deterministic ordering
		// when H2 assigns the same millisecond timestamp to both saves.
		Thread.sleep(10);
		persistenceService.save("list-2", def2, "DYNAMIC");

		// Act
		List<AgentEntity> all = persistenceService.listAll();

		// Assert
		assertThat(all).hasSize(2);
		assertThat(all.get(0).getTitle()).isEqualTo("Agent 2"); // newest first
		assertThat(all.get(1).getTitle()).isEqualTo("Agent 1");
	}

	@Test
	public void givenAgentId_whenDeleted_thenNotListed() {
		// Arrange
		AgentDefinition def = new AgentDefinition(".*\\.txt", "Delete Me", "Body", "Map", "Out", "out", "/tmp/delete-me");
		persistenceService.save("del-1", def, "DYNAMIC");
		assertThat(persistenceService.listAll()).hasSize(1);

		// Act
		persistenceService.deleteById("del-1");

		// Assert
		assertThat(persistenceService.listAll()).isEmpty();
	}

	@Test
	public void givenAgent_whenToggledOff_thenActiveIsFalse() {
		// Arrange
		AgentDefinition def = new AgentDefinition(".*\\.txt", "Toggle Agent", "Body", "Map", "Out", "out", "/tmp/toggle");
		persistenceService.save("toggle-1", def, "YAML");

		// Act
		persistenceService.disable("toggle-1");

		// Assert
		AgentEntity entity = agentRepository.findById("toggle-1").orElseThrow();
		assertThat(entity.isActive()).isFalse();
		assertThat(entity.getLastStartedAt()).isNull();
	}

	@Test
	public void givenDisabledAgent_whenToggledOn_thenActiveIsTrueWithLastStartedAt() {
		// Arrange
		AgentDefinition def = new AgentDefinition(".*\\.txt", "Toggle On Agent", "Body", "Map", "Out", "out", "/tmp/toggle-on");
		persistenceService.save("toggle-on-1", def, "YAML");
		persistenceService.disable("toggle-on-1");

		// Act
		persistenceService.enable("toggle-on-1");

		// Assert
		AgentEntity entity = agentRepository.findById("toggle-on-1").orElseThrow();
		assertThat(entity.isActive()).isTrue();
		assertThat(entity.getLastStartedAt()).isNotNull();
	}

	@Test
	public void givenEnabledAgents_whenFindAllActive_thenReturnOnlyActive() {
		// Arrange
		AgentDefinition activeDef = new AgentDefinition(".*\\.txt", "Active Agent", "Body", "Map", "Out", "out", "/tmp/active");
		AgentDefinition inactiveDef = new AgentDefinition(".*\\.md", "Inactive Agent", "Body", "Split", "Out", "out", "/tmp/inactive");
		persistenceService.save("active-find-1", activeDef, "YAML");
		persistenceService.save("inactive-find-1", inactiveDef, "DYNAMIC");
		persistenceService.disable("inactive-find-1");

		// Act
		List<AgentEntity> active = persistenceService.findAllActive();

		// Assert
		assertThat(active).hasSize(1);
		assertThat(active.get(0).getTitle()).isEqualTo("Active Agent");
	}

	@Test
	public void givenAgent_whenSavedWithLargeBody_thenPersistsCorrectly() {
		// Arrange — large prompt body to verify TEXT column handling
		StringBuilder largeBody = new StringBuilder();
		for (int i = 0; i < 500; i++) {
			largeBody.append("Line ").append(i).append(" of the prompt body.\n");
		}
		AgentDefinition def = new AgentDefinition(
				".*\\.txt",
				"Large Body Agent",
				largeBody.toString(),
				"Map",
				"Output structure",
				"output/{filename}",
				"/tmp/large-body");

		persistenceService.save("large-1", def, "DYNAMIC");
		Optional<AgentDefinition> retrieved = persistenceService.getDefinition("large-1");

		// Assert
		assertThat(retrieved).isPresent();
		assertThat(retrieved.get().body()).isEqualTo(largeBody.toString());
	}

	@Test
	public void givenInvalidJson_whenGetDefinition_thenReturnEmpty() {
		// Arrange — manually insert invalid JSON
		agentRepository.save(createEntityWithJson("invalid-json-1", "NOT VALID JSON {"));

		// Act & Assert
		assertThatThrownBy(() -> persistenceService.getDefinition("invalid-json-1"))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Failed to deserialize");
	}

	private AgentEntity createEntityWithJson(String id, String json) {
		AgentEntity entity = new AgentEntity();
		entity.setId(id);
		entity.setAgentDefinitionJson(json);
		entity.setTitle("Test");
		entity.setSource("DYNAMIC");
		entity.setCreatedAt(java.time.LocalDateTime.now());
		entity.setActive(true);
		return entity;
	}
}
