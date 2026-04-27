package com.hdekker.ai_workflow.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.database.agent.AgentPersistenceUsecase;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.files.TargetDirectoryValidator;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

import reactor.core.publisher.Flux;

public class AgentLifecycleUseCaseTest {

    AgentLifecycleUseCase manager;
    ScannerRegistry scannerRegistry;
    AgentPersistenceUsecase mockPersistenceService;

    String expectedMockResult = "This is the expected result";

    boolean persistCalled = false;

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
            persistCalled = true;
        });

        Path outputDirectory = Path.of("/test/output");

        // Create a mock scanner registry
        scannerRegistry = mock(ScannerRegistry.class);
        when(scannerRegistry.createForAgent(anyString(), any(), anyInt())).thenAnswer(invocation -> {
            String agentId = invocation.getArgument(0);
            String targetDir = invocation.getArgument(1);
            return new ScannerInfo(
                    "scanner-" + agentId,
                    agentId,
                    targetDir,
                    "IDLE",
                    java.time.LocalDateTime.now(),
                    null);
        });
        when(scannerRegistry.getScannerFlux(any())).thenReturn(Flux.just(fh));

        // Mock persistence service
        mockPersistenceService = mock(AgentPersistenceUsecase.class);
        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        doNothing().when(mockPersistenceService).disable(anyString());
        doNothing().when(mockPersistenceService).enable(anyString());
        doNothing().when(mockPersistenceService).deleteById(anyString());

        // Use the new constructor with ScannerRegistry
        manager = new AgentLifecycleUseCase(
                scannerRegistry,
                fileWriter,
                outputDirectory,
                chatClient,
                mockPersistenceService,
                null);
    }

    @Test
    public void givenEmptyYAMLAgents_ExpectNoAgentsInitialized() {
        manager.initializeFromYAML(List.of());

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).isEmpty();
    }

    @Test
    public void givenYAMLAgents_ExpectAgentsInitialized() {
        AgentDefinition agent = TestData.basicPrompt();

        manager.initializeFromYAML(List.of(agent));

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);

        AgentInfo info = agents.get(0);
        assertThat(info.id()).isEqualTo(agent.title());
        assertThat(info.definition().title()).isEqualTo(agent.title());
        assertThat(info.definition().agentType()).isEqualTo(agent.agentType());
        assertThat(info.source()).isEqualTo("YAML");
        assertThat(info.active()).isTrue();
    }

    @Test
    public void givenDynamicAgentAdded_ExpectAgentCreatedAndListed() {
        AgentDefinition agent = TestData.basicPrompt();

        AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

        assertThat(info.definition().title()).isEqualTo(agent.title());
        assertThat(info.definition().agentType()).isEqualTo(agent.agentType());
        assertThat(info.source()).isEqualTo("DYNAMIC");
        assertThat(info.active()).isTrue();
        assertThat(info.scannerId()).isNotNull();

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).id()).isEqualTo(info.id());
    }

    @Test
    public void givenAgentRemoved_ExpectAgentNotListed() {
        AgentDefinition agent = TestData.basicPrompt();

        AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");
        assertThat(manager.listAgents()).hasSize(1);

        manager.removeAgent(info.id());
        assertThat(manager.listAgents()).isEmpty();
    }

    @Test
    public void givenNonExistentAgentRemoved_ExpectNoError() {
        manager.removeAgent("non-existent-id");
        // Should not throw exception
    }

    @Test
    public void givenMultipleAgents_ExpectAllListed() {
        AgentDefinition agent1 = TestData.basicPrompt();
        AgentDefinition agent2 = new AgentDefinition(".*\\.txt", "TestAgent2", "Test body 2", "Map", "Test structure 2", "output-{filename}", "/tmp/test-dir-2");

        AgentInfo info1 = manager.addDynamicAgent(agent1, "/tmp/test-dir-1");
        AgentInfo info2 = manager.addDynamicAgent(agent2, "/tmp/test-dir-2");

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(2);

        List<String> ids = agents.stream().map(AgentInfo::id).toList();
        assertThat(ids).contains(info1.id(), info2.id());

        // Verify each agent has a scanner ID
        for (AgentInfo agentInfo : agents) {
            assertThat(agentInfo.scannerId()).isNotNull();
        }
    }

    @Test
    public void givenYAMLandDynamicAgents_ExpectAllListed() {
        AgentDefinition yamlAgent = TestData.basicPrompt();
        AgentDefinition dynamicAgent = new AgentDefinition(".*\\.md", "DynamicAgent", "Dynamic body", "Map", "Dynamic structure", "dynamic-{filename}", "/tmp/dynamic-dir");

        manager.initializeFromYAML(List.of(yamlAgent));
        manager.addDynamicAgent(dynamicAgent, "/tmp/dynamic-dir");

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(2);

        List<String> sources = agents.stream().map(AgentInfo::source).toList();
        assertThat(sources).contains("YAML", "DYNAMIC");
    }

    @Test
    public void givenDynamicAgentRefreshed_ExpectAgentStillListed() {
        AgentDefinition agent = TestData.basicPrompt();

        AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");
        assertThat(manager.listAgents()).hasSize(1);

        AgentInfo refreshed = manager.refreshAgent(info.id());
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.id()).isEqualTo(info.id());
        assertThat(refreshed.scannerId()).isEqualTo(info.scannerId());

        assertThat(manager.listAgents()).hasSize(1);
    }

    @Test
    public void givenDynamicAgentEnabled_ExpectAgentListedAsActive() {
        AgentDefinition agent = TestData.basicPrompt();
        AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

        manager.disableAgent(info.id());
        assertThat(manager.listAgents()).hasSize(1);
        assertThat(manager.listAgents().get(0).active()).isFalse();

        AgentInfo enabled = manager.enableAgent(info.id());
        assertThat(enabled).isNotNull();
        assertThat(enabled.active()).isTrue();
    }

    @Test
    public void givenDynamicAgentDisabled_ExpectAgentNotActive() {
        AgentDefinition agent = TestData.basicPrompt();
        AgentInfo info = manager.addDynamicAgent(agent, "/tmp/test-dir");

        manager.disableAgent(info.id());

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).active()).isFalse();
    }

    @Test
    public void givenDynamicAgentUpdated_ExpectAgentWithNewDefinition() {
        AgentDefinition original = TestData.basicPrompt();
        original = new AgentDefinition(".*\\.txt", "OriginalAgent", "Original body", "Map", "Original structure", "original-{filename}", "/tmp/test-dir");

        AgentInfo info = manager.addDynamicAgent(original, "/tmp/test-dir");
        assertThat(manager.listAgents()).hasSize(1);

        AgentDefinition updated = new AgentDefinition(".*\\.md", "UpdatedAgent", "Updated body", "Reduction", "Updated structure", "updated-{filename}", "/tmp/test-dir-updated");
        AgentInfo updatedInfo = manager.updateAgent(info.id(), updated);

        assertThat(updatedInfo).isNotNull();
        // updateAgent uses remove+re-add, so a new UUID is generated
        assertThat(updatedInfo.definition().title()).isEqualTo("UpdatedAgent");
        assertThat(updatedInfo.definition().agentType()).isEqualTo("Reduction");
        assertThat(updatedInfo.definition().body()).isEqualTo("Updated body");
        assertThat(updatedInfo.definition().targetDirectory()).isEqualTo("/tmp/test-dir-updated");
        assertThat(updatedInfo.scannerId()).isNotNull();

        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).definition().title()).isEqualTo("UpdatedAgent");
    }

    @Test
    public void givenNonExistentAgentUpdated_ExpectNewAgentCreated() {
        // updateAgent uses remove+re-add pattern, so it always creates a new agent
        AgentDefinition updated = TestData.basicPrompt();
        AgentInfo result = manager.updateAgent("non-existent-id", updated);
        assertThat(result).isNotNull();
        assertThat(result.definition().title()).isEqualTo(TestData.basicPrompt().title());
        assertThat(result.source()).isEqualTo("DYNAMIC");
    }
}
