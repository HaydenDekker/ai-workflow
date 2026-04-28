package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for {@link FileSystemScannerAdapter} metrics instrumentation.
 * <p>
 * Verifies that:
 * 1. files_discovered counter increments on file discovery
 * 2. files_unchanged counter increments on unchanged files
 * 3. file_count gauge updates after scan completes
 */
public class FileSystemScannerAdapterMetricsTest {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScannerAdapterMetricsTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private FileMetadataDatabase fileMetadataDatabase;
    private SimpleMeterRegistry meterRegistry;
    private FileSystemScannerAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        fileMetadataDatabase = mock(FileMetadataDatabase.class);
        meterRegistry = new SimpleMeterRegistry();
        // Store metadata in a map for FileComparator to find during reset scans
        java.util.Map<String, FileMetadata> metadataStore = new java.util.concurrent.ConcurrentHashMap<>();
        doAnswer(invocation -> {
            FileMetadata fm = invocation.getArgument(0);
            metadataStore.put(fm.url(), fm);
            return null;
        }).when(fileMetadataDatabase).save(any(FileMetadata.class));
        when(fileMetadataDatabase.findById(any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            return java.util.Optional.ofNullable(metadataStore.get(url));
        });
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) {
            adapter.destroy();
        }
    }

    @Test
    void givenEmptyDirectory_WhenAdapterCreated_ThenGaugeStartsAtZero() throws Exception {
        log.info("Test: gauge starts at zero for empty directory");

        adapter = new FileSystemScannerAdapter("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                fileMetadataDatabase,
                meterRegistry, null);

        // Gauge should be 0 for empty directory
        double fileCount = meterRegistry.find("ai_workflow.scanner.file_count")
                .tag("agentId", "test-agent")
                .gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .findFirst()
                .orElse(0.0);

        assertThat(fileCount).isZero();
        log.info("PASSED: gauge starts at zero");
    }

    @Test
    void givenFileInDirectory_WhenAdapterCreated_ThenDiscoveredCounterIncrements() throws Exception {
        log.info("Test: discovered counter increments on file discovery");

        // Create a test file
        Path testFile = inputDir.resolve("test-metrics.txt");
        Files.writeString(testFile, "test content for metrics");

        adapter = new FileSystemScannerAdapter("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                meterRegistry, null);

        // Wait for initial scan to complete
        Thread.sleep(1000);

        // Discovered counter should be 1
        double discovered = meterRegistry.find("ai_workflow.scanner.files_discovered")
                .tag("agentId", "test-agent")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .findFirst()
                .orElse(0.0);

        assertThat(discovered).isEqualTo(1.0);
        log.info("PASSED: discovered counter incremented to {}", (long) discovered);
    }

    @Test
    void givenFileInDirectory_WhenFullScan_ThenUnchangedCounterIncrements() throws Exception {
        log.info("Test: unchanged counter increments on full scan");

        // Create a test file
        Path testFile = inputDir.resolve("test-unchanged.txt");
        Files.writeString(testFile, "test content for unchanged");

        adapter = new FileSystemScannerAdapter("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                meterRegistry, null);

        // Wait for initial scan
        Thread.sleep(1000);

        // Now do a full scan (reset) - file should be unchanged
        adapter.resetToFullScan();
        Thread.sleep(500);

        // Unchanged counter should be 1
        double unchanged = meterRegistry.find("ai_workflow.scanner.files_unchanged")
                .tag("agentId", "test-agent")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .findFirst()
                .orElse(0.0);

        assertThat(unchanged).isGreaterThanOrEqualTo(1.0);
        log.info("PASSED: unchanged counter incremented to {}", (long) unchanged);
    }

    @Test
    void givenFileInDirectory_WhenAdapterCreated_ThenGaugeUpdatesToCorrectCount() throws Exception {
        log.info("Test: gauge reflects correct file count");

        // Create multiple test files
        Files.writeString(inputDir.resolve("file1.txt"), "content 1");
        Files.writeString(inputDir.resolve("file2.txt"), "content 2");
        Files.writeString(inputDir.resolve("file3.txt"), "content 3");

        adapter = new FileSystemScannerAdapter("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                meterRegistry, null);

        // Wait for initial scan to complete
        Thread.sleep(1000);

        // Gauge should reflect 3 files
        double fileCount = meterRegistry.find("ai_workflow.scanner.file_count")
                .tag("agentId", "test-agent")
                .gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .findFirst()
                .orElse(0.0);

        assertThat(fileCount).isEqualTo(3.0);
        log.info("PASSED: gauge updated to {}", (long) fileCount);
    }

    @Test
    void givenMultipleFiles_WhenNewFileAddedAndScanned_ThenDiscoveredIncrements() throws Exception {
        log.info("Test: discovered counter increments for each new file");

        // Create initial files
        Files.writeString(inputDir.resolve("initial.txt"), "initial content");

        adapter = new FileSystemScannerAdapter("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                meterRegistry, null);

        // Wait for initial scan
        Thread.sleep(1000);

        double initialDiscovered = meterRegistry.find("ai_workflow.scanner.files_discovered")
                .tag("agentId", "test-agent")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .findFirst()
                .orElse(0.0);

        // Add a new file and trigger full scan
        Files.writeString(inputDir.resolve("new.txt"), "new content");
        adapter.resetToFullScan();
        Thread.sleep(500);

        double finalDiscovered = meterRegistry.find("ai_workflow.scanner.files_discovered")
                .tag("agentId", "test-agent")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .findFirst()
                .orElse(0.0);

        assertThat(finalDiscovered).isGreaterThan(initialDiscovered);
        log.info("PASSED: discovered counter went from {} to {}", 
                (long) initialDiscovered, (long) finalDiscovered);
    }

    @Test
    void givenMultipleFiles_WhenFileCountGaugeUpdated_ThenReflectsAllFiles() throws Exception {
        log.info("Test: gauge reflects all files after multiple operations");

        // Create initial files
        Files.writeString(inputDir.resolve("file-a.txt"), "content a");
        Files.writeString(inputDir.resolve("file-b.txt"), "content b");

        adapter = new FileSystemScannerAdapter("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                meterRegistry, null);

        // Wait for initial scan
        Thread.sleep(1000);

        // Add more files
        Files.writeString(inputDir.resolve("file-c.txt"), "content c");
        
        // Trigger full scan
        adapter.resetToFullScan();
        Thread.sleep(500);

        // Gauge should reflect 3 files
        double fileCount = meterRegistry.find("ai_workflow.scanner.file_count")
                .tag("agentId", "test-agent")
                .gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .findFirst()
                .orElse(0.0);

        assertThat(fileCount).isEqualTo(3.0);
        log.info("PASSED: gauge reflects all {} files", (long) fileCount);
    }
}
