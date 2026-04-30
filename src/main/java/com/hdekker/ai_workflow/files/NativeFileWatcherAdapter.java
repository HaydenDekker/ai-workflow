package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.usecases.RawFileEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * A native NIO-based file watcher that emits raw file events as reactive streams.
 * <p>
 * This class is a pure infrastructure adapter — it owns the {@link WatchService} loop
 * and emits {@link RawFileEvent} (path + content) for CREATE, MODIFY, and initial scan.
 * No business rules (hashing, comparison, metadata) are applied here; those belong
 * to {@link com.hdekker.ai_workflow.usecases.Scanner}.
 * <p>
 * Lifecycle is managed externally: call {@link #start()} to begin watching
 * and {@link #stop()} to clean up resources.
 */
public class NativeFileWatcherAdapter {

    private static final Logger log = LoggerFactory.getLogger(NativeFileWatcherAdapter.class);

    private final Path directory;
    private final Duration pollInterval;

    private final Sinks.Many<RawFileEvent> sink;
    private WatchService watchService;
    private volatile boolean running = false;
    private volatile Thread watchThread;

    /**
     * Creates a new file watcher.
     *
     * @param directory    absolute path to watch
     * @param pollInterval interval for polling the watch service
     */
    public NativeFileWatcherAdapter(Path directory, Duration pollInterval) {
        this.directory = directory.toAbsolutePath().normalize();
        this.pollInterval = pollInterval;
        this.sink = Sinks.many().multicast().directBestEffort();
    }

    /**
     * Start watching the directory for file changes.
     * <p>
     * Registers a watch service for CREATE, MODIFY, and DELETE events in
     * a background thread, then performs an initial full scan.
     * Raw events are emitted through the sink — consumers apply business logic.
     */
    public void start() {
        if (running) {
            log.warn("Watcher already running for: {}", directory);
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();

            // Register watch dirs in a non-blocking way
            registerWatchDirs();
            running = true;
            watchThread = new Thread(this::watchLoop, "native-file-watcher");
            watchThread.setDaemon(true);
            watchThread.start();

            // Initial full scan — emits raw events for each file
            log.info("Performing initial full scan of: {}", directory);
            rawScan();
            log.info("File watcher started for: {}", directory);

        } catch (IOException e) {
            log.error("Failed to start watcher for: {}", directory, e);
            running = false;
        }
    }

    /**
     * Register watch service on all directories. Called synchronously during start().
     */
    private void registerWatchDirs() throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The main watch loop that processes file system events.
     */
    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.poll(pollInterval.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // Overflow event indicates events were lost
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path eventName = pathEvent.context();
                    Path eventPath = directory.resolve(eventName);

                    processEvent(kind, eventPath);
                }

                // Reset the key
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("Watch key no longer valid for: {}, skipping to next poll", directory);
                }

            } catch (java.nio.file.ClosedWatchServiceException e) {
                // Service was closed, stop watching
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Process a single file system event.
     * Reads file content and emits a raw event through the sink.
     */
    private void processEvent(WatchEvent.Kind<?> kind, Path eventPath) {
        try {
            switch (kind.name()) {
                case "ENTRY_CREATE" -> {
                    if (Files.isRegularFile(eventPath)) {
                        // Small delay to ensure file is fully written
                        Thread.sleep(100);
                        emitRawFile(eventPath);
                    }
                }
                case "ENTRY_MODIFY" -> {
                    if (Files.isRegularFile(eventPath)) {
                        // Small delay to ensure file is fully written
                        Thread.sleep(100);
                        emitRawFile(eventPath);
                    }
                }
                case "ENTRY_DELETE" -> {
                    log.debug("File deleted: {}", eventPath);
                    // Note: We don't emit DELETE events since content is no longer available.
                    // The file will be re-created if it appears again.
                }
                default -> log.debug("Unknown event kind: {} for: {}", kind, eventPath);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing event {} for path {}: {}", kind, eventPath, e.getMessage());
        }
    }

    /**
     * Read a file and emit a raw event (path + content) through the sink.
     * Called during watch events (CREATE/MODIFY) and during raw scan.
     */
    private void emitRawFile(Path path) {
        try {
            String content = Files.readString(path);
            sink.tryEmitNext(new RawFileEvent(path, content));
        } catch (IOException e) {
            log.warn("Failed to read file for event: {}", path, e);
        }
    }

    /**
     * Scan all files in the directory and emit raw events.
     * Called during initial startup (start()) and on demand (rescan()).
     */
    public void rawScan() throws IOException {
        Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    try {
                        emitRawFile(p);
                    } catch (Exception e) {
                        log.warn("Failed to read file during scan: {}", p, e);
                    }
                });
    }

    /**
     * Stop the watcher and clean up resources.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        sink.tryEmitComplete();

        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Failed to close watch service", e);
            }
        }
        log.info("File watcher stopped for: {}", directory);
    }

    /**
     * Check if the watcher is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the flux of raw file events.
     * <p>
     * Consumers (e.g., {@link com.hdekker.ai_workflow.usecases.Scanner}) subscribe
     * to this flux and apply business logic (hashing, comparison, history creation).
     */
    public Flux<RawFileEvent> flux() {
        return sink.asFlux().onBackpressureBuffer();
    }

    /**
     * Get the watched directory.
     */
    public Path getDirectory() {
        return directory;
    }
}
