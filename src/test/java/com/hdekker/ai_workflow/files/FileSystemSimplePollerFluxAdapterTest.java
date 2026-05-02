package com.hdekker.ai_workflow.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.TestConstants;
import com.hdekker.ai_workflow.usecases.Scanner;
import com.hdekker.ai_workflow.usecases.FileCounter;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import reactor.core.publisher.Flux;

/**
 * Tests for the end-to-end file watching flow using {@link Scanner}.
 * <p>
 * The Scanner composes {@link NativeFileWatcherAdapter} (raw events) and applies
 * business logic (hashing, comparison, history creation). These tests verify the
 * complete flow from file system events to FileHistory emission.
 */
public class FileSystemSimplePollerFluxAdapterTest {

    @TempDir
    Path tempDirectory;

    private Scanner scanner;

    @AfterEach
    void tearDown() {
        if (scanner != null) {
            scanner.destroy();
            scanner = null;
        }
    }

    static class InMemoryFileMetaDatabase implements FileMetadataStore {
        List<FileMetadata> stored = new ArrayList<>();

        @Override
        public Optional<FileMetadata> findById(String url) {
            return stored.stream()
                    .filter(fm -> fm.url().equals(url))
                    .findFirst();
        }

        @Override
        public void save(FileMetadata file) {
            stored.add(file);
        }
    }

    /**
     * Create a Scanner that watches the given folder.
     * Returns the Scanner's flux of FileHistory events.
     */
    public Flux<FileHistory> createFluxWithScanner(File folder, FileMetadataStore database) {
        ScannerObserverUseCase observer = new ScannerObserverUseCase(path -> 0L);
        scanner = new Scanner(
                folder.toString(),
                folder.toString(),
                Duration.ofMillis(500),
                Duration.ZERO,
                database,
                observer
                );
        return scanner.flux();
    }

    @Test
    public void canCaptureFileCreationWithNativeWatcher() throws IOException, InterruptedException {
        InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
        File folder = tempDirectory.toFile();

        // Track emissions with a latch
        CountDownLatch latch = new CountDownLatch(1);
        FileHistory[] received = new FileHistory[1];

        Flux<FileHistory> flux = createFluxWithScanner(folder, database);

        // The initial scan should pick up no files since the dir is empty
        assertThat(database.stored).isEmpty();

        // Create a file - the watch service should detect this
        Path testFile = tempDirectory.resolve(TestConstants.FILE_POOR_SOLID_COMPLIANCE);
        Files.copy(Path.of(TestConstants.getTestFilePath(TestConstants.FILE_POOR_SOLID_COMPLIANCE)), testFile);

        // Subscribe and wait for emission
        flux.subscribe(fh -> {
            received[0] = fh;
            latch.countDown();
        });

        // Wait up to 10 seconds for the file event
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).as("Expected file creation event within 10 seconds").isTrue();

        assertThat(received[0].currentFile().url()).contains(TestConstants.FILE_POOR_SOLID_COMPLIANCE);
        assertThat(received[0].previousFile()).isEmpty();
    }

    @Test
    public void canDetectFileModificationWithNativeWatcher() throws IOException, InterruptedException {
        InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
        File folder = tempDirectory.toFile();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        FileHistory[] first = new FileHistory[1];
        FileHistory[] second = new FileHistory[1];
        int[] count = new int[1];

        Flux<FileHistory> flux = createFluxWithScanner(folder, database);

        Path testFile = tempDirectory.resolve("test2.txt");

        // Create initial file
        Files.writeString(testFile, "initial content");

        // Subscribe and count emissions
        flux.subscribe(fh -> {
            if (count[0] == 0) {
                first[0] = fh;
                latch1.countDown();
            } else {
                second[0] = fh;
                latch2.countDown();
            }
            count[0]++;
        });

        // Wait for initial file emission
        assertThat(latch1.await(10, TimeUnit.SECONDS))
                .as("Expected initial file creation event").isTrue();

        assertThat(first[0].currentFile().body()).isEqualTo("initial content");
        assertThat(first[0].previousFile()).isEmpty();

        // Modify the file
        Files.writeString(testFile, "modified content");

        // Wait for modification event
        assertThat(latch2.await(10, TimeUnit.SECONDS))
                .as("Expected file modification event").isTrue();

        assertThat(second[0].currentFile().body()).isEqualTo("modified content");
        assertThat(second[0].previousFile()).isPresent();
        assertThat(second[0].previousFile().get().body()).isEqualTo("initial content");
    }

    @Test
    public void skipsUnchangedFilesWithNativeWatcher() throws IOException, InterruptedException {
        InMemoryFileMetaDatabase database = new InMemoryFileMetaDatabase();
        File folder = tempDirectory.toFile();

        CountDownLatch latch = new CountDownLatch(1);
        int[] count = new int[1];

        Flux<FileHistory> flux = createFluxWithScanner(folder, database);

        // Subscribe and count emissions
        flux.subscribe(fh -> {
            count[0]++;
            latch.countDown();
        });

        // Create file AFTER watcher started - watch service will detect it
        Path testFile = tempDirectory.resolve("test3.txt");
        Files.writeString(testFile, "unchanged content");

        // Wait for watch service to detect and emit
        assertThat(latch.await(10, TimeUnit.SECONDS))
                .as("Expected file creation event via watch service").isTrue();

        // File was stored in database during emission
        assertThat(database.stored).hasSize(1);

        // Wait extra time to ensure no additional emissions for unchanged file
        Thread.sleep(3000);

        // Should only have received the initial file event, not repeated events
        assertThat(count[0]).isEqualTo(1);
    }
}
