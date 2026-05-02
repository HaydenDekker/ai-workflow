package com.hdekker.ai_workflow.application.file.port;

import java.nio.file.Path;
import java.time.Duration;

import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;

import reactor.core.publisher.Flux;

/**
 * Port interface for watching a directory for file changes.
 * <p>
 * Declares the contract between the application layer (scanner) and the
 * file watching infrastructure. Implementations use native NIO WatchService
 * or polling-based approaches.
 */
public interface FileWatcherPort {

    /**
     * Creates a file watcher for the given directory.
     *
     * @param directory    absolute path to watch
     * @param pollInterval interval for polling the watch service
     * @return a file watcher instance
     */
    FileWatcherPort forDirectory(Path directory, Duration pollInterval);

    /**
     * Start watching the directory for file changes.
     * <p>
     * Registers a watch service and begins emitting raw file events
     * through the flux returned by {@link #flux()}.
     */
    void start();

    /**
     * Stop watching and clean up resources.
     */
    void stop();

    /**
     * Check if the watcher is currently running.
     *
     * @return true if the watcher is running
     */
    boolean isRunning();

    /**
     * Perform an initial full scan of the directory.
     * <p>
     * Emits raw file events for all existing files through the flux.
     * Called during startup and on-demand rescan.
     */
    void rawScan();

    /**
     * Get the flux of raw file events.
     * <p>
     * Consumers subscribe to this flux to receive CREATE, MODIFY, and
     * DELETE events. The application layer applies business logic
     * (hashing, comparison, metadata) on top of these events.
     *
     * @return a reactive stream of raw file events
     */
    Flux<RawFileEvent> flux();

    /**
     * Get the watched directory.
     *
     * @return the directory path being watched
     */
    Path getDirectory();
}
