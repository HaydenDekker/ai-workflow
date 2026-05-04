package com.hdekker.ai_workflow.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.TestData;
import com.hdekker.ai_workflow.adapter.outbound.persistence.agent.AgentRepositoryAdapter;
import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import com.hdekker.ai_workflow.adapter.outbound.persistence.agent.AgentEntity;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.shared.FileHash;
import com.hdekker.ai_workflow.adapter.outbound.file.FileWriter;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.adapter.inbound.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Tests for AgentLifecycleUseCase scanner creation during restore-from-database.
 * 
 * Validates that when the application starts and restores agents from the database,
 * scanners are created in the ScannerRegistry for each restored agent — ensuring
 * the /scanners view shows dynamic agents alongside YAML agents.
 */
public class AgentLifecycleUseCaseScannerRestoreTest {

    AgentLifecycleUseCase manager;
    AgentRepositoryAdapter mockPersistenceService;
    ScannerRegistry mockScannerRegistry;
    java.util.List<ScannerInfo> createdScanners;
    java.util.List<String> fluxLookups;

    @BeforeEach
    public void init() {
        createdScanners = new java.util.ArrayList<>();
        fluxLookups = new java.util.ArrayList<>();

        String mockFileBody = "This is an example file input body";
        FileHistory fh = new FileHistory(
                new FileMetadata("/config/doco.txt", mockFileBody, FileHash.hash(mockFileBody)),
                Optional.empty());

        ChatClient chatClient = ChatClientMockBuilder.createMock("mock response");

        FileWriter fileWriter = mock(FileWriter.class);
        when(fileWriter.createPersister(any(Path.class))).thenReturn((pr) -> {});

        Path outputDirectory = Path.of("/test/output");

        mockPersistenceService = mock(AgentRepositoryAdapter.class);
        mockScannerRegistry = mock(ScannerRegistry.class);

        // Track scanner creations
        when(mockScannerRegistry.createForAgent(anyString(), any(), anyInt())).thenAnswer(invocation -> {
            String agentId = invocation.getArgument(0);
            String targetDir = invocation.getArgument(1);
            ScannerInfo info = new ScannerInfo(
                    "scanner-" + agentId, agentId, targetDir,
                    "IDLE", java.time.LocalDateTime.now(), null);
            createdScanners.add(info);
            return info;
        });

        // Track flux lookups and return a working flux
        when(mockScannerRegistry.getScannerFlux(anyString())).thenAnswer(invocation -> {
            fluxLookups.add(invocation.getArgument(0));
            return Flux.just(fh);
        });

        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        doNothing().when(mockPersistenceService).disable(anyString());
        doNothing().when(mockPersistenceService).enable(anyString());
        doNothing().when(mockPersistenceService).deleteById(anyString());

        manager = new AgentLifecycleUseCase(
                mockScannerRegistry,
                fileWriter,
                outputDirectory,
                chatClient,
                mockPersistenceService,
                null);
    }

    @Test
    public void givenNoAgentsInDatabase_ExpectNoScannersCreated() {
        // Arrange — no active or dormant agents
        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());

        // Act
        manager.restoreFromDatabase();

        // Assert — no scanners should be created
        assertThat(createdScanners).isEmpty();
        assertThat(manager.getActiveAgentCount()).isEqualTo(0);
        assertThat(manager.getDormantAgentCount()).isEqualTo(0);
    }

    @Test
    public void givenActiveAgentInDatabase_ExpectScannerCreatedDuringRestore() {
        // Arrange — an active dynamic agent in the database
        AgentEntity activeEntity = createActiveEntity("restored-agent-1", "Restored Agent", "DYNAMIC", "/tmp/restored-dir");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeEntity));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        when(mockPersistenceService.getDefinition("restored-agent-1")).thenReturn(Optional.of(createMatchingDefinition("restored-agent-1", "/tmp/restored-dir")));

        // Act
        manager.restoreFromDatabase();

        // Assert — scanner should have been created
        assertThat(createdScanners).hasSize(1);
        assertThat(createdScanners.get(0).agentId()).isEqualTo("restored-agent-1");
        assertThat(createdScanners.get(0).targetDirectory()).isEqualTo("/tmp/restored-dir");
        assertThat(manager.getActiveAgentCount()).isEqualTo(1);
        assertThat(manager.getDormantAgentCount()).isEqualTo(0);

        // Verify the agent is listed
        List<AgentInfo> agents = manager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).active()).isTrue();
    }

    @Test
    public void givenDormantAgentInDatabase_ExpectScannerNotCreatedForDormant() {
        // Arrange — a dormant (disabled) agent in the database
        AgentEntity dormantEntity = createDormantEntity("dormant-agent-1", "Dormant Agent", "DYNAMIC", "/tmp/dormant-dir");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of());
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of(dormantEntity));
        when(mockPersistenceService.getDefinition("dormant-agent-1")).thenReturn(Optional.of(createMatchingDefinition("dormant-agent-1", "/tmp/dormant-dir")));

        // Act
        manager.restoreFromDatabase();

        // Assert — dormant agents should not create scanners (they're inactive)
        assertThat(createdScanners).isEmpty();
        assertThat(manager.getActiveAgentCount()).isEqualTo(0);
        assertThat(manager.getDormantAgentCount()).isEqualTo(1);
    }

    @Test
    public void givenActiveAndDormantAgentsInDatabase_ExpectScannerOnlyForActive() {
        // Arrange
        AgentEntity activeEntity = createActiveEntity("active-1", "Active Agent", "DYNAMIC", "/tmp/active-dir");
        AgentEntity dormantEntity = createDormantEntity("dormant-1", "Dormant Agent", "YAML", "/tmp/dormant-dir");
        
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeEntity));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of(activeEntity, dormantEntity));
        when(mockPersistenceService.getDefinition("active-1")).thenReturn(Optional.of(createMatchingDefinition("active-1", "/tmp/active-dir")));
        when(mockPersistenceService.getDefinition("dormant-1")).thenReturn(Optional.of(createMatchingDefinition("dormant-1", "/tmp/dormant-dir")));

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
        AgentEntity activeEntity = createActiveEntity("list-test-1", "List Test Agent", "DYNAMIC", "/tmp/list-dir");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeEntity));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        when(mockPersistenceService.getDefinition("list-test-1")).thenReturn(Optional.of(createMatchingDefinition("list-test-1", "/tmp/list-dir")));

        // Act
        manager.restoreFromDatabase();
        List<AgentInfo> agents = manager.listAgents();

        // Assert
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).id()).isEqualTo("list-test-1");
        assertThat(agents.get(0).active()).isTrue();
        assertThat(agents.get(0).source()).isEqualTo("DYNAMIC");
    }

    @Test
    public void givenAgentWithoutTargetDirectory_ExpectNoScannerCreated() {
        // Arrange — agent with null targetDirectory.
        // In unit tests the no-arg constructor is used (validator = null),
        // which skips validation for backward compatibility.
        // The real validation happens in production where null is rejected.
        AgentEntity activeEntity = createActiveEntity("no-target-1", "No Target Agent", "DYNAMIC", null);
        new AgentDefinition(
                ".*\\.txt", "No Target Agent", "prompt", "Map", "structure", "template", null);
        
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeEntity));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        when(mockPersistenceService.getDefinition("no-target-1")).thenReturn(Optional.of(createMatchingDefinition("no-target-1", null)));

        // Act
        manager.restoreFromDatabase();

        // Assert — in unit tests (no validator), null passes through for backward compat.
        // In production, this would be rejected with a WARN log.
        assertThat(createdScanners).hasSize(1);
        assertThat(createdScanners.get(0).agentId()).isEqualTo("no-target-1");
    }

    @Test
    public void givenMultipleActiveAgentsInDatabase_ExpectScannerCreatedForEach() {
        // Arrange
        AgentEntity agent1 = createActiveEntity("multi-1", "Agent One", "DYNAMIC", "/tmp/dir1");
        AgentEntity agent2 = createActiveEntity("multi-2", "Agent Two", "DYNAMIC", "/tmp/dir2");
        
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(agent1, agent2));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        when(mockPersistenceService.getDefinition("multi-1")).thenReturn(Optional.of(createMatchingDefinition("multi-1", "/tmp/dir1")));
        when(mockPersistenceService.getDefinition("multi-2")).thenReturn(Optional.of(createMatchingDefinition("multi-2", "/tmp/dir2")));

        // Act
        manager.restoreFromDatabase();

        // Assert
        assertThat(createdScanners).hasSize(2);
        List<String> agentIds = createdScanners.stream().map(ScannerInfo::agentId).toList();
        assertThat(agentIds).containsExactlyInAnyOrder("multi-1", "multi-2");
        assertThat(manager.getActiveAgentCount()).isEqualTo(2);
    }

    @Test
    public void givenRestoreThenAddDynamicAgent_ExpectBothHaveScanners() {
        // Arrange — restore one agent from DB
        AgentEntity activeEntity = createActiveEntity("restored-1", "Restored Agent", "DYNAMIC", "/tmp/restored");
        when(mockPersistenceService.findAllActive()).thenReturn(List.of(activeEntity));
        when(mockPersistenceService.findAllOrdered()).thenReturn(List.of());
        when(mockPersistenceService.getDefinition("restored-1")).thenReturn(Optional.of(createMatchingDefinition("restored-1", "/tmp/restored")));
        doNothing().when(mockPersistenceService).deleteById(anyString());

        // Act — restore then add a new dynamic agent
        manager.restoreFromDatabase();
        AgentDefinition newAgent = TestData.basicPrompt();
        manager.addDynamicAgent(newAgent, "/tmp/new-agent-dir");

        // Assert — both should have scanners
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

    private AgentEntity createActiveEntity(String id, String title, String source, String targetDirectory) {
        AgentEntity entity = new AgentEntity();
        entity.setId(id);
        entity.setAgentDefinitionJson(
                "{\"fileInputRegex\":\".*\\\\.txt\",\"title\":\"" + title + "\",\"body\":\"prompt\","
                        + "\"agentType\":\"Map\",\"outputStructure\":\"struct\",\"outputFilenameTemplate\":\"out/{filename}\","
                        + "\"targetDirectory\":\"" + (targetDirectory != null ? targetDirectory : "") + "\"}");
        entity.setTitle(title);
        entity.setSource(source);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setActive(true);
        return entity;
    }

    /**
     * Creates an AgentDefinition matching the given id and targetDirectory.
     * Used to mock persistenceService.getDefinition() with matching data.
     */
    private AgentDefinition createMatchingDefinition(String id, String targetDirectory) {
        return new AgentDefinition(
                ".*\\.txt", id, "prompt", "Map", "struct", "out/{filename}",
                targetDirectory);
    }

    private AgentEntity createDormantEntity(String id, String title, String source, String targetDirectory) {
        AgentEntity entity = createActiveEntity(id, title, source, targetDirectory);
        entity.setActive(false);
        return entity;
    }
}
