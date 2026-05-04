package com.hdekker.ai_workflow.adapter.outbound.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;

/**
 * Unit tests for {@link NativeFileWatcher} raw event emission.
 * <p>
 * The adapter is a pure infrastructure component — it emits {@link RawFileEvent}
 * (path + content) for CREATE, MODIFY, and initial scan. No business rules
 * (hashing, comparison, metadata) are applied here.
 * <p>
 * Verifies that:
 * 1. Raw events are emitted during initial scan
 * 2. Raw events are emitted for file creation (watch service)
 * 3. Multiple files produce multiple raw events
 */
public class NativeFileWatcherAdapterMetricsTest {

    private static final Logger log = LoggerFactory.getLogger(NativeFileWatcherAdapterMetricsTest.class);

    @TempDir
    Path tempDir;

    private NativeFileWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    void givenEmptyDirectory_WhenWatcherStarted_ThenNoRawEventsEmitted() throws Exception {
        watcher = new NativeFileWatcher(tempDir, Duration.ofMillis(500));

        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<RawFileEvent> events = new CopyOnWriteArrayList<>();

        watcher.flux().subscribe(
                events::add,
                e -> {},
                latch::countDown
        );

        watcher.start();
        Thread.sleep(500);

        // No files to scan — no raw events emitted
        assertThat(events).isEmpty();
        log.info("PASSED: no raw events for empty directory");
    }

    @Test
    void givenFileInDirectory_WhenWatcherStarted_ThenRawEventEmitted() throws Exception {
        // Create a file before starting the watcher
        Path testFile = tempDir.resolve("initial.txt");
        Files.writeString(testFile, "initial content");

        watcher = new NativeFileWatcher(tempDir, Duration.ofMillis(500));

        new CountDownLatch(1);
        CopyOnWriteArrayList<RawFileEvent> events = new CopyOnWriteArrayList<>();

        watcher.flux().subscribe(events::add, e -> {}, () -> {});

        watcher.start();
        Thread.sleep(1000);

        // At least one raw event should have been emitted during initial scan
        assertThat(events).isNotEmpty();
        RawFileEvent event = events.get(0);
        assertThat(event.path().getFileName().toString()).isEqualTo("initial.txt");
        assertThat(event.content()).isEqualTo("initial content");
        log.info("PASSED: raw event emitted for file in directory");
    }

    @Test
    void givenMultipleFiles_WhenWatcherStarted_ThenAllEmitRawEvents() throws Exception {
        // Create multiple files
        Files.writeString(tempDir.resolve("file1.txt"), "content 1");
        Files.writeString(tempDir.resolve("file2.txt"), "content 2");
        Files.writeString(tempDir.resolve("file3.txt"), "content 3");

        watcher = new NativeFileWatcher(tempDir, Duration.ofMillis(500));

        CopyOnWriteArrayList<RawFileEvent> events = new CopyOnWriteArrayList<>();
        watcher.flux().subscribe(events::add, e -> {}, () -> {});

        watcher.start();
        Thread.sleep(1000);

        // All 3 files should produce raw events during initial scan
        assertThat(events).hasSize(3);

        CopyOnWriteArrayList<String> fileNames = new CopyOnWriteArrayList<>();
        for (RawFileEvent event : events) {
            fileNames.add(event.path().getFileName().toString());
        }
        assertThat(fileNames).contains("file1.txt", "file2.txt", "file3.txt");
        log.info("PASSED: all {} files emitted as raw events", events.size());
    }

    @Test
    void givenWatcherStarted_WhenFileCreated_ThenRawEventEmitted() throws Exception {
        watcher = new NativeFileWatcher(tempDir, Duration.ofMillis(500));

        CopyOnWriteArrayList<RawFileEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        watcher.flux().subscribe(
                events::add,
                e -> {},
                () -> {}
        );

        watcher.start();
        Thread.sleep(500);

        // Create a file after watcher starts
        Path testFile = tempDir.resolve("watch-create.txt");
        Files.writeString(testFile, "watch service test content");

        // Wait for watch service to detect
        boolean detected = latch.await(10, TimeUnit.SECONDS);

        if (detected) {
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).content()).isEqualTo("watch service test content");
        } else {
            // Watch service may not fire in all environments — check events anyway
            assertThat(events).isNotEmpty();
        }

        log.info("PASSED: raw event emitted for file creation ({} events)", events.size());
    }

    @Test
    void givenWatcherStarted_ThenRawScanEmitsEvents() throws Exception {
        // Create files
        Files.writeString(tempDir.resolve("scan1.txt"), "scan content 1");
        Files.writeString(tempDir.resolve("scan2.txt"), "scan content 2");

        watcher = new NativeFileWatcher(tempDir, Duration.ofMillis(500));

        CopyOnWriteArrayList<RawFileEvent> events = new CopyOnWriteArrayList<>();
        watcher.flux().subscribe(events::add, e -> {}, () -> {});

        watcher.start();
        Thread.sleep(500);

        // Clear events from initial scan
        events.clear();

        // Trigger raw scan again
        watcher.rawScan();
        Thread.sleep(500);

        // Raw scan should emit events for all files
        assertThat(events).hasSize(2);
        log.info("PASSED: rawScan emitted {} events", events.size());
    }
}
