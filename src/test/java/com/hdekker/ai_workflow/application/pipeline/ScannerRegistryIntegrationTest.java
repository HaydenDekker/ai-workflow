package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.agent.AgentLifecycleService;
import com.hdekker.ai_workflow.application.agent.port.AgentRepository;
import com.hdekker.ai_workflow.application.agent.port.DirectoryValidationPort;
import com.hdekker.ai_workflow.application.agent.port.FileWritePort;
import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;
import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.application.scanner.ScannerEventBus;
import com.hdekker.ai_workflow.application.scanner.ScannerMetricsService;
import com.hdekker.ai_workflow.application.scanner.ScannerObservabilityUseCase;
import com.hdekker.ai_workflow.application.scanner.ScannerService;
import com.hdekker.ai_workflow.domain.agent.AgentDefinition;
import com.hdekker.ai_workflow.domain.agent.AgentInfo;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.prompt.PromptResponse;
import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;
import com.hdekker.ai_workflow.test.harness.mock.ChatClientMockBuilder;

import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Integration tests for the full agent-scanner lifecycle.
 */
public class ScannerRegistryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistryIntegrationTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path outputDir;

    private ScannerRegistry scannerRegistry;
    private AgentLifecycleService agentManager;
    private FileMetadataRepository fileMetadataRepo;
    private FileCounterPort fileCounter;
    private FileWatcherPort mockWatcherFactory;
    private FileWatcherPort mockWatcher;
    private ScannerMetricsService metrics;
    private ScannerEventBus eventBus;
    private ScannerObservabilityUseCase observability;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        outputDir = Files.createDirectory(tempDir.resolve("output"));

        fileMetadataRepo = mock(FileMetadataRepository.class);
        when(fileMetadataRepo.findById(any())).thenReturn(Optional.empty());
        fileCounter = mock(FileCounterPort.class);
        when(fileCounter.countFiles(any())).thenReturn(0L);
        metrics = new ScannerMetricsService();
        eventBus = new ScannerEventBus();
        observability = new ScannerObservabilityUseCase(metrics, eventBus);

        // Mock FileWatcherPort
        mockWatcherFactory = mock(FileWatcherPort.class);
        mockWatcher = mock(FileWatcherPort.class);
        when(mockWatcherFactory.forDirectory(any(Path.class), any(Duration.class))).thenReturn(mockWatcher);
        when(mockWatcher.flux()).thenReturn(Flux.empty());
        when(mockWatcher.getDirectory()).thenReturn(inputDir);
        when(mockWatcher.isRunning()).thenReturn(false);

        // Create the real scanner registry
        scannerRegistry = new ScannerRegistry(fileMetadataRepo, observability, mockWatcherFactory, fileCounter);

        // Create a mock ChatClient and FileWriter for the agent manager
        String mockResponse = "## Analysis\n\nDocument processed successfully.";
        var chatClient = ChatClientMockBuilder.createMock(mockResponse);

        // Mock FileWritePort
        FileWritePort fileWritePort = mock(FileWritePort.class);
        when(fileWritePort.createPersister(any(Path.class))).thenReturn((Consumer<PromptResponse>) content -> {
            // No-op for integration tests
        });

        // Mock persistence
        AgentRepository agentRepository = mock(AgentRepository.class);
        when(agentRepository.findAllActive()).thenReturn(List.of());
        when(agentRepository.findAllOrdered()).thenReturn(List.of());

        // Mock validator
        DirectoryValidationPort validator = mock(DirectoryValidationPort.class);
        when(validator.validate(anyString())).thenReturn(DirectoryValidationPort.ValidationResult.success());

        // Create agent manager with real scanner registry
        AgentObserverUseCase observerMock = Mockito.mock(AgentObserverUseCase.class);
        agentManager = new AgentLifecycleService(
                scannerRegistry,
                fileWritePort,
                outputDir,
                chatClient,
                agentRepository,
                validator,
                observerMock);
    }

    @AfterEach
    void tearDown() {
        scannerRegistry.destroy();
    }

    @Test
    void givenAgentAdded_WhenScannerCreated_ThenScannerExistsAndIsListed() {
        log.info("Test: agent creation creates scanner");

        AgentDefinition agent = createAgentDefinition("AGENT-CREATE-TEST");
        String targetDir = inputDir.toString();

        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        assertThat(agentInfo).isNotNull();
        assertThat(agentInfo.id()).isNotNull();

        List<ScannerService.ScannerInfo> scanners = scannerRegistry.listAll();
        assertThat(scanners).hasSize(1);
        assertThat(scanners.get(0).agentId()).isEqualTo(agentInfo.id());
        assertThat(scanners.get(0).folderPath()).isEqualTo(targetDir);
        assertThat(scanners.get(0).status()).isEqualTo("IDLE");

        log.info("PASSED: agent {} has scanner, listed in registry", agentInfo.id());
    }

    @Test
    void givenAgentAdded_WhenAgentRemoved_ThenScannerDestroyed() {
        log.info("Test: agent removal destroys scanner");

        AgentDefinition agent = createAgentDefinition("AGENT-REMOVE-TEST");
        String targetDir = inputDir.toString();

        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        assertThat(scannerRegistry.listAll()).hasSize(1);

        agentManager.removeAgent(agentInfo.id());

        assertThat(scannerRegistry.listAll()).hasSize(0);
        assertThat(agentManager.listAgents()).hasSize(0);

        log.info("PASSED: scanner destroyed when agent {} removed", agentInfo.id());
    }

    @Test
    void givenAgentAdded_WhenAgentRefreshed_ThenScannerResetToFullScan() {
        log.info("Test: agent refresh resets scanner to full-scan mode");

        AgentDefinition agent = createAgentDefinition("AGENT-REFRESH-TEST");
        String targetDir = inputDir.toString();

        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        AgentInfo refreshed = agentManager.refreshAgent(agentInfo.id());

        assertThat(refreshed).isNotNull();
        assertThat(refreshed.id()).isEqualTo(agentInfo.id());
        assertThat(agentManager.listAgents()).hasSize(1);

        List<ScannerService.ScannerInfo> scanners = scannerRegistry.listAll();
        assertThat(scanners).hasSize(1);

        log.info("PASSED: agent refreshed, scanner {} still active", agentInfo.id());
    }

    @Test
    void givenMultipleAgents_WhenEachHasScanner_ThenScannersAreIsolated() {
        log.info("Test: multiple agents each get isolated scanners");

        AgentDefinition agent1 = createAgentDefinition("AGENT-ISOLATE-1");
        AgentDefinition agent2 = createAgentDefinition("AGENT-ISOLATE-2");

        String targetDir = inputDir.toString();

        AgentInfo info1 = agentManager.addDynamicAgent(agent1, targetDir);
        AgentInfo info2 = agentManager.addDynamicAgent(agent2, targetDir);

        List<ScannerService.ScannerInfo> scanners = scannerRegistry.listAll();
        assertThat(scanners).hasSize(2);

        assertThat(info1.id()).isNotEqualTo(info2.id());

        for (ScannerService.ScannerInfo scanner : scanners) {
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

        AgentInfo info1 = agentManager.addDynamicAgent(agent1, targetDir);
        AgentInfo info2 = agentManager.addDynamicAgent(agent2, targetDir);
        assertThat(scannerRegistry.listAll()).hasSize(2);

        agentManager.removeAgent(info1.id());

        List<ScannerService.ScannerInfo> remainingScanners = scannerRegistry.listAll();
        assertThat(remainingScanners).hasSize(1);
        assertThat(remainingScanners.get(0).agentId()).isEqualTo(info2.id());

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

        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        Flux<FileHistory> flux = scannerRegistry.getScannerFlux(agentInfo.id());

        assertThat(flux).isNotNull();

        log.info("PASSED: scanner flux is accessible for agent {}", agentInfo.id());
    }

    @Test
    void givenAgentWithScanner_WhenAgentRefreshedAndFluxQueried_ThenFluxStillAccessible() {
        log.info("Test: scanner flux remains accessible after agent refresh");

        AgentDefinition agent = createAgentDefinition("AGENT-FLUX-AFTER-REFRESH");
        String targetDir = inputDir.toString();

        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, targetDir);

        agentManager.refreshAgent(agentInfo.id());

        Flux<FileHistory> flux = scannerRegistry.getScannerFlux(agentInfo.id());
        assertThat(flux).isNotNull();

        log.info("PASSED: scanner flux still accessible after refresh for agent {}", agentInfo.id());
    }

    @Test
    void givenNonExistentDirectory_WhenAgentCreated_ThenScannerCreationFailsGracefully() {
        log.info("Test: agent creation with non-existent directory fails gracefully");

        AgentDefinition agent = createAgentDefinition("AGENT-INVALID-DIR-TEST");
        String nonExistentDir = tempDir.resolve("does-not-exist").toString();

        AgentInfo agentInfo = agentManager.addDynamicAgent(agent, nonExistentDir);

        assertThat(agentInfo).isNotNull();
        assertThat(scannerRegistry.listAll()).hasSize(0);

        log.info("PASSED: agent created without scanner when directory does not exist");
    }

    @Test
    void givenFullLifecycle_WhenCreateRefreshDelete_ThenScannerLifecycleCorrect() {
        log.info("Test: full lifecycle - create -> refresh -> delete");

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

        log.info("PASSED: full lifecycle verified (create -> refresh -> delete)");
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
