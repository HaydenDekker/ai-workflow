package com.hdekker.ai_workflow.adapter.outbound.persistence.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestProfiles;
import com.hdekker.ai_workflow.domain.agent.AgentSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository test for AgentEntity CRUD operations.
 * 
 * Tests that agents can be created, read, updated, deleted,
 * and queried by active state.
 * 
 * Note: Uses @DataJpaTest which is available in Spring Boot 4.0.3.
 * Tests run against H2 in-memory database configured in application-test.yml.
 */
@DataJpaTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class AgentRepositoryTest {

	@Autowired
	private AgentJpaRepository repository;

	@Test
	public void givenAgent_whenSaved_thenReturnAgentWithId() {
		// Arrange
		AgentEntity entity = new AgentEntity();
		entity.setId("test-agent-1");
		entity.setAgentDefinitionJson("{\"fileInputRegex\":\".*\",\"title\":\"Test\",\"body\":\"Hello\",\"agentType\":\"Map\",\"outputStructure\":\"clean\",\"outputFilenameTemplate\":\"output.txt\"}");
		entity.setTitle("Test Agent");
		entity.setSource(AgentSource.YAML);
		entity.setCreatedAt(LocalDateTime.now());
		entity.setActive(true);

		// Act
		AgentEntity saved = repository.save(entity);

		// Assert
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo("test-agent-1");
		assertThat(saved.getTitle()).isEqualTo("Test Agent");
		assertThat(saved.getSource()).isEqualTo(AgentSource.YAML);
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	public void givenAgentId_whenFindById_thenReturnAgent() {
		// Arrange
		AgentEntity entity = new AgentEntity();
		entity.setId("find-me-1");
		entity.setAgentDefinitionJson("{}");
		entity.setTitle("Find Me");
		entity.setSource(AgentSource.DYNAMIC);
		entity.setCreatedAt(LocalDateTime.now());
		repository.save(entity);

		// Act
		AgentEntity found = repository.findById("find-me-1").orElseThrow();

		// Assert
		assertThat(found.getTitle()).isEqualTo("Find Me");
		assertThat(found.getSource()).isEqualTo(AgentSource.DYNAMIC);
	}

	@Test
	public void givenSavedAgent_whenUpdated_thenReflectsInRepository() {
		// Arrange
		AgentEntity entity = new AgentEntity();
		entity.setId("update-me-1");
		entity.setAgentDefinitionJson("{}");
		entity.setTitle("Original Title");
		entity.setSource(AgentSource.YAML);
		entity.setCreatedAt(LocalDateTime.now());
		entity.setActive(true);
		repository.save(entity);

		// Act
		entity.setTitle("Updated Title");
		entity.setActive(false);
		entity.setLastStartedAt(LocalDateTime.now());
		AgentEntity updated = repository.save(entity);

		// Assert
		assertThat(updated.getTitle()).isEqualTo("Updated Title");
		assertThat(updated.isActive()).isFalse();
		assertThat(updated.getLastStartedAt()).isNotNull();

		// Verify from repository
		AgentEntity fromRepo = repository.findById("update-me-1").orElseThrow();
		assertThat(fromRepo.getTitle()).isEqualTo("Updated Title");
		assertThat(fromRepo.isActive()).isFalse();
	}

	@Test
	public void givenAgent_whenDeleted_thenNotFound() {
		// Arrange
		AgentEntity entity = new AgentEntity();
		entity.setId("delete-me-1");
		entity.setAgentDefinitionJson("{}");
		entity.setTitle("To Delete");
		entity.setSource(AgentSource.YAML);
		entity.setCreatedAt(LocalDateTime.now());
		repository.save(entity);

		// Verify exists
		assertThat(repository.findById("delete-me-1")).isPresent();

		// Act
		repository.deleteById("delete-me-1");

		// Assert
		assertThat(repository.findById("delete-me-1")).isEmpty();
	}

	@Test
	public void givenMultipleAgents_whenFindAllByOrderByCreatedAtDesc_thenReturnOrdered() {
		// Arrange
		AgentEntity older = new AgentEntity();
		older.setId("older-1");
		older.setAgentDefinitionJson("{}");
		older.setTitle("Older Agent");
		older.setSource(AgentSource.YAML);
		older.setCreatedAt(LocalDateTime.now().minusDays(1));
		repository.save(older);

		AgentEntity newer = new AgentEntity();
		newer.setId("newer-1");
		newer.setAgentDefinitionJson("{}");
		newer.setTitle("Newer Agent");
		newer.setSource(AgentSource.DYNAMIC);
		newer.setCreatedAt(LocalDateTime.now());
		repository.save(newer);

		// Act
		List<AgentEntity> all = repository.findAllByOrderByCreatedAtDesc();

		// Assert
		assertThat(all).hasSize(2);
		assertThat(all.get(0).getId()).isEqualTo("newer-1");
		assertThat(all.get(1).getId()).isEqualTo("older-1");
	}

	@Test
	public void givenAgentsWithDifferentActiveStates_whenFindByActiveTrue_thenReturnOnlyActive() {
		// Arrange
		AgentEntity active = new AgentEntity();
		active.setId("active-1");
		active.setAgentDefinitionJson("{}");
		active.setTitle("Active Agent");
		active.setSource(AgentSource.YAML);
		active.setCreatedAt(LocalDateTime.now());
		active.setActive(true);
		repository.save(active);

		AgentEntity inactive = new AgentEntity();
		inactive.setId("inactive-1");
		inactive.setAgentDefinitionJson("{}");
		inactive.setTitle("Inactive Agent");
		inactive.setSource(AgentSource.YAML);
		inactive.setCreatedAt(LocalDateTime.now());
		inactive.setActive(false);
		repository.save(inactive);

		// Act
		List<AgentEntity> activeAgents = repository.findByActiveTrueOrderByCreatedAtDesc();
		List<AgentEntity> inactiveAgents = repository.findByActiveFalseOrderByCreatedAtDesc();

		// Assert
		assertThat(activeAgents).hasSize(1);
		assertThat(activeAgents.get(0).getId()).isEqualTo("active-1");
		assertThat(inactiveAgents).hasSize(1);
		assertThat(inactiveAgents.get(0).getId()).isEqualTo("inactive-1");
	}

	@Test
	public void givenAgents_whenCountByActiveTrue_thenReturnCorrectCount() {
		// Arrange
		repository.save(createAgent("count-1", true));
		repository.save(createAgent("count-2", true));
		repository.save(createAgent("count-3", false));

		// Act & Assert
		assertThat(repository.countByActiveTrue()).isEqualTo(2);
		assertThat(repository.countByActiveFalse()).isEqualTo(1);
	}

	private AgentEntity createAgent(String id, boolean active) {
		AgentEntity entity = new AgentEntity();
		entity.setId(id);
		entity.setAgentDefinitionJson("{}");
		entity.setTitle("Count Agent " + id);
		entity.setSource(AgentSource.YAML);
		entity.setCreatedAt(LocalDateTime.now());
		entity.setActive(active);
		return entity;
	}
}
