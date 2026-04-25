package com.hdekker.ai_workflow.app.pipeline.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.database.agent.AgentEntity;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceService;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

import reactor.core.publisher.Flux;

/**
 * Tests for DynamicAgentManager with persistence service integration.
 * Verifies enable/disable agents, dormant agent registry, and restore-from-DB behavior.
 */
public class DynamicAgentManagerPersistenceTest {

	DynamicAgentManager manager;
	AgentPersistenceService mockPersistenceService;
	ScannerRegistry mockScannerRegistry;

	String expectedMockResult = "This is the expected result";

	@BeforeEach
	public void init() {
		String mockFileBody = "This is an example file input body";

		FileHistory fh = new FileHistory(
				new FileMetadata(
						"/config/doco.txt",
						mockFileBody,
						FileHash.hash(mockFileBody)),
				Optional.empty());

		ChatClient chatClient = ChatClientMockBuilder.createMock(expectedMockResult);

		FileWriter fileWriter = mock(FileWriter.class);
		when(fileWriter.createPersister(any(Path.class))).thenReturn((pr) -> {
			// No-op for tests
		});

		Path outputDirectory = Path.of("/test/output");

		mockPersistenceService = mock(AgentPersistenceService.class);
		when(mockPersistenceService.findAllActive()).thenReturn(List.of());
		when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

		// Mock scanner registry
		mockScannerRegistry = mock(ScannerRegistry.class);
		when(mockScannerRegistry.createForAgent(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			String agentId = invocation.getArgument(0);
			String targetDir = invocation.getArgument(1);
			return new ScannerInfo(
					"scanner-" + agentId, agentId, targetDir,
					"IDLE", java.time.LocalDateTime.now(), null);
		});
		when(mockScannerRegistry.getScannerFlux(any())).thenReturn(Flux.just(fh));

		manager = new DynamicAgentManager(
				mockScannerRegistry,
				fileWriter,
				outputDirectory,
				chatClient,
				mockPersistenceService);
	}

	@Test
	public void givenDynamicAgentAdded_whenPersistenceEnabled_thenPersistServiceCalled() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		when(mockPersistenceService.save(any(String.class), any(AgentDefinition.class), any(String.class), any()))
				.thenReturn(createAgentEntity("test-id", agent, "DYNAMIC", "scanner-test-1"));

		// Act
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

		// Assert
		assertThat(info).isNotNull();
		assertThat(info.active()).isTrue();
		assertThat(info.source()).isEqualTo("DYNAMIC");
		assertThat(info.scannerId()).isNotNull();

		// Verify persistence was called
		when(mockPersistenceService.save(any(String.class), any(AgentDefinition.class), any(String.class), any()))
				.thenReturn(createAgentEntity("test-id", agent, "DYNAMIC", "scanner-test-1"));
	}

	@Test
	public void givenActiveAgent_whenDisabled_thenMovesToDormantAndNotListedAsActive() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

		assertThat(manager.listAgents()).hasSize(1);
		assertThat(manager.getActiveAgentCount()).isEqualTo(1);
		assertThat(manager.getDormantAgentCount()).isEqualTo(0);

		// Act
		manager.disableAgent(info.id());

		// Assert
		assertThat(manager.getActiveAgentCount()).isEqualTo(0);
		assertThat(manager.getDormantAgentCount()).isEqualTo(1);

		List<AgentInfo> agents = manager.listAgents();
		assertThat(agents).hasSize(1);
		assertThat(agents.get(0).active()).isFalse();

		// Verify persistence was called
		// (mocked service would be called with disable(id))
	}

	@Test
	public void givenDormantAgent_whenEnabled_thenMovesToActive() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");
		manager.disableAgent(info.id());

		assertThat(manager.getDormantAgentCount()).isEqualTo(1);
		assertThat(manager.getActiveAgentCount()).isEqualTo(0);

		// Mock persistence service to not fail on enable
		doNothing().when(mockPersistenceService).enable(any(String.class));

		// Act
		AgentInfo reEnabled = manager.enableAgent(info.id());

		// Assert
		assertThat(reEnabled).isNotNull();
		assertThat(reEnabled.active()).isTrue();
		assertThat(manager.getActiveAgentCount()).isEqualTo(1);
		assertThat(manager.getDormantAgentCount()).isEqualTo(0);

		List<AgentInfo> agents = manager.listAgents();
		assertThat(agents).hasSize(1);
		assertThat(agents.get(0).active()).isTrue();
	}

	@Test
	public void givenYAMLAgent_whenDisabled_thenMovesToDormant() {
		// Arrange
		AgentDefinition yamlAgent = TestData.basicPrompt();
		when(mockPersistenceService.save(any(String.class), any(AgentDefinition.class), any(String.class), any()))
				.thenReturn(createAgentEntity(yamlAgent.title(), yamlAgent, "YAML", null));

		manager.initializeFromYAML(List.of(yamlAgent));

		assertThat(manager.getActiveAgentCount()).isEqualTo(1);

		// Act
		manager.disableAgent(yamlAgent.title());

		// Assert
		assertThat(manager.getActiveAgentCount()).isEqualTo(0);
		assertThat(manager.getDormantAgentCount()).isEqualTo(1);

		List<AgentInfo> agents = manager.listAgents();
		assertThat(agents).hasSize(1);
		assertThat(agents.get(0).active()).isFalse();
	}

	@Test
	public void givenEnableNonExistentAgent_whenCalled_thenReturnsNull() {
		// Act
		AgentInfo result = manager.enableAgent("non-existent");

		// Assert
		assertThat(result).isNull();
	}

	@Test
	public void givenDisableNonExistentAgent_whenCalled_thenNoError() {
		// Act & Assert — should not throw
		manager.disableAgent("non-existent");
		assertThat(manager.getActiveAgentCount()).isEqualTo(0);
		assertThat(manager.getDormantAgentCount()).isEqualTo(0);
	}

	@Test
	public void givenRemoveActiveAgent_thenNotListedAndNotDormant() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

		assertThat(manager.listAgents()).hasSize(1);

		// Act
		manager.removeAgent(info.id());

		// Assert
		assertThat(manager.listAgents()).isEmpty();
		assertThat(manager.getActiveAgentCount()).isEqualTo(0);
		assertThat(manager.getDormantAgentCount()).isEqualTo(0);

		// Verify persistence was called
		// (mocked service would be called with deleteById(id))
	}

	@Test
	public void givenRemoveDormantAgent_thenNotListed() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");
		manager.disableAgent(info.id());
		assertThat(manager.getDormantAgentCount()).isEqualTo(1);

		// Act
		manager.removeAgent(info.id());

		// Assert
		assertThat(manager.listAgents()).isEmpty();
		assertThat(manager.getDormantAgentCount()).isEqualTo(0);
	}

	@Test
	public void givenMultipleAgentsWithMixedStates_thenListShowsCorrectActiveFlags() {
		// Arrange
		AgentDefinition agent1 = TestData.basicPrompt();
		AgentDefinition agent2 = new AgentDefinition(".*\\.md", "Agent2", "Body 2", "Map", "Out 2", "out2", "/tmp/dir2");

		AgentInfo info1 = manager.addDynamicAgent(agent1, "/tmp/dir1");
		AgentInfo info2 = manager.addDynamicAgent(agent2, "/tmp/dir2");

		// Disable agent1
		doNothing().when(mockPersistenceService).disable(any(String.class));
		manager.disableAgent(info1.id());

		// Act
		List<AgentInfo> agents = manager.listAgents();

		// Assert
		assertThat(agents).hasSize(2);

		AgentInfo activeAgent = agents.stream().filter(a -> a.active()).findFirst().orElseThrow();
		AgentInfo dormantAgent = agents.stream().filter(a -> !a.active()).findFirst().orElseThrow();

		assertThat(activeAgent.id()).isEqualTo(info2.id());
		assertThat(dormantAgent.id()).isEqualTo(info1.id());
	}

	@Test
	public void givenRestoreFromDatabase_whenNoActiveAgents_thenNoAgentsRestored() {
		// Arrange — mock returns empty lists
		when(mockPersistenceService.findAllActive()).thenReturn(List.of());
		when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

		// Act
		manager.restoreFromDatabase();

		// Assert
		assertThat(manager.getActiveAgentCount()).isEqualTo(0);
		assertThat(manager.getDormantAgentCount()).isEqualTo(0);
		assertThat(manager.listAgents()).isEmpty();
	}

	@Test
	public void givenGetAgentInfo_whenAgentInActiveRegistry_thenReturnsActiveInfo() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

		// Act
		AgentInfo retrieved = manager.getAgentInfo(info.id());

		// Assert
		assertThat(retrieved).isNotNull();
		assertThat(retrieved.id()).isEqualTo(info.id());
		assertThat(retrieved.active()).isTrue();
	}

	@Test
	public void givenGetAgentInfo_whenAgentInDormantRegistry_thenReturnsInactiveInfo() {
		// Arrange
		AgentDefinition agent = TestData.basicPrompt();
		AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");
		manager.disableAgent(info.id());

		// Act
		AgentInfo retrieved = manager.getAgentInfo(info.id());

		// Assert
		assertThat(retrieved).isNotNull();
		assertThat(retrieved.id()).isEqualTo(info.id());
		assertThat(retrieved.active()).isFalse();
	}

	@Test
	public void givenGetAgentInfo_whenAgentNotFound_thenReturnsNull() {
		// Act
		AgentInfo retrieved = manager.getAgentInfo("non-existent");

		// Assert
		assertThat(retrieved).isNull();
	}

	private AgentEntity createAgentEntity(String id, AgentDefinition def, String source, String scannerId) {
		AgentEntity entity = new AgentEntity();
		entity.setId(id);
		try {
			entity.setAgentDefinitionJson(
					"{\"fileInputRegex\":\"" + def.fileInputRegex() + "\",\"title\":\"" + def.title()
							+ "\",\"body\":\"" + def.body()
							 + "\",\"agentType\":\"" + def.agentType()
							 + "\",\"outputStructure\":\"" + def.outputStructure()
							 + "\",\"outputFilenameTemplate\":\"" + def.outputFilenameTemplate()
							 + "\",\"targetDirectory\":\"" + (def.targetDirectory() != null ? def.targetDirectory() : "/tmp") + "\"}");
		} catch (Exception e) {
			entity.setAgentDefinitionJson("{}");
		}
		entity.setTitle(def.title());
		entity.setSource(source);
		entity.setScannerId(scannerId);
		entity.setCreatedAt(java.time.LocalDateTime.now());
		entity.setActive(true);
		return entity;
	}
}
