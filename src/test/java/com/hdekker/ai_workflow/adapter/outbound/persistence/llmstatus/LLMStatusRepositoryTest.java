package com.hdekker.ai_workflow.adapter.outbound.persistence.llmstatus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;


import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestProfiles;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository test for LLMStatusEntity CRUD operations.
 * 
 * Tests that the entity can be created, read, updated, and deleted
 * using the repository with the configured database.
 * 
 * Note: Uses @SpringBootTest because @DataJpaTest is not available in Spring Boot 4.0.3.
 * Tests run against SQLite database configured in application.yml.
 */
@DataJpaTest
@ActiveProfiles(TestProfiles.RESOURCES_TEST_FOLDER)
public class LLMStatusRepositoryTest {
	
	@Autowired
	private LLMStatusJpaRepository repository;
	
	@Test
	public void givenDatabaseConfig_whenEntityCreated_thenReturnEntity() {
		// Arrange
		LLMStatusEntity entity = new LLMStatusEntity();
		entity.setEndpoint("http://192.168.2.108:11434");
		entity.setConfiguredModel("gemma3:27b");
		entity.setStatus("UP");
		entity.setLastChecked(LocalDateTime.now());
		entity.setModelCount(3);
		entity.setModelNames("qwen3,gemma3:27b,gemma3:4b");
		entity.setErrorMessage(null);
		
		// Act
		LLMStatusEntity saved = repository.save(entity);
		
		// Assert
		assertThat(saved).isNotNull();
		assertThat(saved.getEndpoint()).isEqualTo("http://192.168.2.108:11434");
		assertThat(saved.getConfiguredModel()).isEqualTo("gemma3:27b");
		assertThat(saved.getStatus()).isEqualTo("UP");
		assertThat(saved.getModelCount()).isEqualTo(3);
		assertThat(saved.getModelNames()).isEqualTo("qwen3,gemma3:27b,gemma3:4b");
		assertThat(saved.getErrorMessage()).isNull();
		assertThat(saved.getLastChecked()).isNotNull();
	}
	
	@Test
	public void givenEndpoint_whenFindByEndpoint_thenReturnEntity() {
		// Arrange
		LLMStatusEntity entity = new LLMStatusEntity("http://192.168.2.108:11434", 
			"gemma3:27b", "UP", LocalDateTime.now(), 3, "qwen3,gemma3:27b", null);
		repository.save(entity);
		
		// Act
		Optional<LLMStatusEntity> found = repository.findByEndpoint("http://192.168.2.108:11434");
		
		// Assert
		assertThat(found).isPresent();
		assertThat(found.get().getEndpoint()).isEqualTo("http://192.168.2.108:11434");
		assertThat(found.get().getStatus()).isEqualTo("UP");
	}
	
	@Test
	public void givenSavedEntity_whenUpdated_thenReturnUpdatedEntity() {
		// Arrange
		LLMStatusEntity entity = new LLMStatusEntity("http://192.168.2.108:11434", 
			"gemma3:27b", "UP", LocalDateTime.now(), 3, "qwen3,gemma3:27b", null);
		repository.save(entity);
		
		// Act - Update status to DOWN
		entity.setStatus("DOWN");
		entity.setErrorMessage("Connection timeout");
		entity.setModelCount(0);
		LLMStatusEntity updated = repository.save(entity);
		
		// Assert
		assertThat(updated.getStatus()).isEqualTo("DOWN");
		assertThat(updated.getErrorMessage()).isEqualTo("Connection timeout");
		assertThat(updated.getModelCount()).isEqualTo(0);
		
		// Verify from repository
		LLMStatusEntity fromRepo = repository.findById("http://192.168.2.108:11434").orElseThrow();
		assertThat(fromRepo.getStatus()).isEqualTo("DOWN");
		assertThat(fromRepo.getErrorMessage()).isEqualTo("Connection timeout");
	}
	
	@Test
	public void givenEntity_whenDeleted_thenEntityNotFound() {
		// Arrange
		LLMStatusEntity entity = new LLMStatusEntity("http://192.168.2.108:11434", 
			"gemma3:27b", "UP", LocalDateTime.now(), 3, "qwen3,gemma3:27b", null);
		repository.save(entity);
		
		// Verify entity exists
		assertThat(repository.findById("http://192.168.2.108:11434")).isPresent();
		
		// Act
		repository.delete(entity);
		
		// Assert
		assertThat(repository.findById("http://192.168.2.108:11434")).isEmpty();
	}
	
	@Test
	public void givenMultipleEndpoints_whenFindAll_thenReturnAllEntities() {
		// Arrange
		LLMStatusEntity entity1 = new LLMStatusEntity("http://192.168.2.108:11434", 
			"gemma3:27b", "UP", LocalDateTime.now(), 3, "qwen3,gemma3:27b", null);
		LLMStatusEntity entity2 = new LLMStatusEntity("http://localhost:11434", 
			"qwen3", "DOWN", LocalDateTime.now(), 0, "", "Connection refused");
		repository.save(entity1);
		repository.save(entity2);
		
		// Act
		var all = repository.findAll();
		
		// Assert
		assertThat(all).hasSize(2);
		assertThat(all.stream().map(LLMStatusEntity::getEndpoint))
			.containsExactlyInAnyOrder("http://192.168.2.108:11434", "http://localhost:11434");
	}
	
}
