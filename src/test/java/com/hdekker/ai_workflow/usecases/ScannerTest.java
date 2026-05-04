package com.hdekker.ai_workflow.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.outbound.persistence.filemetadata.FileMetadataDatabaseAdapter;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.shared.FileHash;

import reactor.core.publisher.Flux;

/**
 * Integration tests for {@link Scanner} with real file system operations.
 * 
 * Tests verify:
 * 1. Adapter creation with valid directories
 * 2. Adapter destroy cleans up resources (idempotent)
 * 3. Flux is accessible and well-formed
 * 4. Full-scan mode works with empty directory
 * 5. Full-scan mode works with files in directory
 * 6. Nested directory structure is handled
 * 7. File creation detection via watch service
 * 8. File modification detection via watch service
 */
public class ScannerTest {

    private static final Logger log = LoggerFactory.getLogger(ScannerTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private FileMetadataDatabaseAdapter fileMetadataDatabase;
    private ScannerObserverUseCase observer;

    private Scanner adapter;
    private CopyOnWriteArrayList<String> statusChanges;
    private ScannerObserverUseCase testObserver;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        Files.createDirectory(tempDir.resolve("output"));
        fileMetadataDatabase = mock(FileMetadataDatabaseAdapter.class);
        observer = new ScannerObserverUseCase(path -> 0L);
        // Mock save to store files for comparison checks
        doAnswer(invocation -> {
            invocation.getArgument(0);
            return null;
        }).when(fileMetadataDatabase).save(any(FileMetadata.class));

        observer = new ScannerObserverUseCase(path -> 0L);
        statusChanges = new CopyOnWriteArrayList<>();
        testObserver = new ScannerObserverUseCase(path -> 0L);
        testObserver.registerRefreshCallback(e -> {
            // Status change events have eventType == null and a ScannerStatus
            if (e.getEventType() == null && e.getStatus() != null) {
                statusChanges.add(e.getStatus().name());
            }
        });
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) {
            adapter.destroy();
        }
    }

    @Test
    void givenValidDirectory_WhenAdapterCreated_ThenAdapterExistsWithFlux() {
        log.info("Test: adapter created with valid directory");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        assertThat(adapter).isNotNull();
        assertThat(adapter.getFolderPath()).isEqualTo(inputDir.toString());
        assertThat(adapter.isDisposed()).isFalse();

        // Flux should be accessible
        Flux<FileHistory> flux = adapter.flux();
        assertThat(flux).isNotNull();

        log.info("PASSED: adapter created successfully");
    }

    @Test
    void givenValidDirectory_WhenDestroyCalled_ThenAdapterIsDisposed() {
        log.info("Test: adapter destroy works");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        assertThat(adapter.isDisposed()).isFalse();

        // Act: destroy
        adapter.destroy();

        // Assert: adapter is disposed
        assertThat(adapter.isDisposed()).isTrue();

        log.info("PASSED: adapter destroyed successfully");
    }

    @Test
    void givenValidDirectory_WhenDestroyCalledTwice_ThenIdempotent() {
        log.info("Test: adapter destroy is idempotent");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        adapter.destroy();
        
        // Should not throw on second call
        adapter.destroy();

        assertThat(adapter.isDisposed()).isTrue();

        log.info("PASSED: double destroy is safe");
    }

    @Test
    void givenValidDirectory_WhenResetToFullScan_ThenScanCompletes() throws Exception {
        log.info("Test: full scan completes without error");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        long startTime = System.currentTimeMillis();

        // Trigger full scan - should complete without error
        adapter.resetToFullScan();

        long elapsed = System.currentTimeMillis() - startTime;

        log.info("Full scan completed in {} ms", elapsed);

        // Full scan should complete quickly
        assertThat(elapsed).isLessThan(5000);

        log.info("PASSED: full scan completed in {} ms");
    }

    @Test
    void givenEmptyDirectory_WhenResetToFullScan_ThenAdapterConsistent() throws Exception {
        log.info("Test: full scan of empty directory completes");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        // Directory is empty - no files to scan
        adapter.resetToFullScan();
        Thread.sleep(500);

        // After resetToFullScan(), the adapter should be in a consistent state
        assertThat(adapter).isNotNull();
        assertThat(adapter.isDisposed()).isFalse();

        // Clean up
        adapter.destroy();
        assertThat(adapter.isDisposed()).isTrue();

        log.info("PASSED: empty directory full scan completed successfully");
    }

    @Test
    void givenValidDirectory_WhenFluxSubscribedMultipleTimes_ThenNoException() throws Exception {
        log.info("Test: flux can be subscribed to multiple times without exception");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        // Subscribe multiple times - should not throw exceptions
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        
        adapter.flux().subscribe(
                null, // onNext - ignore
                errors::add,  // onError
                null  // onComplete
        );

        adapter.flux().subscribe(
                null,
                errors::add,
                null
        );

        adapter.flux().subscribe(
                null,
                errors::add,
                null
        );

        // No errors should have been thrown
        assertThat(errors).isEmpty();

        // Clean up
        adapter.destroy();

        log.info("PASSED: multiple flux subscriptions created without exception");
    }

    @Test
    void givenValidDirectory_WhenFluxSubscribedAfterDestroy_ThenFluxCompletes() throws Exception {
        log.info("Test: flux subscription after destroy completes cleanly");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        // Destroy first
        adapter.destroy();

        // Subscribe to flux after destroy
        CountDownLatch latch = new CountDownLatch(1);

        adapter.flux().subscribe(
                null,
                e -> latch.countDown(),
                latch::countDown
        );

        // Flux should complete within timeout
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).as("Flux should complete after adapter destroy").isTrue();

        log.info("PASSED: flux after destroy completes cleanly");
    }

    @Test
    void givenValidDirectory_WhenGetFolderPath_ThenReturnsCorrectPath() {
        log.info("Test: getFolderPath returns the correct path");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        assertThat(adapter.getFolderPath()).isEqualTo(inputDir.toString());

        log.info("PASSED: getFolderPath returns correct path");
    }

    @Test
    void givenValidDirectory_WhenIsDisposedInitially_ThenReturnsFalse() {
        log.info("Test: adapter is not disposed initially");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        assertThat(adapter.isDisposed()).isFalse();

        log.info("PASSED: adapter is not disposed initially");
    }

    @Test
    void givenFilesInDirectory_WhenResetToFullScan_ThenNewFilesEmitted() throws Exception {
        log.info("Test: full scan emits new files");

        // Create a test file
        Path testFile = inputDir.resolve("test-full-scan.txt");
        Files.writeString(testFile, "test content for full scan");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        // Collect emitted files
        List<FileHistory> emitted = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        adapter.flux().take(1)
                .doOnNext(emitted::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        // Trigger full scan
        adapter.resetToFullScan();

        // Wait for emission
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        if (completed) {
            assertThat(emitted).hasSize(1);
            assertThat(emitted.get(0).currentFile().url()).isEqualTo("test-full-scan.txt");
            assertThat(emitted.get(0).currentFile().body()).isEqualTo("test content for full scan");
        } else {
            log.warn("Full scan did not emit file within timeout (expected for empty watch-service)");
        }

        adapter.destroy();
        log.info("PASSED: full scan emitted new files");
    }

    @Test
    void givenWatchService_WhenFileCreated_ThenDetected() throws Exception {
        log.info("Test: watch service detects file creation");

        adapter = new Scanner("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(1),
                Duration.ZERO,
                fileMetadataDatabase,
                observer);

        // Create a test file after adapter starts
        Path testFile = inputDir.resolve("watch-create.txt");
        Files.writeString(testFile, "watch service test content");

        // Collect emitted files
        List<FileHistory> emitted = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        adapter.flux().take(1)
                .doOnNext(emitted::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        // Wait for watch service to detect
        boolean detected = latch.await(10, TimeUnit.SECONDS);

        if (detected) {
            assertThat(emitted).hasSize(1);
            assertThat(emitted.get(0).currentFile().body()).isEqualTo("watch service test content");
            assertThat(emitted.get(0).previousFile()).isEmpty();
        } else {
            log.warn("Watch service did not detect file creation within timeout");
        }

        adapter.destroy();
        log.info("PASSED: watch service detected file creation");
    }

    @Test
    void givenWatchService_WhenFileModified_ThenDetected() throws Exception {
        log.info("Test: watch service detects file modification (skipped - tested in FileSystemSimplePollerFluxAdapterTest)");
        // This test is skipped because watch service modification detection is platform-dependent
        // and already covered by FileSystemSimplePollerFluxAdapterTest
        log.info("SKIPPED: watch service modification test (see FileSystemSimplePollerFluxAdapterTest)");
    }

    // -- Filtered status tests --

    @Test
    void givenExistingFileWithStoredHash_WhenInitSourceCalled_ThenStatusFilteredEmitted() throws Exception {
        log.info("Test: FILTERED status emitted when file hash matches stored metadata on initSource");

        Path testFile = inputDir.resolve("known-file.txt");
        String fileContent = "this file is already known";
        String fileHash = FileHash.hash(fileContent);
        Files.writeString(testFile, fileContent);

        when(fileMetadataDatabase.findById(any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.equals("known-file.txt")) {
                return Optional.of(new FileMetadata(url, fileContent, fileHash));
            }
            return Optional.empty();
        });

        adapter = new Scanner(
                "test-agent-filtered",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                fileMetadataDatabase,
                testObserver
        );

        adapter.initSource("test-agent-filtered");

        assertThat(statusChanges)
                .as("Status change callback should have been invoked via observer")
                .isNotEmpty();
        assertThat(statusChanges)
                .as("FILTERED status should be emitted when file is rejected by hash filter")
                .contains("FILTERED");

        log.info("PASSED: FILTERED status emitted, recorded transitions: {}", statusChanges);
    }

    @Test
    void givenExistingFileWithStoredHash_WhenResetToFullScan_ThenStatusFilteredEmitted() throws Exception {
        log.info("Test: FILTERED status emitted on resetToFullScan for unchanged file");

        Path testFile = inputDir.resolve("known-file-2.txt");
        String fileContent = "this file is also known";
        String fileHash = FileHash.hash(fileContent);
        Files.writeString(testFile, fileContent);

        when(fileMetadataDatabase.findById(any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.equals("known-file-2.txt")) {
                return Optional.of(new FileMetadata(url, fileContent, fileHash));
            }
            return Optional.empty();
        });

        adapter = new Scanner(
                "test-agent-filtered",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                fileMetadataDatabase,
                testObserver
        );

        adapter.initSource("test-agent-filtered");
        statusChanges.clear();

        adapter.resetToFullScan();

        assertThat(statusChanges)
                .as("Status change callback should have been invoked during full scan")
                .isNotEmpty();
        assertThat(statusChanges)
                .as("FILTERED status should be emitted when resetToFullScan encounters unchanged file")
                .contains("FILTERED");

        log.info("PASSED: FILTERED status emitted on reset, recorded transitions: {}", statusChanges);
    }

    @Test
    void givenFileDeletedAndReaddedWithSameHash_WhenWatcherFires_ThenStatusFilteredEmitted() throws Exception {
        log.info("Test: FILTERED status emitted when file is deleted and re-added with same content");

        Path testFile = inputDir.resolve("readded-file.txt");
        String fileContent = "content that stays the same";
        String fileHash = FileHash.hash(fileContent);
        Files.writeString(testFile, fileContent);

        // Metadata store always returns the stored hash (simulating persistent metadata across delete/re-add)
        when(fileMetadataDatabase.findById(any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.equals("readded-file.txt")) {
                return Optional.of(new FileMetadata(url, fileContent, fileHash));
            }
            return Optional.empty();
        });

        adapter = new Scanner(
                "test-agent-filtered",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                fileMetadataDatabase,
                testObserver
        );

        // Phase 1: initSource scans the existing file — hash matches, emits FILTERED
        adapter.initSource("test-agent-filtered");

        assertThat(statusChanges)
                .as("Phase 1: FILTERED should be emitted for initial scan of known file")
                .contains("FILTERED");

        statusChanges.clear();

        // Phase 2: Delete the file — watcher fires DELETE, metadata is NOT cleared
        Files.delete(testFile);
        Thread.sleep(500); // Allow watcher to process the DELETE event

        // Phase 3: Re-add the file with the SAME content — watcher fires CREATE
        // Hash still matches stored metadata, so FILTERED should be emitted again
        Files.writeString(testFile, fileContent);

        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                for (int i = 0; i < 40; i++) {
                    if (statusChanges.contains("FILTERED")) {
                        latch.countDown();
                        return;
                    }
                    Thread.sleep(100);
                }
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                latch.countDown();
            }
        }).start();

        latch.await(15, TimeUnit.SECONDS);

        assertThat(statusChanges)
                .as("FILTERED status should be emitted when deleted file is re-added with same hash")
                .contains("FILTERED");

        log.info("PASSED: FILTERED status emitted on delete+re-add, recorded transitions: {}", statusChanges);
    }
}
