package com.hdekker.ai_workflow.adapter.outbound.file;

import java.nio.file.Path;
import java.time.Duration;

import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * Factory adapter for {@link FileWatcherPort}.
 * <p>
 * Implements the {@link FileWatcherPort} interface so it can be injected as a Spring bean.
 * The {@link #forDirectory(Path, Duration)} method delegates to creating a new
 * {@link NativeFileWatcher} instance for the requested directory.
 * <p>
 * This class is the Spring-managed entry point for file watching — the application
 * layer requests a watcher via the port, and this factory materialises the native
 * NIO implementation.
 */
@Component
public class FileWatcherFactory implements FileWatcherPort {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherFactory.class);

    // Delegates — set when forDirectory() is called, so the instance methods
    // (start, stop, etc.) operate on the configured watcher.
    private NativeFileWatcher delegate;

    /**
     * Creates a file watcher for the given directory.
     * <p>
     * Returns a new {@link FileWatcherPort} instance backed by a fresh
     * {@link NativeFileWatcher}. The returned watcher is independent of this
     * factory and manages its own lifecycle.
     *
     * @param directory    absolute path to watch
     * @param pollInterval interval for polling the watch service
     * @return a new FileWatcherPort instance for the specified directory
     */
    @Override
    public FileWatcherPort forDirectory(Path directory, Duration pollInterval) {
        log.debug("Creating NativeFileWatcher for {} with poll interval {}", directory, pollInterval);
        return new NativeFileWatcherAdapter(new NativeFileWatcher(directory, pollInterval));
    }

    // ── Default (no-op) implementations for factory instance ──
    // The factory itself is not a live watcher; these methods exist only to
    // satisfy the FileWatcherPort interface for Spring injection. Actual
    // operations happen on the watcher instances returned by forDirectory().

    @Override
    public void start() {
        // No-op on factory — use the instance returned by forDirectory()
    }

    @Override
    public void stop() {
        // No-op on factory — use the instance returned by forDirectory()
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void rawScan() {
        // No-op on factory — use the instance returned by forDirectory()
    }

    @Override
    public Flux<RawFileEvent> flux() {
        return Flux.empty();
    }

    @Override
    public Path getDirectory() {
        return null;
    }

    // ── Inner adapter wrapping NativeFileWatcher ──────────────────

    /**
     * Thin adapter wrapping {@link NativeFileWatcher} to implement {@link FileWatcherPort}.
     * <p>
     * NativeFileWatcher has matching methods but is not a Spring bean and does not
     * declare the interface. This adapter bridges the gap.
     */
    private static class NativeFileWatcherAdapter implements FileWatcherPort {

        private final NativeFileWatcher watcher;

        NativeFileWatcherAdapter(NativeFileWatcher watcher) {
            this.watcher = watcher;
        }

        @Override
        public FileWatcherPort forDirectory(Path directory, Duration pollInterval) {
            // Delegation — should not be called on an already-configured watcher
            throw new UnsupportedOperationException(
                    "forDirectory() is only available on FileWatcherFactory, not on individual watcher instances");
        }

        @Override
        public void start() {
            watcher.start();
        }

        @Override
        public void stop() {
            watcher.stop();
        }

        @Override
        public boolean isRunning() {
            return watcher.isRunning();
        }

        @Override
        public void rawScan() {
            try {
                watcher.rawScan();
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to perform raw scan", e);
            }
        }

        @Override
        public Flux<RawFileEvent> flux() {
            return watcher.flux();
        }

        @Override
        public Path getDirectory() {
            return watcher.getDirectory();
        }
    }
}
