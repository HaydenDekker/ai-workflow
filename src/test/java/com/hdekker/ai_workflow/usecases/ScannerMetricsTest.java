package com.hdekker.ai_workflow.usecases;

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
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

/**
 * Unit tests for {@link Scanner} metrics instrumentation via {@link ScannerObserverUseCase}.
 * <p>
 * Verifies that:
 * 1. recordDiscovery is called on file discovery
 * 2. recordUnchanged is called on unchanged files
 * 3. updateFileCount is called after scan completes
 */
public class ScannerMetricsTest {

    private static final Logger log = LoggerFactory.getLogger(ScannerMetricsTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private FileMetadataDatabase fileMetadataDatabase;
    private ScannerObserverUseCase observer;
    private Scanner adapter;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        fileMetadataDatabase = mock(FileMetadataDatabase.class);
        observer = new ScannerObserverUseCase();
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
    void givenEmptyDirectory_WhenAdapterCreated_ThenFileCountIsZero() throws Exception {
        log.info("Test: file count is zero for empty directory");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                fileMetadataDatabase,
                observer, null);

        // Metrics should be zero for empty directory
        var snapshot = observer.getMetrics("test-agent");

        assertThat(snapshot.fileCount()).isZero();
        assertThat(snapshot.totalDiscovered()).isZero();
        log.info("PASSED: file count starts at zero");
    }

    @Test
    void givenFileInDirectory_WhenAdapterCreated_ThenDiscoveredCountIncrements() throws Exception {
        log.info("Test: discovered count increments on file discovery");

        // Create a test file
        Path testFile = inputDir.resolve("test-metrics.txt");
        Files.writeString(testFile, "test content for metrics");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                observer, null);

        // Wait for initial scan to complete
        Thread.sleep(1000);

        // Discovered count should be 1
        var snapshot = observer.getMetrics("test-agent");

        assertThat(snapshot.totalDiscovered()).isEqualTo(1);
        log.info("PASSED: discovered count incremented to {}", snapshot.totalDiscovered());
    }

    @Test
    void givenFileInDirectory_WhenFullScan_ThenUnchangedCountIncrements() throws Exception {
        log.info("Test: unchanged count increments on full scan");

        // Create a test file
        Path testFile = inputDir.resolve("test-unchanged.txt");
        Files.writeString(testFile, "test content for unchanged");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                observer, null);

        // Wait for initial scan
        Thread.sleep(1000);

        // Now do a full scan (reset) - file should be unchanged
        adapter.resetToFullScan();
        Thread.sleep(500);

        // Unchanged count should be at least 1
        var snapshot = observer.getMetrics("test-agent");

        assertThat(snapshot.unchanged()).isGreaterThanOrEqualTo(1);
        log.info("PASSED: unchanged count incremented to {}", snapshot.unchanged());
    }

    @Test
    void givenFileInDirectory_WhenAdapterCreated_ThenFileCountUpdates() throws Exception {
        log.info("Test: file count reflects correct count");

        // Create multiple test files
        Files.writeString(inputDir.resolve("file1.txt"), "content 1");
        Files.writeString(inputDir.resolve("file2.txt"), "content 2");
        Files.writeString(inputDir.resolve("file3.txt"), "content 3");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                observer, null);

        // Wait for initial scan to complete
        Thread.sleep(1000);

        // File count should reflect 3 files
        var snapshot = observer.getMetrics("test-agent");

        assertThat(snapshot.fileCount()).isEqualTo(3);
        log.info("PASSED: file count updated to {}", snapshot.fileCount());
    }

    @Test
    void givenMultipleFiles_WhenNewFileAddedAndScanned_ThenDiscoveredIncrements() throws Exception {
        log.info("Test: discovered count increments for each new file");

        // Create initial files
        Files.writeString(inputDir.resolve("initial.txt"), "initial content");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                observer, null);

        // Wait for initial scan
        Thread.sleep(1000);

        long initialDiscovered = observer.getMetrics("test-agent").totalDiscovered();

        // Add a new file and trigger full scan
        Files.writeString(inputDir.resolve("new.txt"), "new content");
        adapter.resetToFullScan();
        Thread.sleep(500);

        long finalDiscovered = observer.getMetrics("test-agent").totalDiscovered();

        assertThat(finalDiscovered).isGreaterThan(initialDiscovered);
        log.info("PASSED: discovered count went from {} to {}", 
                initialDiscovered, finalDiscovered);
    }

    @Test
    void givenMultipleFiles_WhenFileCountUpdated_ThenReflectsAllFiles() throws Exception {
        log.info("Test: file count reflects all files after multiple operations");

        // Create initial files
        Files.writeString(inputDir.resolve("file-a.txt"), "content a");
        Files.writeString(inputDir.resolve("file-b.txt"), "content b");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                observer, null);

        // Wait for initial scan
        Thread.sleep(1000);

        // Add more files
        Files.writeString(inputDir.resolve("file-c.txt"), "content c");
        
        // Trigger full scan
        adapter.resetToFullScan();
        Thread.sleep(500);

        // File count should reflect 3 files
        var snapshot = observer.getMetrics("test-agent");

        assertThat(snapshot.fileCount()).isEqualTo(3);
        log.info("PASSED: file count reflects all {} files", snapshot.fileCount());
    }

    @Test
    void givenAdapterCreated_WhenMetricsEventPublished_ThenEventReceived() throws Exception {
        log.info("Test: metrics events are published correctly");

        CopyOnWriteArrayList<ScannerMetricsChangedEvent> events = new CopyOnWriteArrayList<>();
        observer.registerRefreshCallback(events::add);

        // Create a test file
        Files.writeString(inputDir.resolve("test-event.txt"), "test content");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                fileMetadataDatabase,
                observer, null);

        // Wait for initial scan
        Thread.sleep(1000);

        // Should have received events (file_count, discovered, etc.)
        assertThat(events).isNotEmpty();
        log.info("PASSED: received {} metrics events", events.size());
    }
}
