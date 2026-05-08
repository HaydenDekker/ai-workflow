package com.hdekker.ai_workflow.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.application.agent.port.AgentRepository;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.application.pipeline.AgentObserverUseCase;
import com.hdekker.ai_workflow.application.pipeline.ScannerRegistry;
import com.hdekker.ai_workflow.application.scanner.ScannerService;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentInfo;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import reactor.core.publisher.Flux;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Tests for AgentLifecycleService scanner creation during restore-from-database.
 *
 * Validates that when the application starts and restores agents from the database,
 * scanners are created in the ScannerRegistry for each restored agent - ensuring
 * the /scanners view shows dynamic agents alongside YAML agents.
 */
public class AgentLifecycleServiceScannerRestoreTest {

    AgentLifecycleService manager;
    AgentRepository mockPersistenceService;
    ScannerRegistry mockScannerRegistry;
    java.util.List<ScannerService.ScannerInfo> createdScanners;
    java.util.List<String> fluxLookups;

    @BeforeEach
    public void init() {
        createdScanners = new java.util.ArrayList<>();
        fluxLookups = new java.util.ArrayList<>();

        ChatClient chatClient = ChatClientMockBuilder.createMock("mock response");

        FileWritePort fileWritePort = mock(FileWritePort.class);
        when(fileWritePort.createPersister(any(Path.class))).thenReturn((Consumer<PromptResponse>) pr -> {});

        Path outputDirectory = Path.of("/test/output");

        mockPersistenceService = mock(AgentRepository.class);
        mockScannerRegistry = mock(ScannerRegistry.class);

        // Track scanner creations
        when(mockScannerRegistry.createForAgent(anyString(), any(), anyInt())).thenAnswer(invocation -> {
            String agentId = invocation.getArgument(0);
            String targetDir = invocation.getArgument(1);
            ScannerService.ScannerInfo info = new ScannerService.ScannerInfo(
                    agentId, "scanner-" + agentId, targetDir,
                    "IDLE", null, LocalDateTime.now(), null, null);
            createdScanners.add(info);
            return info;
        });
        when(mockScannerRegistry.getScannerFlux(anyString())).thenReturn(Flux.<FileHistory>empty());

        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        doNothing().when(mockPersistenceService).disable(anyString());
        doNothing().when(mockPersistenceService).enable(anyString());
        doNothing().when(mockPersistenceService).deleteById(anyString());

        DirectoryValidationPort validator = mock(DirectoryValidationPort.class);
        when(validator.validate(anyString())).thenReturn(DirectoryValidationPort.ValidationResult.success());

        AgentObserverUseCase observerMock = Mockito.mock(AgentObserverUseCase.class);

        manager = new AgentLifecycleService(
                mockScannerRegistry,
                fileWritePort,
                outputDirectory,
                chatClient,
                mockPersistenceService,
                validator,
                observerMock);
    }

    @Test
    public void givenNoAgentsInDatabase_ExpectNoScannersCreated() {
        // Arrange - no active or dormant agents
        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

        // Act
        manager.restoreFromDatabase();

        // Assert - no scanners should be created
        assertThat(createdScanners).isEmpty();
        assertThat(manager.getActiveAgentCount()).isEqualTo(0);
        assertThat(manager.getDormantAgentCount()).isEqualTo(0);
    }

    @Test
    public void givenActiveAgentInDatabase_ExpectScannerCreatedDuringRestore() {
        // Arrange - an active agent definition in the database
        AgentDefinition activeDef = createDefinition("restored-agent-1", "/tmp/restored-dir");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeDef));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

        // Act
        manager.restoreFromDatabase();

        // Assert - scanner should have been created
        assertThat(createdScanners).hasSize(1);
        assertThat(createdScanners.get(0).agentId()).isEqualTo("restored-agent-1");
        assertThat(createdScanners.get(0).folderPath()).isEqualTo("/tmp/restored-dir");
        assertThat(manager.getActiveAgentCount()).isEqualTo(1);
        assertThat(manager.getDormantAgentCount()).isEqualTo(0);

        // Verify the agent is listed
        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).active()).isTrue();
    }

    @Test
    public void givenDormantAgentInDatabase_ExpectScannerNotCreatedForDormant() {
        // Arrange - a dormant agent (in allOrdered but not in active)
        AgentDefinition dormantDef = createDefinition("dormant-agent-1", "/tmp/dormant-dir");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of(dormantDef));

        // Act
        manager.restoreFromDatabase();

        // Assert - dormant agents should not create scanners (they're inactive)
        assertThat(createdScanners).isEmpty();
        assertThat(manager.getActiveAgentCount()).isEqualTo(0);
        assertThat(manager.getDormantAgentCount()).isEqualTo(1);
    }

    @Test
    public void givenActiveAndDormantAgentsInDatabase_ExpectScannerOnlyForActive() {
        // Arrange
        AgentDefinition activeDef = createDefinition("active-1", "/tmp/active-dir");
        AgentDefinition dormantDef = createDefinition("dormant-1", "/tmp/dormant-dir");

        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeDef));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of(activeDef, dormantDef));

        // Act
        manager.restoreFromDatabase();

        // Assert
        assertThat(createdScanners).hasSize(1);
        assertThat(createdScanners.get(0).agentId()).isEqualTo("active-1");
        assertThat(manager.getActiveAgentCount()).isEqualTo(1);
        assertThat(manager.getDormantAgentCount()).isEqualTo(1);
    }

    @Test
    public void givenRestoredAgent_ExpectListAgentsShowsInfo() {
        // Arrange
        AgentDefinition activeDef = createDefinition("list-test-1", "/tmp/list-dir");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeDef));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

        // Act
        manager.restoreFromDatabase();
        List<AgentInfo> agents = manager.listAgents();

        // Assert
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).id()).isEqualTo("list-test-1");
        assertThat(agents.get(0).active()).isTrue();
        assertThat(agents.get(0).source()).isEqualTo("YAML");
    }

    @Test
    public void givenAgentWithoutTargetDirectory_ExpectAgentSkipped() {
        // Arrange — agent with null targetDirectory.
        // ScannerRegistry.createForAgent rejects null/invalid paths.
        AgentDefinition noTargetDef = new AgentDefinition(
                ".*\\.txt", "No Target Agent", "prompt", "Map", "structure", "template", null);

        when(mockPersistenceService.findAllActive()).thenReturn(List.of(noTargetDef));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

        // Act
        manager.restoreFromDatabase();

        // Assert — agent is skipped because scanner creation fails for null targetDirectory
        assertThat(createdScanners).isEmpty();
        assertThat(manager.getActiveAgentCount()).isEqualTo(0);
    }

    @Test
    public void givenMultipleActiveAgentsInDatabase_ExpectScannerCreatedForEach() {
        // Arrange
        AgentDefinition agent1 = createDefinition("multi-1", "/tmp/dir1");
        AgentDefinition agent2 = createDefinition("multi-2", "/tmp/dir2");

        when(mockPersistenceService.findAllActive()).thenReturn(List.of(agent1, agent2));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

        // Act
        manager.restoreFromDatabase();

        // Assert
        assertThat(createdScanners).hasSize(2);
        List<String> agentIds = createdScanners.stream().map(ScannerService.ScannerInfo::agentId).toList();
        assertThat(agentIds).containsExactlyInAnyOrder("multi-1", "multi-2");
        assertThat(manager.getActiveAgentCount()).isEqualTo(2);
    }

    @Test
    public void givenRestoreThenAddDynamicAgent_ExpectBothHaveScanners() {
        // Arrange - restore one agent from DB
        AgentDefinition activeDef = createDefinition("restored-1", "/tmp/restored");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeDef));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        doNothing().when(mockPersistenceService).deleteById(anyString());

        // Act - restore then add a new dynamic agent
        manager.restoreFromDatabase();
        AgentDefinition newAgent = TestData.basicPrompt();
        manager.addDynamicAgent(newAgent, "/tmp/new-agent-dir");

        // Assert - both should have scanners
        assertThat(createdScanners).hasSize(2);
        // Verify the restored agent has a scanner
        assertThat(manager.listAgents().stream().filter(a -> a.id().equals("restored-1")).findFirst())
                .isPresent();
        // Verify the newly added dynamic agent has a scanner
        assertThat(manager.listAgents().stream().filter(a -> !a.id().equals("restored-1")).findFirst())
                .isPresent();
        assertThat(manager.listAgents()).hasSize(2);
    }

    // ── Helpers ──

    private AgentDefinition createDefinition(String title, String targetDirectory) {
        return new AgentDefinition(
                ".*\\.txt", title, "prompt", "Map", "structure", "out/{filename}",
                targetDirectory);
    }

    // ── End of helpers ──
}
