package com.hdekker.ai_workflow.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileMetadataStore;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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
    private final String effectiveAgentId;
    private final Duration delayBetweenReads;
    private final Duration emissionDelay;
    private final FileMetadataStore fileMetadataStore;

    private final NativeFileWatcher nativeFileWatcher;
    private final ScannerObserverUseCase observer;
    private volatile boolean disposed = false;
    private final Consumer<String> onErrorCallback;

    /**
     * Creates a new parameterised scanner adapter.
     *
     * @param agentId             owning agent's ID (used for metric tagging)
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param emissionDelay       minimum interval between consecutive file emissions
     * @param fileMetadataStore   metadata for change detection
     * @param observer            scanner observer use case for metrics tracking
     * @param metricsEventPublisher  callback to publish metrics change events
     * @param onErrorCallback     callback invoked when the scanner encounters an error
     */
    public FileSystemScannerAdapter(String agentId,
                                     String folderPath,
                                     Duration delayBetweenReads,
                                     Duration emissionDelay,
                                     FileMetadataStore fileMetadataStore,
                                     ScannerObserverUseCase observer,
                                     Consumer<ScannerMetricsChangedEvent> metricsEventPublisher,
                                     Consumer<String> onErrorCallback) {
        this.folderPath = folderPath;
        this.effectiveAgentId = agentId != null ? agentId : folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.emissionDelay = emissionDelay;
        this.fileMetadataStore = fileMetadataStore;
        this.observer = observer;
        this.onErrorCallback = onErrorCallback;
        final String agentIdForCallbacks = this.effectiveAgentId;

        // Pass callbacks to NativeFileWatcher instead of Micrometer types
        this.nativeFileWatcher = new NativeFileWatcher(
                Path.of(folderPath), delayBetweenReads, emissionDelay, fileMetadataStore,
                aId -> observer.recordDiscovery(agentIdForCallbacks),
                aId -> observer.recordUnchanged(agentIdForCallbacks),
                aId -> observer.updateFileCount(agentIdForCallbacks, countFiles()),
                history -> {
                    if (metricsEventPublisher != null) {
                        metricsEventPublisher.accept(ScannerMetricsChangedEvent.fileCountUpdated(agentIdForCallbacks));
                    }
                },
                agentIdForCallbacks, onErrorCallback);
        initSource(this.effectiveAgentId);
    }

    /**
     * Backward-compatible constructor for tests.
     * <p>
     * Uses zero emission delay and no error callback.
     *
     * @param agentId             owning agent's ID (used for metric tagging)
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param fileMetadataStore   metadata for change detection
     * @param observer            scanner observer use case for metrics tracking
     * @param metricsEventPublisher  callback to publish metrics change events
     */
    public FileSystemScannerAdapter(String agentId,
                                     String folderPath,
                                     Duration delayBetweenReads,
                                     FileMetadataStore fileMetadataStore,
                                     ScannerObserverUseCase observer,
                                     Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
        this(agentId, folderPath, delayBetweenReads, Duration.ZERO,
                fileMetadataStore, observer, metricsEventPublisher, null);
    }

    /**
     * Initialise the native file watcher for watching the target directory.
     */
    private void initSource(String effectiveAgentId) {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner for folder: {}", folder);

            nativeFileWatcher.start();

            // Update file count after initial scan completes
            long currentCount = countFiles();
            observer.updateFileCount(effectiveAgentId, currentCount);

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            String errorMsg = "Failed to initialise scanner: " + e.getMessage();
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
            if (onErrorCallback != null) {
                onErrorCallback.accept(errorMsg);
            }
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
                if (onErrorCallback != null) {
                    onErrorCallback.accept("Target folder does not exist: " + folderPath);
                }
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
                                observer.recordDiscovery(effectiveAgentId);
                                log.debug("Full scan - emitting new file: {}", relativePath);
                                fileMetadataStore.save(metadata);
                                nativeFileWatcher.emit(history);
                            } else {
                                observer.recordUnchanged(effectiveAgentId);
                                log.debug("Full scan - skipping existing file: {}", relativePath);
                            }
                        } catch (IOException e) {
                            log.warn("Failed to read file during full scan: {}", p, e);
                        }
                    });

            // Update file count after scan completes
            long currentCount = countFiles();
            observer.updateFileCount(effectiveAgentId, currentCount);
        } catch (IOException e) {
            String errorMsg = "Failed to walk folder during full scan: " + e.getMessage();
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
            if (onErrorCallback != null) {
                onErrorCallback.accept(errorMsg);
            }
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

    /**
     * Get the effective agent ID used for metrics (falls back to folder path if agentId is null).
     */
    public String getEffectiveAgentId() {
        return effectiveAgentId;
    }

    /**
     * Count the number of regular files in the target directory.
     */
    private long countFiles() {
        try {
            return Files.walk(Path.of(folderPath).toAbsolutePath())
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            return 0L;
        }
    }
}
