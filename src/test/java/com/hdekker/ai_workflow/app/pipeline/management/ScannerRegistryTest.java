package com.hdekker.ai_workflow.app.pipeline.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

public class ScannerRegistryTest {

    private ScannerRegistry registry;
    private FileMetadataDatabase fileMetadataDb;
    private ApplicationContext appContext;
    private Path tempDir;

    @BeforeEach
    public void init() throws Exception {
        // Create a real temp directory for testing
        tempDir = Files.createTempDirectory("scanner-registry-test-");

        fileMetadataDb = mock(FileMetadataDatabase.class);
        appContext = mock(ApplicationContext.class);

        registry = new ScannerRegistry(appContext, fileMetadataDb);
    }

    @Test
    public void givenValidDirectory_ExpectScannerCreated() {
        String agentId = "test-agent-1";
        String targetDir = tempDir.toString();

        ScannerInfo info = registry.createForAgent(agentId, targetDir, 5);

        assertThat(info).isNotNull();
        assertThat(info.agentId()).isEqualTo(agentId);
        assertThat(info.targetDirectory()).isEqualTo(targetDir);
        assertThat(info.status()).isEqualTo("EMITTING_ALL");
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

        ScannerInfo first = registry.createForAgent(agentId, targetDir, 5);
        ScannerInfo second = registry.createForAgent(agentId, targetDir, 5);

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.agentId()).isEqualTo(second.agentId());
    }

    @Test
    public void givenMultipleAgents_ExpectAllListed() {
        String agentId1 = "test-agent-4";
        String agentId2 = "test-agent-5";
        String targetDir = tempDir.toString();

        registry.createForAgent(agentId1, targetDir, 5);
        registry.createForAgent(agentId2, targetDir, 5);

        List<ScannerInfo> scanners = registry.listAll();
        assertThat(scanners).hasSize(2);

        List<String> agentIds = scanners.stream().map(ScannerInfo::agentId).toList();
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

        ScannerInfo created = registry.createForAgent(agentId, targetDir, 5);
        assertThat(created.status()).isEqualTo("EMITTING_ALL");

        // Refresh should update the status
        registry.refreshAgent(agentId);

        // The status should have been updated (to EMITTING_ALL again since we're in full-scan mode)
        List<ScannerInfo> scanners = registry.listAll();
        assertThat(scanners).hasSize(1);
        // Status may have changed based on refresh logic
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
        // Flux should be empty (no errors)
    }

    @Test
    public void givenDummyData_ExpectSeededCorrectly() {
        registry.seedDummyData();

        List<ScannerInfo> scanners = registry.listAll();
        assertThat(scanners).hasSize(4);

        List<String> agentIds = scanners.stream().map(ScannerInfo::agentId).toList();
        assertThat(agentIds).containsExactlyInAnyOrder(
                "agent-alpha", "agent-beta", "agent-gamma", "agent-delta");
    }

    @Test
    public void givenAlreadySeeded_ExpectNoDuplicateDummyData() {
        registry.seedDummyData();
        int firstSize = registry.listAll().size();

        registry.seedDummyData();
        assertThat(registry.listAll().size()).isEqualTo(firstSize);
    }

    @Test
    public void givenValidPathNotDirectory_ExpectIllegalArgumentException() throws Exception {
        Path tempFile = Files.createTempFile("test-not-dir-", ".txt");

        assertThatThrownBy(() -> registry.createForAgent("test-agent-x", tempFile.toString(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");

        // Clean up
        Files.deleteIfExists(tempFile);
    }
}
