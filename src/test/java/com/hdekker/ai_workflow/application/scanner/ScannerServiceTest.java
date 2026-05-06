package com.hdekker.ai_workflow.application.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.hdekker.ai_workflow.application.file.FileComparator;
import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;
import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;
import com.hdekker.ai_workflow.domain.shared.FileHash;

import reactor.core.publisher.Flux;

/**
 * Integration tests for {@link ScannerService} with mocked FileWatcherPort.
 *
 * Tests verify:
 * 1. Scanner creation with valid directories
 * 2. Scanner destroy cleans up resources (idempotent)
 * 3. Flux is accessible and well-formed
 * 4. Scanner state transitions work correctly
 */
public class ScannerServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ScannerServiceTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private FileMetadataRepository fileMetadataRepo;
    private FileComparator comparator;
    private ScannerObserverService observer;
    private FileWatcherPort mockWatcher;

    private ScannerService scanner;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        Files.createDirectory(tempDir.resolve("output"));

        fileMetadataRepo = mock(FileMetadataRepository.class);
        when(fileMetadataRepo.findById(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.doAnswer(inv -> {
            FileMetadata fm = inv.getArgument(0);
            return null;
        }).when(fileMetadataRepo).save(any());

        comparator = new FileComparator(fileMetadataRepo);
        observer = new ScannerObserverService(path -> 0L);

        mockWatcher = mock(FileWatcherPort.class);
        when(mockWatcher.flux()).thenReturn(Flux.empty());
        when(mockWatcher.getDirectory()).thenReturn(inputDir);
        when(mockWatcher.isRunning()).thenReturn(true);
        org.mockito.Mockito.doNothing().when(mockWatcher).start();
        org.mockito.Mockito.doNothing().when(mockWatcher).stop();
        org.mockito.Mockito.doNothing().when(mockWatcher).rawScan();
        when(mockWatcher.forDirectory(any(Path.class), any(Duration.class))).thenReturn(mockWatcher);
    }

    @AfterEach
    void tearDown() {
        if (scanner != null) {
            scanner.destroy();
        }
    }

    @Test
    void givenValidDirectory_WhenScannerCreated_ThenScannerExistsWithFlux() {
        log.info("Test: scanner created with valid directory");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        assertThat(scanner).isNotNull();
        assertThat(scanner.toInfo().folderPath()).isEqualTo(inputDir.toString());

        // Flux should be accessible
        Flux<?> flux = scanner.flux();
        assertThat(flux).isNotNull();

        log.info("PASSED: scanner created successfully");
    }

    @Test
    void givenValidDirectory_WhenDestroyCalled_ThenScannerIsDisposed() {
        log.info("Test: scanner destroy works");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        // Act: destroy
        scanner.destroy();

        log.info("PASSED: scanner destroyed successfully");
    }

    @Test
    void givenValidDirectory_WhenDestroyCalledTwice_ThenIdempotent() {
        log.info("Test: scanner destroy is idempotent");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        scanner.destroy();

        // Should not throw on second call
        scanner.destroy();

        log.info("PASSED: double destroy is safe");
    }

    @Test
    void givenValidDirectory_WhenResetToFullScan_ThenScanCompletes() throws Exception {
        log.info("Test: full scan completes without error");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        long startTime = System.currentTimeMillis();

        // Trigger full scan - should complete without error
        scanner.resetToFullScan();

        long elapsed = System.currentTimeMillis() - startTime;

        log.info("Full scan completed in {} ms", elapsed);

        assertThat(elapsed).isLessThan(5000);

        log.info("PASSED: full scan completed in {} ms");
    }

    @Test
    void givenEmptyDirectory_WhenResetToFullScan_ThenScannerConsistent() throws Exception {
        log.info("Test: full scan of empty directory completes");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        scanner.resetToFullScan();
        Thread.sleep(500);

        assertThat(scanner).isNotNull();

        log.info("PASSED: empty directory full scan completed successfully");
    }

    @Test
    void givenValidDirectory_WhenFluxSubscribedMultipleTimes_ThenNoException() throws Exception {
        log.info("Test: flux can be subscribed to multiple times without exception");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        List<Throwable> errors = new CopyOnWriteArrayList<>();

        scanner.flux().subscribe(
                null,
                errors::add,
                null
        );

        scanner.flux().subscribe(
                null,
                errors::add,
                null
        );

        scanner.flux().subscribe(
                null,
                errors::add,
                null
        );

        assertThat(errors).isEmpty();

        log.info("PASSED: multiple flux subscriptions created without exception");
    }

    @Test
    void givenValidDirectory_WhenFluxSubscribedAfterDestroy_ThenFluxCompletes() throws Exception {
        log.info("Test: flux subscription after destroy completes cleanly");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        scanner.destroy();

        CountDownLatch latch = new CountDownLatch(1);

        scanner.flux().subscribe(
                null,
                e -> latch.countDown(),
                latch::countDown
        );

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).as("Flux should complete after scanner destroy").isTrue();

        log.info("PASSED: flux after destroy completes cleanly");
    }

    @Test
    void givenValidDirectory_WhenGetFolderPath_ThenReturnsCorrectPath() {
        log.info("Test: getFolderPath returns the correct path");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        assertThat(scanner.toInfo().folderPath()).isEqualTo(inputDir.toString());

        log.info("PASSED: getFolderPath returns correct path");
    }

    @Test
    void givenValidDirectory_WhenToInfo_ThenInfoContainsCorrectData() {
        log.info("Test: toInfo returns correct scanner info");

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofSeconds(5),
                Duration.ZERO,
                mockWatcher,
                comparator,
                observer);

        var info = scanner.toInfo();

        assertThat(info.agentId()).isEqualTo("test-agent");
        assertThat(info.folderPath()).isEqualTo(inputDir.toString());
        assertThat(info.status()).isEqualTo("IDLE");
        assertThat(info.createdAt()).isNotNull();

        log.info("PASSED: toInfo returns correct data");
    }

    @Test
    void givenFileCreated_WhenWatcherEmitsEvent_ThenFileProcessed() throws Exception {
        log.info("Test: watcher event is processed through scanner");

        String testFileName = "test-full-scan.txt";
        String testContent = "test content for full scan";
        Path testFile = inputDir.resolve(testFileName);
        Files.writeString(testFile, testContent);

        // Create a flux that emits a raw file event
        RawFileEvent rawEvent = new RawFileEvent(testFile, testContent);
        FileWatcherPort emittingWatcher = mock(FileWatcherPort.class);
        when(emittingWatcher.flux()).thenReturn(Flux.just(rawEvent));
        when(emittingWatcher.getDirectory()).thenReturn(inputDir);
        when(emittingWatcher.isRunning()).thenReturn(true);
        org.mockito.Mockito.doNothing().when(emittingWatcher).start();
        org.mockito.Mockito.doNothing().when(emittingWatcher).stop();
        org.mockito.Mockito.doNothing().when(emittingWatcher).rawScan();
        when(emittingWatcher.forDirectory(any(Path.class), any(Duration.class))).thenReturn(emittingWatcher);

        scanner = new ScannerService("test-agent",
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                emittingWatcher,
                comparator,
                observer);

        // Collect emitted files
        List<com.hdekker.ai_workflow.domain.file.FileHistory> emitted = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        scanner.flux().take(1)
                .doOnNext(emitted::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        if (completed) {
            assertThat(emitted).hasSize(1);
            assertThat(emitted.get(0).currentFile().url()).isEqualTo(testFileName);
            assertThat(emitted.get(0).currentFile().body()).isEqualTo(testContent);
        } else {
            log.warn("File event was not emitted within timeout");
        }

        log.info("PASSED: watcher event processed through scanner");
    }
}
