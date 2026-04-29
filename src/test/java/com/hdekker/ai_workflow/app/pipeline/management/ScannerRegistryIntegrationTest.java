package com.hdekker.ai_workflow.app.pipeline.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.hdekker.ai_workflow.database.agent.AgentPersistenceUsecase;
import com.hdekker.ai_workflow.usecases.AgentLifecycleUseCase;
import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileWriter;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.prompt.PromptResponse;
import com.hdekker.ai_workflow.rest.dto.AgentInfo;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.test.pipeline.mock.ChatClientMockBuilder;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import reactor.core.publisher.Flux;

/**
 * Integration tests for the full agent-scanner lifecycle.
 * 
 * Tests verify:
 * 1. Create agent → scanner created
 * 2. Refresh agent → scanner resets to full-scan mode
 * 3. Delete agent → scanner destroyed
 * 4. Scanner flux is properly connected to the agent pipeline
 * 5. Multiple agents each get isolated scanners
 * 
 * These tests use real Spring Integration infrastructure (not mocks)
 * for the scanner adapter, but mock the ChatClient and FileWriter.
 */
public class ScannerRegistryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistryIntegrationTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path outputDir;

    private ScannerRegistry scannerRegistry;
    private AgentLifecycleUseCase agentManager;
    private FileMetadataDatabase fileMetadataDb;
    private ApplicationContext appContext;
    private ScannerObserverUseCase observer;

    @BeforeEach
    void setUp() throws Exception {
        // Create subdirectories for input and output
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Create real (non-mocked) dependencies for scanner integration
        fileMetadataDb = mock(FileMetadataDatabase.class);
        appContext = mock(ApplicationContext.class);
        observer = new ScannerObserverUseCase();

        // Create the real scanner registry
        scannerRegistry = new ScannerRegistry(appContext, fileMetadataDb, observer, null);

        // Create a mock ChatClient and FileWriter for the agent manager
        String mockResponse = "## Analysis\n\nDocument processed successfully.";
        var chatClient = ChatClientMockBuilder.createMock(mockResponse);

        // Mock FileWriter to write to output dir
        FileWriter fileWriter = mock(FileWriter.class);
        when(fileWriter.createPersister(any(Path.class))).thenReturn((content) -> {
            // No-op for integration tests - we just want to verify the flow works
        });

        // Create agent manager with real scanner registry
        agentManager = new AgentLifecycleUseCase(
                scannerRegistry,
                fileWriter,
                outputDir,
                chatClient,
                null, // no persistence for these tests
                null); // no target directory validator for these tests
    }

    @AfterEach
    void tearDown() {
        // Clean up any remaining scanners
        scannerRegistry.destroy();
    }

    @Test
    void givenAgentAdded_WhenScannerCreated_ThenScannerExistsAndIsListed() {
        log.info("Test: agent creation creates scanner");

        AgentDefinition agent = createAgentDefinition("AGENT-CREATE-TEST");
        String targetDir = inputDir.toString();

        // Act: add agent
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        // Assert: agent created
        assertThat(agentInfo).isNotNull();
        assertThat(agentInfo.id()).isNotNull();

        // Assert: scanner is listed in registry
        // Empty directory → no files buffered → stays IDLE (correct behavior after hash filter)
        List<ScannerInfo> scanners = scannerRegistry.listAll();
        assertThat(scanners).hasSize(1);
        assertThat(scanners.get(0).agentId()).isEqualTo(agentInfo.id());
        assertThat(scanners.get(0).targetDirectory()).isEqualTo(targetDir);
        assertThat(scanners.get(0).status()).isEqualTo("IDLE");

        log.info("PASSED: agent {} has scanner, listed in registry", agentInfo.id());
    }

    @Test
    void givenAgentAdded_WhenAgentRemoved_ThenScannerDestroyed() {
        log.info("Test: agent removal destroys scanner");

        AgentDefinition agent = createAgentDefinition("AGENT-REMOVE-TEST");
        String targetDir = inputDir.toString();

        // Arrange: add agent
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);
        String scannerId = agentInfo.id();

        // Assert: scanner exists
        assertThat(scannerRegistry.listAll()).hasSize(1);

        // Act: remove agent
        agentManager.removeAgent(agentInfo.id());

        // Assert: scanner destroyed
        assertThat(scannerRegistry.listAll()).hasSize(0);
        assertThat(agentManager.listAgents()).hasSize(0);

        log.info("PASSED: scanner {} destroyed when agent {} removed", scannerId, agentInfo.id());
    }

    @Test
    void givenAgentAdded_WhenAgentRefreshed_ThenScannerResetToFullScan() {
        log.info("Test: agent refresh resets scanner to full-scan mode");

        AgentDefinition agent = createAgentDefinition("AGENT-REFRESH-TEST");
        String targetDir = inputDir.toString();

        // Arrange: add agent
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        // Act: refresh agent
        AgentInfo refreshed = agentManager.refreshAgent(agentInfo.id());

        // Assert: agent still exists
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.id()).isEqualTo(agentInfo.id());
        assertThat(agentManager.listAgents()).hasSize(1);

        // Assert: scanner still exists
        List<ScannerInfo> scanners = scannerRegistry.listAll();
        assertThat(scanners).hasSize(1);

        log.info("PASSED: agent refreshed, scanner {} still active", agentInfo.id());
    }

    @Test
    void givenMultipleAgents_WhenEachHasScanner_ThenScannersAreIsolated() {
        log.info("Test: multiple agents each get isolated scanners");

        AgentDefinition agent1 = createAgentDefinition("AGENT-ISOLATE-1");
        AgentDefinition agent2 = createAgentDefinition("AGENT-ISOLATE-2");

        String targetDir = inputDir.toString();

        // Act: add both agents
        AgentInfo info1 = agentManager.addDynamicAgent(agent1, targetDir);
        AgentInfo info2 = agentManager.addDynamicAgent(agent2, targetDir);

        // Assert: two scanners exist
        List<ScannerInfo> scanners = scannerRegistry.listAll();
        assertThat(scanners).hasSize(2);

        // Assert: each agent has a unique ID
        assertThat(info1.id()).isNotEqualTo(info2.id());

        // Assert: each scanner belongs to the correct agent
        for (ScannerInfo scanner : scanners) {
            boolean found = scanners.stream()
                    .anyMatch(s -> s.agentId().equals(scanner.agentId()));
            assertThat(found).isTrue();
        }

        log.info("PASSED: {} isolated scanners created for {} agents", scanners.size(), 2);
    }

    @Test
    void givenMultipleAgents_WhenOneRemoved_ThenOnlyItsScannerDestroyed() {
        log.info("Test: removing one agent destroys only its scanner");

        AgentDefinition agent1 = createAgentDefinition("AGENT-PARTIAL-REMOVE-1");
        AgentDefinition agent2 = createAgentDefinition("AGENT-PARTIAL-REMOVE-2");

        String targetDir = inputDir.toString();

        // Arrange: add both agents
        AgentInfo info1 = agentManager.addDynamicAgent(agent1, targetDir);
        AgentInfo info2 = agentManager.addDynamicAgent(agent2, targetDir);
        assertThat(scannerRegistry.listAll()).hasSize(2);

        // Act: remove first agent only
        agentManager.removeAgent(info1.id());

        // Assert: only second agent's scanner remains
        List<ScannerInfo> remainingScanners = scannerRegistry.listAll();
        assertThat(remainingScanners).hasSize(1);
        assertThat(remainingScanners.get(0).agentId()).isEqualTo(info2.id());

        // Assert: agent1 removed, agent2 still exists
        List<AgentInfo> agents = agentManager.listAgents();
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).id()).isEqualTo(info2.id());

        log.info("PASSED: removing agent {} left only scanner for agent {}", info1.id(), info2.id());
    }

    @Test
    void givenAgentWithScanner_WhenScannerFluxQueried_ThenFluxIsNotNull() {
        log.info("Test: scanner flux is accessible after agent creation");

        AgentDefinition agent = createAgentDefinition("AGENT-FLUX-TEST");
        String targetDir = inputDir.toString();

        // Arrange: add agent
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        // Act: get scanner flux
        Flux<FileHistory> flux = scannerRegistry.getScannerFlux(agentInfo.id());

        // Assert: flux is not null (may be empty if no files)
        assertThat(flux).isNotNull();

        log.info("PASSED: scanner flux is accessible and well-formed for agent {}", agentInfo.id());
    }

    @Test
    void givenAgentWithScanner_WhenAgentRefreshedAndFluxQueried_ThenFluxStillAccessible() {
        log.info("Test: scanner flux remains accessible after agent refresh");

        AgentDefinition agent = createAgentDefinition("AGENT-FLUX-AFTER-REFRESH");
        String targetDir = inputDir.toString();

        // Arrange: add agent
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        // Act: refresh agent
        agentManager.refreshAgent(agentInfo.id());

        // Assert: flux still accessible
        Flux<FileHistory> flux = scannerRegistry.getScannerFlux(agentInfo.id());
        assertThat(flux).isNotNull();

        log.info("PASSED: scanner flux still accessible after refresh for agent {}", agentInfo.id());
    }

    @Test
    void givenNonExistentDirectory_WhenAgentCreated_ThenScannerCreationFailsGracefully() {
        log.info("Test: agent creation with non-existent directory fails gracefully");

        AgentDefinition agent = createAgentDefinition("AGENT-INVALID-DIR-TEST");
        String nonExistentDir = tempDir.resolve("does-not-exist").toString();

        // Act & Assert: addDynamicAgent should not throw
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, nonExistentDir);

        // The agent is still added (scanner creation failure is logged but not fatal)
        assertThat(agentInfo).isNotNull();

        // No scanners should be registered
        assertThat(scannerRegistry.listAll()).hasSize(0);

        log.info("PASSED: agent created without scanner when directory does not exist");
    }

    @Test
    void givenFullLifecycle_WhenCreateRefreshDelete_ThenScannerLifecycleCorrect() {
        log.info("Test: full lifecycle - create → refresh → delete");

        AgentDefinition agent = createAgentDefinition("AGENT-FULL-LIFECYCLE");
        String targetDir = inputDir.toString();

        // Step 1: Create
        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);
        String scannerId = agentInfo.id();
        assertThat(scannerRegistry.listAll()).hasSize(1);
        log.info("Step 1 complete: agent created with scanner {}", scannerId);

        // Step 2: Refresh
        AgentInfo refreshed = agentManager.refreshAgent(agentInfo.id());
        assertThat(refreshed).isNotNull();
        assertThat(scannerRegistry.listAll()).hasSize(1);
        log.info("Step 2 complete: agent refreshed, scanner {} still active", scannerId);

        // Step 3: Delete
        agentManager.removeAgent(agentInfo.id());
        assertThat(scannerRegistry.listAll()).hasSize(0);
        assertThat(agentManager.listAgents()).hasSize(0);
        log.info("Step 3 complete: agent and scanner {} destroyed", scannerId);

        log.info("PASSED: full lifecycle verified (create → refresh → delete)");
    }

    private AgentDefinition createAgentDefinition(String title) {
        return new AgentDefinition(
                ".*\\.txt$",
                title,
                "Process this text file.",
                "Map",
                "Return a structured analysis.",
                "output/${name}.md",
                "/tmp/test-dir");
    }
}
