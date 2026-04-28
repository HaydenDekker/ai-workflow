package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileMetadataStore;
import com.hdekker.ai_workflow.files.domain.FileMetadata;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.function.Consumer;

/**
 * Parameterised file scanner adapter for use with {@link com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry}.
 * <p>
 * This adapter accepts its folder path and delay at construction time, enabling one adapter per agent.
 * <p>
 * Lifecycle is managed externally: the adapter uses a native NIO WatchService pipeline
 * for watching the target directory.
 */
public class FileSystemScannerAdapter implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScannerAdapter.class);

    private final String folderPath;
    private final Duration delayBetweenReads;
    private final FileMetadataStore fileMetadataStore;

    private final NativeFileWatcher nativeFileWatcher;
    private volatile boolean disposed = false;

    // Metrics
    private final Counter filesDiscoveredCounter;
    private final Counter filesUnchangedCounter;
    private final AtomicLong fileCount;

    /**
     * Creates a new parameterised scanner adapter.
     *
     * @param agentId             owning agent's ID (used for metric tagging)
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param fileMetadataStore   metadata for change detection
     * @param meterRegistry       Micrometer registry for metrics
     * @param metricsEventPublisher  callback to publish metrics change events
     */
    public FileSystemScannerAdapter(String agentId,
                                     String folderPath,
                                     Duration delayBetweenReads,
                                     FileMetadataStore fileMetadataStore,
                                     MeterRegistry meterRegistry,
                                     Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
        this.folderPath = folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.fileMetadataStore = fileMetadataStore;
        String effectiveAgentId = agentId != null ? agentId : folderPath;

        // Create metrics tagged with the real agentId and folder
        this.filesDiscoveredCounter = meterRegistry.counter(
                "ai_workflow.scanner.files_discovered", "agentId", effectiveAgentId, "folder", folderPath);
        this.filesUnchangedCounter = meterRegistry.counter(
                "ai_workflow.scanner.files_unchanged", "agentId", effectiveAgentId, "folder", folderPath);
        
        // Create an AtomicLong-backed gauge that we update after each scan
        this.fileCount = new AtomicLong(0);
        Gauge.builder("ai_workflow.scanner.file_count", fileCount, AtomicLong::get)
                .tag("agentId", effectiveAgentId)
                .tag("folder", folderPath)
                .register(meterRegistry);

        // Pass counters, gauge, and event callback to NativeFileWatcher
        this.nativeFileWatcher = new NativeFileWatcher(
                Path.of(folderPath), delayBetweenReads, fileMetadataStore,
                filesDiscoveredCounter, filesUnchangedCounter, fileCount,
                history -> {
                    if (metricsEventPublisher != null) {
                        metricsEventPublisher.accept(ScannerMetricsChangedEvent.fileCountUpdated(effectiveAgentId));
                    }
                });
        initSource();
    }

    /**
     * Initialise the native file watcher for watching the target directory.
     */
    private void initSource() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner for folder: {}", folder);

            nativeFileWatcher.start();

            // Update gauge after initial scan completes
            long currentCount = Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .count();
            fileCount.set(currentCount);

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
        }
    }

    /**
     * Returns the flux of file changes. Subscribers receive incremental updates
     * from the watch service.
     */
    @Override
    public Flux<FileHistory> flux() {
        return nativeFileWatcher.flux().onBackpressureBuffer();
    }

    /**
     * Reset the scanner to full-scan mode.
     * Emits all files from the target directory through the existing flux.
     * The watch service continues to emit incremental changes.
     */
    public void resetToFullScan() {
        log.info("Resetting scanner to full scan at: {}", folderPath);
        scanAllFiles();
        log.info("Full scan complete for: {}", folderPath);
    }

    /**
     * Scan all files in the target directory and emit new ones through the sink.
     * This is called during reset-to-full-scan operations.
     */
    private void scanAllFiles() {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            if (!Files.exists(folder)) {
                log.warn("Target folder does not exist: {}", folderPath);
                return;
            }

            FileComparator comparator = new FileComparator(fileMetadataStore);

            Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String hash = FileHash.hash(content);
                            String relativePath = folder.relativize(p).toString().replace("\\", "/");
                            FileMetadata metadata = new FileMetadata(relativePath, content, hash);
                            FileHistory history = comparator.matches(metadata);

                            if (!history.hashMatches()) {
                                filesDiscoveredCounter.increment();
                                log.debug("Full scan - emitting new file: {}", relativePath);
                                fileMetadataStore.save(metadata);
                                nativeFileWatcher.emit(history);
                            } else {
                                filesUnchangedCounter.increment();
                                log.debug("Full scan - skipping existing file: {}", relativePath);
                            }
                        } catch (IOException e) {
                            log.warn("Failed to read file during full scan: {}", p, e);
                        }
                    });

            // Update gauge after scan completes
            long currentCount = Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .count();
            fileCount.set(currentCount);
        } catch (IOException e) {
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
        }
    }

    /**
     * Destroy the scanner and clean up resources.
     */
    public void destroy() {
        if (disposed) {
            return;
        }
        disposed = true;
        nativeFileWatcher.stop();
        log.info("Scanner destroyed for folder: {}", folderPath);
    }

    /**
     * Check if this adapter has been destroyed.
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Get the folder path this adapter is watching.
     */
    public String getFolderPath() {
        return folderPath;
    }
}
