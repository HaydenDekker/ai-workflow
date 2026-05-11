package com.hdekker.ai_workflow.adapter.inbound.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfoDTO;
import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverUseCase;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentType;

import reactor.core.publisher.Mono;

/**
 * Tests for AgentInfoService UI service.
 *
 * Verifies agent CRUD operations and output file count retrieval.
 */
public class AgentInfoServiceTest {

	private AgentInfoService service;
	private AgentLifecycleService lifecycleService;
	private DirectoryValidationPort validationPort;
	private AgentObserverUseCase observer;

	@BeforeEach
	public void init() {
		lifecycleService = mock(AgentLifecycleService.class);
		validationPort = mock(DirectoryValidationPort.class);
		observer = mock(AgentObserverUseCase.class);
		service = new AgentInfoService(lifecycleService, validationPort, observer);
	}

	@Test
	public void givenObserver_WhenGetOutputFileCount_thenReturnCount() {
		// Arrange
		when(observer.getOutputDirectoryFileCount()).thenReturn(42L);

		// Act
		Mono<Long> result = service.getOutputFileCount();
		long count = result.block();

		// Assert
		assertThat(count).isEqualTo(42L);
	}

	@Test
	public void givenObserver_WhenNoOutputFiles_thenReturnZero() {
		// Arrange
		when(observer.getOutputDirectoryFileCount()).thenReturn(0L);

		// Act
		Mono<Long> result = service.getOutputFileCount();
		long count = result.block();

		// Assert
		assertThat(count).isEqualTo(0L);
	}

	@Test
	public void givenNullObserver_WhenGetOutputFileCount_thenReturnZero() {
		// Arrange
		service = new AgentInfoService(lifecycleService, validationPort, null);

		// Act
		Mono<Long> result = service.getOutputFileCount();
		long count = result.block();

		// Assert
		assertThat(count).isEqualTo(0L);
	}

	@Test
	public void givenObserverThrows_WhenGetOutputFileCount_thenReturnZero() {
		// Arrange
		when(observer.getOutputDirectoryFileCount()).thenThrow(new RuntimeException("Disk error"));

		// Act
		Mono<Long> result = service.getOutputFileCount();
		long count = result.block();

		// Assert
		assertThat(count).isEqualTo(0L);
	}

	@Test
	public void givenValidAgent_WhenCreateAgent_thenReturnAgentInfo() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt", "Test Agent", "Test prompt", AgentType.MAP, "Structure",
				"output/{filename}", "/tmp/test-dir");
		when(validationPort.validate(anyString())).thenReturn(
				DirectoryValidationPort.ValidationResult.success());
		com.hdekker.ai_workflow.domain.agent.AgentInfo info =
				new com.hdekker.ai_workflow.domain.agent.AgentInfo("id-1", def,
						LocalDateTime.now(), true, "DYNAMIC");
		when(lifecycleService.addDynamicAgent(def, "/tmp/test-dir")).thenReturn(info);

		// Act
		Mono<AgentInfoDTO> result = service.createAgent(def);
		AgentInfoDTO dto = result.block();

		// Assert
		assertThat(dto).isNotNull();
		assertThat(dto.id()).isEqualTo("id-1");
		assertThat(dto.definition().title()).isEqualTo("Test Agent");
	}

	@Test
	public void givenInvalidDir_WhenCreateAgent_thenReturnError() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt", "Test Agent", "Test prompt", AgentType.MAP, "Structure",
				"output/{filename}", "");
		when(validationPort.validate(anyString())).thenReturn(
				DirectoryValidationPort.ValidationResult.failure("targetDirectory is required"));

		// Act & Assert - service wraps validation errors in Mono.error
		try {
			service.createAgent(def).block();
			assertThat(false).as("Expected Mono.error").isTrue();
		} catch (IllegalArgumentException ex) {
			assertThat(ex.getMessage()).isEqualTo("targetDirectory is required");
		}
	}

	@Test
	public void givenAgentId_WhenDeleteAgent_thenCompletes() {
		// Act
		Mono<String> result = service.deleteAgent("agent-1");

		// Assert
		assertThat(result.block()).isEqualTo("agent-1");
	}

	@Test
	public void givenAgentIds_WhenListAgents_thenReturnAll() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt", "Test", "Prompt", AgentType.MAP, "Structure",
				"output", "/tmp");
		com.hdekker.ai_workflow.domain.agent.AgentInfo info1 =
				new com.hdekker.ai_workflow.domain.agent.AgentInfo("id-1", def,
						LocalDateTime.now(), true, "YAML");
		com.hdekker.ai_workflow.domain.agent.AgentInfo info2 =
				new com.hdekker.ai_workflow.domain.agent.AgentInfo("id-2", def,
						LocalDateTime.now().minusDays(1), true, "DYNAMIC");
		when(lifecycleService.listAgents()).thenReturn(List.of(info1, info2));

		// Act
		Mono<List<AgentInfoDTO>> result = service.getAllAgentInfos();
		List<AgentInfoDTO> dtos = result.block();

		// Assert
		assertThat(dtos).hasSize(2);
		assertThat(dtos.get(0).id()).isEqualTo("id-1");
		assertThat(dtos.get(1).id()).isEqualTo("id-2");
	}

	@Test
	public void givenException_WhenListAgents_thenReturnEmpty() {
		// Arrange
		when(lifecycleService.listAgents()).thenThrow(new RuntimeException("DB error"));

		// Act
		Mono<List<AgentInfoDTO>> result = service.getAllAgentInfos();
		List<AgentInfoDTO> dtos = result.block();

		// Assert
		assertThat(dtos).isEmpty();
	}

	@Test
	public void givenAgentId_WhenRefreshAgent_thenReturnUpdatedInfo() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt", "Test", "Prompt", AgentType.MAP, "Structure",
				"output", "/tmp");
		com.hdekker.ai_workflow.domain.agent.AgentInfo info =
				new com.hdekker.ai_workflow.domain.agent.AgentInfo("id-1", def,
						LocalDateTime.now(), true, "DYNAMIC");
		when(lifecycleService.refreshAgent(anyString())).thenReturn(info);

		// Act
		Mono<AgentInfoDTO> result = service.refreshAgent("id-1");
		AgentInfoDTO dto = result.block();

		// Assert
		assertThat(dto).isNotNull();
		assertThat(dto.id()).isEqualTo("id-1");
	}

	@Test
	public void givenNonExistentAgent_WhenRefreshAgent_thenThrowsError() {
		// Arrange
		when(lifecycleService.refreshAgent(anyString())).thenReturn(null);

		// Act & Assert - service wraps null in Mono.error
		try {
			service.refreshAgent("non-existent").block();
			assertThat(false).as("Expected Mono.error").isTrue();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage()).isEqualTo("Agent not found: non-existent");
		}
	}

	@Test
	public void givenAgentId_WhenUpdateAgent_thenReturnUpdatedInfo() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt", "Updated", "Prompt", AgentType.MAP, "Structure",
				"output", "/tmp");
		com.hdekker.ai_workflow.domain.agent.AgentInfo info =
				new com.hdekker.ai_workflow.domain.agent.AgentInfo("id-1", def,
						LocalDateTime.now(), true, "DYNAMIC");
		when(lifecycleService.updateAgent(anyString(), any(AgentDefinition.class))).thenReturn(info);

		// Act
		Mono<AgentInfoDTO> result = service.updateAgent("id-1", def);
		AgentInfoDTO dto = result.block();

		// Assert
		assertThat(dto).isNotNull();
		assertThat(dto.definition().title()).isEqualTo("Updated");
	}

	@Test
	public void givenNonExistentAgent_WhenUpdateAgent_thenThrowsError() {
		// Arrange
		AgentDefinition def = new AgentDefinition(
				".*\\.txt", "Test", "Prompt", AgentType.MAP, "Structure",
				"output", "/tmp");
		when(lifecycleService.updateAgent(anyString(), any(AgentDefinition.class))).thenReturn(null);

		// Act & Assert - service wraps null in Mono.error
		try {
			service.updateAgent("non-existent", def).block();
			assertThat(false).as("Expected Mono.error").isTrue();
		} catch (RuntimeException ex) {
			assertThat(ex.getMessage()).isEqualTo("Agent not found: non-existent");
		}
	}
}
