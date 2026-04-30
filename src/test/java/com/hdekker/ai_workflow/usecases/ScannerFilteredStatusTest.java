package com.hdekker.ai_workflow.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileMetadataStore;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

/**
 * Tests that the scanner status transitions to FILTERED when a file is rejected
 * by the hash filter (i.e., the file is unchanged / already known).
 * <p>
 * Verifies:
 * 1. When a file exists and its hash matches the stored metadata, the
 *    {@code onStatusChanged} callback is invoked with STATUS_FILTERED
 * 2. The FILTERED status is triggered during initSource() for pre-existing files
 * 3. The FILTERED status is triggered during resetToFullScan() for unchanged files
 */
public class ScannerFilteredStatusTest {

    private static final Logger log = LoggerFactory.getLogger(ScannerFilteredStatusTest.class);

    @TempDir
    Path tempDir;

    private Path inputDir;
    private FileMetadataStore fileMetadataStore;
    private ScannerObserverUseCase observer;

    private Scanner adapter;
    private CopyOnWriteArrayList<String> statusChanges;
    private String agentId;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = Files.createDirectory(tempDir.resolve("input"));
        fileMetadataStore = mock(FileMetadataStore.class);
        observer = new ScannerObserverUseCase();
        agentId = "test-agent-filtered";

        statusChanges = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) {
            adapter.destroy();
        }
    }

    @Test
    void givenExistingFileWithStoredHash_WhenInitSourceCalled_ThenStatusFilteredEmitted() {
        log.info("Test: FILTERED status emitted when file is rejected by hash filter");

        // Step 1: Create a file in the directory
        Path testFile = inputDir.resolve("known-file.txt");
        String fileContent = "this file is already known";
        String fileHash = FileHash.hash(fileContent);

        try {
            Files.writeString(testFile, fileContent);
        } catch (Exception e) {
            log.error("Failed to create test file", e);
            return;
        }

        // Step 2: Pre-populate the metadata store with this file's hash
        // This simulates the file being previously scanned and stored
        doAnswer(invocation -> {
            FileMetadata fm = invocation.getArgument(0);
            return null;
        }).when(fileMetadataStore).save(any(FileMetadata.class));

        when(fileMetadataStore.findById(any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.equals("known-file.txt")) {
                return Optional.of(new FileMetadata(url, fileContent, fileHash));
            }
            return Optional.empty();
        });

        // Step 3: Create adapter with status change callback
        adapter = new Scanner(
                agentId,
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                fileMetadataStore,
                observer,
                null,           // no metrics event publisher
                null,           // no error callback
                statusChanges::add, // record status changes (string callback)
                null,           // no enum callback
                null            // no emission callback
        );

        // Step 4: Call initSource() which triggers the initial scan
        adapter.initSource(agentId);

        // Step 5: Verify that STATUS_FILTERED was emitted
        assertThat(statusChanges)
                .as("Status change callback should have been invoked")
                .isNotEmpty();

        boolean filteredEmitted = statusChanges.contains("FILTERED");
        assertThat(filteredEmitted)
                .as("FILTERED status should be emitted when file is rejected by hash filter")
                .isTrue();

        log.info("PASSED: FILTERED status emitted, recorded transitions: {}", statusChanges);
    }

    @Test
    void givenExistingFileWithStoredHash_WhenResetToFullScan_ThenStatusFilteredEmitted() throws Exception {
        log.info("Test: FILTERED status emitted on resetToFullScan for unchanged file");

        // Step 1: Create a file in the directory
        Path testFile = inputDir.resolve("known-file-2.txt");
        String fileContent = "this file is also known";
        String fileHash = FileHash.hash(fileContent);

        Files.writeString(testFile, fileContent);

        // Step 2: Pre-populate the metadata store
        doAnswer(invocation -> {
            FileMetadata fm = invocation.getArgument(0);
            return null;
        }).when(fileMetadataStore).save(any(FileMetadata.class));

        when(fileMetadataStore.findById(any())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.equals("known-file-2.txt")) {
                return Optional.of(new FileMetadata(url, fileContent, fileHash));
            }
            return Optional.empty();
        });

        // Step 3: Create adapter with status callback
        adapter = new Scanner(
                agentId,
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                fileMetadataStore,
                observer,
                null,
                null,
                statusChanges::add, // record status changes (string callback)
                null,           // no enum callback
                null            // no emission callback
        );

        // Step 4: Init source first (to establish initial state)
        adapter.initSource(agentId);
        statusChanges.clear();

        // Step 5: Trigger reset to full scan
        adapter.resetToFullScan();

        // Step 6: Verify FILTERED status was emitted
        assertThat(statusChanges)
                .as("Status change callback should have been invoked during full scan")
                .isNotEmpty();

        boolean filteredEmitted = statusChanges.contains("FILTERED");
        assertThat(filteredEmitted)
                .as("FILTERED status should be emitted when resetToFullScan encounters unchanged file")
                .isTrue();

        log.info("PASSED: FILTERED status emitted on reset, recorded transitions: {}", statusChanges);
    }

    @Test
    void givenNewFile_WhenInitSourceCalled_ThenStatusFilteredNotEmitted() {
        log.info("Test: FILTERED status NOT emitted for new files");

        // Step 1: Create a file in the directory
        Path testFile = inputDir.resolve("new-file.txt");
        String fileContent = "this is a brand new file";

        try {
            Files.writeString(testFile, fileContent);
        } catch (Exception e) {
            log.error("Failed to create test file", e);
            return;
        }

        // Step 2: Pre-populate the metadata store to return empty for this file
        // (simulating a file that has never been seen before)
        when(fileMetadataStore.findById(any())).thenReturn(Optional.empty());

        // Step 3: Create adapter with status callback
        adapter = new Scanner(
                agentId,
                inputDir.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                fileMetadataStore,
                observer,
                null,
                null,
                statusChanges::add, // record status changes (string callback)
                null,           // no enum callback
                null            // no emission callback
        );

        // Step 4: Call initSource()
        adapter.initSource(agentId);

        // Step 5: Verify FILTERED was NOT emitted (file is new, should be discovered)
        boolean filteredEmitted = statusChanges.contains("FILTERED");
        assertThat(filteredEmitted)
                .as("FILTERED status should NOT be emitted for new files")
                .isFalse();

        log.info("PASSED: FILTERED status correctly not emitted for new files, transitions: {}", statusChanges);
    }
}
