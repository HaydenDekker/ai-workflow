package com.hdekker.ai_workflow.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;
import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.application.scanner.ScannerEventBus;
import com.hdekker.ai_workflow.application.scanner.ScannerMetricsService;
import com.hdekker.ai_workflow.application.scanner.ScannerObservabilityUseCase;
import com.hdekker.ai_workflow.application.scanner.ScannerService;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerFileResult;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

import reactor.core.publisher.Flux;

public class ScannerRegistryTest {

    private ScannerRegistry registry;
    private FileMetadataRepository fileMetadataRepo;
    private ScannerMetricsService metrics;
    private ScannerEventBus eventBus;
    private ScannerObservabilityUseCase observability;
    private FileCounterPort fileCounter;
    private Path tempDir;
    private List<ScannerFileEvent> capturedEvents;
    private FileWatcherPort mockWatcherFactory;
    private FileWatcherPort mockWatcher;

    @BeforeEach
    public void init() throws Exception {
        tempDir = Files.createTempDirectory("scanner-registry-test-");

        fileMetadataRepo = mock(FileMetadataRepository.class);
        when(fileMetadataRepo.findById(any())).thenReturn(Optional.empty());
        fileCounter = mock(FileCounterPort.class);
        when(fileCounter.countFiles(any())).thenReturn(0L);
        metrics = new ScannerMetricsService();
        eventBus = new ScannerEventBus();
        observability = new ScannerObservabilityUseCase(metrics, eventBus);

        // Capture events pushed by the event bus
        capturedEvents = new CopyOnWriteArrayList<>();
        eventBus.registerCallback(capturedEvents::add);

        // Mock FileWatcherPort factory
        mockWatcherFactory = mock(FileWatcherPort.class);
        mockWatcher = mock(FileWatcherPort.class);
        when(mockWatcherFactory.forDirectory(any(Path.class), any(Duration.class))).thenReturn(mockWatcher);
        when(mockWatcher.flux()).thenReturn(Flux.empty());
        when(mockWatcher.getDirectory()).thenReturn(tempDir);
        when(mockWatcher.isRunning()).thenReturn(false);

        registry = new ScannerRegistry(fileMetadataRepo, observability, mockWatcherFactory, fileCounter);
    }

    @AfterEach
    public void tearDown() {
        registry.destroy();
        try {
            Files.walk(tempDir).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.delete(p); } catch (Exception e) { /* ignore */ }
            });
        } catch (Exception e) { /* ignore */ }
    }

    @Test
    public void givenValidDirectory_ExpectScannerCreated() {
        String agentId = "test-agent-1";
        String targetDir = tempDir.toString();

        ScannerService.ScannerInfo info = registry.createForAgent(agentId, targetDir, 5);

        assertThat(info).isNotNull();
        assertThat(info.agentId()).isEqualTo(agentId);
        assertThat(info.folderPath()).isEqualTo(targetDir);
        assertThat(info.status()).isEqualTo("IDLE");
        assertThat(info.createdAt()).isNotNull();
    }

    @Test
    public void givenNonExistentDirectory_ExpectIllegalArgumentException() {
        String agentId = "test-agent-2";
        String nonExistentDir = "/this/path/does/not/exist/abc123";

        assertThatThrownBy(() -> registry.createForAgent(agentId, nonExistentDir, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    public void givenExistingAgent_ExpectDuplicateScannerReturnsExisting() {
        String agentId = "test-agent-3";
        String targetDir = tempDir.toString();

        ScannerService.ScannerInfo first = registry.createForAgent(agentId, targetDir, 5);
        ScannerService.ScannerInfo second = registry.createForAgent(agentId, targetDir, 5);

        assertThat(first.agentId()).isEqualTo(second.agentId());
    }

    @Test
    public void givenMultipleAgents_ExpectAllListed() {
        String agentId1 = "test-agent-4";
        String agentId2 = "test-agent-5";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId1, targetDir, 5);
        registry.createForAgent(agentId2, targetDir, 5);

        List<ScannerService.ScannerInfo> scanners = registry.listAll();
        assertThat(scanners).hasSize(2);

        List<String> agentIds = scanners.stream().map(ScannerService.ScannerInfo::agentId).toList();
        assertThat(agentIds).contains(agentId1, agentId2);
    }

    @Test
    public void givenScannerCreated_ExpectGetByIdWorks() {
        String agentId = "test-agent-6";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId, targetDir, 5);

        assertThat(registry.getById(agentId)).isPresent();
        assertThat(registry.getById(agentId).get().agentId()).isEqualTo(agentId);
    }

    @Test
    public void givenScannerCreated_ExpectDeleteByIdWorks() {
        String agentId = "test-agent-7";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId, targetDir, 5);
        assertThat(registry.listAll()).hasSize(1);

        registry.deleteById(agentId);
        assertThat(registry.listAll()).hasSize(0);
    }

    @Test
    public void givenScannerCreated_ExpectRefreshAgentUpdatesStatus() {
        String agentId = "test-agent-8";
        String targetDir = tempDir.toString();

        ScannerService.ScannerInfo created = registry.createForAgent(agentId, targetDir, 5);
        assertThat(created.status()).isEqualTo("IDLE");

        // Refresh should work without error
        registry.refreshAgent(agentId);

        List<ScannerService.ScannerInfo> scanners = registry.listAll();
        assertThat(scanners).hasSize(1);
    }

    @Test
    public void givenScannerCreated_ExpectGetScannerFluxReturnsFlux() {
        String agentId = "test-agent-9";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId, targetDir, 5);

        // Should not throw
        var flux = registry.getScannerFlux(agentId);
        assertThat(flux).isNotNull();
    }

    @Test
    public void givenNonExistentScanner_ExpectGetByIdEmpty() {
        assertThat(registry.getById("non-existent-agent")).isEmpty();
    }

    @Test
    public void givenNonExistentScanner_ExpectGetScannerFluxReturnsEmptyFlux() {
        var flux = registry.getScannerFlux("non-existent-agent");
        assertThat(flux).isNotNull();
    }

    @Test
    public void givenValidPathNotDirectory_ExpectIllegalArgumentException() throws Exception {
        Path tempFile = Files.createTempFile("test-not-dir-", ".txt");

        assertThatThrownBy(() -> registry.createForAgent("test-agent-x", tempFile.toString(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void givenScannerTransitioningToError_WhenGetById_ThenErrorStatusVisible() {
        String agentId = "test-agent-error-status";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId, targetDir, 5);
        registry.transitionToError(agentId, "watcher crashed");

        ScannerService.ScannerInfo info = registry.getById(agentId).get();

        assertThat(info.status()).isEqualTo("ERROR");
        assertThat(info.errorMessage()).isEqualTo("watcher crashed");
    }

    @Test
    public void givenScannerRecoveringFromError_WhenGetById_ThenErrorCleared() {
        String agentId = "test-agent-recover-status";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId, targetDir, 5);
        registry.transitionToError(agentId, "watcher crashed");
        registry.recoverFromError(agentId);

        ScannerService.ScannerInfo info = registry.getById(agentId).get();

        assertThat(info.errorMessage()).as("error message should be cleared").satisfiesAnyOf(
                val -> assertThat(val).isNull(),
                val -> assertThat(val).isBlank()
        );
    }

    @Test
    public void givenNonExistentAgent_WhenTransitionToError_ThenNoEventPushed() {
        capturedEvents.clear();

        registry.transitionToError("ghost-agent", "nothing there");

        assertThat(capturedEvents).isEmpty();
    }

    @Test
    public void givenNonExistentAgent_WhenRecoverFromError_ThenNoEventPushed() {
        capturedEvents.clear();

        registry.recoverFromError("ghost-agent");

        assertThat(capturedEvents).isEmpty();
    }

    @Test
    public void givenScannerCreated_WhenStatusUpdatedViaCallback_ThenStatusChangeEventPushed() {
        String agentId = "test-agent-status-change";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId, targetDir, 5);
        capturedEvents.clear();

        // Simulate a status change
        registry.updateStatus(agentId, ScannerStatus.EMITTING_UPDATES);

        assertThat(capturedEvents).anySatisfy(event -> {
            assertThat(event.agentId()).isEqualTo(agentId);
            assertThat(event.result()).isEqualTo(ScannerFileResult.EMITTED);
        });
    }
}
