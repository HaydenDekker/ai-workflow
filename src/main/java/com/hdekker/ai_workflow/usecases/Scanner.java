package com.hdekker.ai_workflow.usecases;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.files.FileComparator;
import com.hdekker.ai_workflow.files.FileHash;
import com.hdekker.ai_workflow.files.FileMetadataStore;
import com.hdekker.ai_workflow.files.domain.FileMetadata;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.files.FileScanner;
import com.hdekker.ai_workflow.files.NativeFileWatcherAdapter;

import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Domain scanner — the central concept for file watching.
 * <p>
 * Owns status, idle timer, error handling, metrics publishing, DTO conversion,
 * and the FileHash/FileHistory business rules. Composes {@link com.hdekker.ai_workflow.files.NativeFileWatcherAdapter}.
 */
public class Scanner implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(Scanner.class);

    private final String folderPath;
    private final String effectiveAgentId;
    private final Duration delayBetweenReads;
    private final Duration emissionDelay;
    private final FileMetadataStore fileMetadataStore;

    private final NativeFileWatcherAdapter nativeFileWatcher;
    private final ScannerObserverUseCase observer;
    private volatile boolean disposed = false;

    // -- Status state (moved from ScannerRegistry) --
    private volatile String status = STATUS_IDLE;
    private volatile String errorMessage;
    private volatile LocalDateTime lastEmittedAt;
    private final LocalDateTime createdAt;

    /** Seconds of inactivity before transitioning from EMITTING_UPDATES to IDLE. */
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);
    /** Interval for the idle-checker scheduler. */
    private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(10);

    // -- Idle checker (moved from ScannerRegistry) --
    private final ScheduledExecutorService idleChecker;

    private final Consumer<ScannerMetricsChangedEvent> metricsEventPublisher;
    private final Consumer<String> onErrorCallback;
    private final Consumer<String> onStatusChanged;
    private final Consumer<String> onEmission; // called when a file is emitted (updates idle timer)
    private final ScheduledExecutorService filteredResetScheduler;
    private volatile java.util.concurrent.ScheduledFuture<?> filteredResetTask;

    /**
     * Creates a new Scanner.
     *
     * @param agentId             owning agent's ID (used for metric tagging)
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param emissionDelay       minimum interval between consecutive file emissions
     * @param fileMetadataStore   metadata for change detection
     * @param observer            scanner observer use case for metrics tracking
     * @param metricsEventPublisher  callback to publish metrics change events
     * @param onErrorCallback     callback invoked when the scanner encounters an error
     * @param onStatusChanged     callback invoked when scanner status changes
     * @param onEmission          callback invoked when a file is emitted (updates idle timer)
     */
    public Scanner(String agentId,
                                     String folderPath,
                                     Duration delayBetweenReads,
                                     Duration emissionDelay,
                                     FileMetadataStore fileMetadataStore,
                                     ScannerObserverUseCase observer,
                                     Consumer<ScannerMetricsChangedEvent> metricsEventPublisher,
                                     Consumer<String> onErrorCallback,
                                     Consumer<String> onStatusChanged,
                                     Consumer<String> onEmission) {
        this.folderPath = folderPath;
        this.effectiveAgentId = agentId != null ? agentId : folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.emissionDelay = emissionDelay;
        this.fileMetadataStore = fileMetadataStore;
        this.observer = observer;
        this.createdAt = LocalDateTime.now();
        this.metricsEventPublisher = metricsEventPublisher;
        this.onErrorCallback = onErrorCallback;
        this.onStatusChanged = onStatusChanged;
        this.onEmission = onEmission;

        // Idle checker — monitors this scanner for inactivity
        this.idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scanner-idle-checker-" + this.effectiveAgentId);
            t.setDaemon(true);
            return t;
        });
        startIdleChecker();

        final String agentIdForCallbacks = this.effectiveAgentId;

        // Scheduled executor for resetting FILTERED status back to IDLE
        this.filteredResetScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "filtered-reset");
            t.setDaemon(true);
            return t;
        });

        // Pass callbacks to NativeFileWatcherAdapter instead of Micrometer types
        this.nativeFileWatcher = new NativeFileWatcherAdapter(
                Path.of(folderPath), delayBetweenReads, emissionDelay, fileMetadataStore,
                aId -> observer.recordDiscovery(agentIdForCallbacks),
                aId -> observer.recordUnchanged(agentIdForCallbacks),
                aId -> observer.updateFileCount(agentIdForCallbacks, countFiles()),
                history -> {
                    if (metricsEventPublisher != null) {
                        metricsEventPublisher.accept(ScannerMetricsChangedEvent.fileCountUpdated(agentIdForCallbacks));
                    }
                },
                aId -> {
                    // Hash filter rejected a file — briefly set FILTERED status
                    if (onStatusChanged != null) {
                        // Cancel any pending reset task
                        if (filteredResetTask != null) {
                            filteredResetTask.cancel(false);
                        }
                        onStatusChanged.accept(STATUS_FILTERED);
                        // Schedule reset to IDLE after 2 seconds
                        final String agentIdForReset = agentIdForCallbacks;
                        filteredResetTask = filteredResetScheduler.schedule(
                                () -> onStatusChanged.accept(STATUS_IDLE), 2, TimeUnit.SECONDS);
                    }
                },
                aId -> {
                    // A file was emitted — update idle timer so the scanner stays active
                    if (onEmission != null) {
                        onEmission.accept(agentIdForCallbacks);
                    }
                },
                agentIdForCallbacks, onErrorCallback);
    }

    /**
     * Backward-compatible constructor for tests.
     * <p>
     * Uses zero emission delay and no error or status callbacks.
     *
     * @param agentId             owning agent's ID (used for metric tagging)
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param fileMetadataStore   metadata for change detection
     * @param observer            scanner observer use case for metrics tracking
     * @param metricsEventPublisher  callback to publish metrics change events
     */
    public Scanner(String agentId,
                                     String folderPath,
                                     Duration delayBetweenReads,
                                     FileMetadataStore fileMetadataStore,
                                     ScannerObserverUseCase observer,
                                     Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
        this.folderPath = folderPath;
        this.effectiveAgentId = agentId != null ? agentId : folderPath;
        this.delayBetweenReads = delayBetweenReads;
        this.emissionDelay = Duration.ZERO;
        this.fileMetadataStore = fileMetadataStore;
        this.observer = observer;
        this.createdAt = LocalDateTime.now();
        this.metricsEventPublisher = metricsEventPublisher;
        this.onErrorCallback = null;
        this.onStatusChanged = null;
        this.onEmission = null;

        // Idle checker
        this.idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scanner-idle-checker-" + this.effectiveAgentId);
            t.setDaemon(true);
            return t;
        });
        startIdleChecker();

        this.filteredResetScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "filtered-reset");
            t.setDaemon(true);
            return t;
        });

        // No callbacks — tests using this constructor don't need status/error transitions
        this.nativeFileWatcher = new NativeFileWatcherAdapter(
                Path.of(folderPath), delayBetweenReads, Duration.ZERO, fileMetadataStore,
                aId -> observer.recordDiscovery(this.effectiveAgentId),
                aId -> observer.recordUnchanged(this.effectiveAgentId),
                aId -> observer.updateFileCount(this.effectiveAgentId, countFiles()),
                history -> {
                    // no metrics event publisher in test constructor
                },
                aId -> {
                    // hash filter — no status callback in test constructor
                },
                aId -> {
                    // emission — no emission callback in test constructor
                },
                this.effectiveAgentId, null);

        // Tests using this constructor need the watcher to run.
        // No status callback, so no status transitions occur.
        initSource(this.effectiveAgentId);
    }

    /**
     * Initialise the native file watcher for watching the target directory.
     * <p>
     * Status transitions happen here so they occur after the hash filter
     * in {@code scanAllFiles()} has processed all existing files:
     * IDLE → EMITTING_INITIAL (before scan) → EMITTING_UPDATES (after scan).
     * <p>
     * Called by {@link com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry}
     * after the scanner is registered in the map so callbacks can find it.
     */
    public void initSource(String effectiveAgentId) {
        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            log.info("Setting up scanner for folder: {}", folder);

            // Transition to EMITTING_INITIAL before the initial full scan
            // (hash filter runs inside nativeFileWatcher.start() → scanAllFiles())
            notifyStatusChange(STATUS_EMITTING_INITIAL);

            nativeFileWatcher.start();

            // Flush any buffered files so they are emitted immediately if the
            // emission delay has elapsed. This ensures the file count is accurate
            // and the status reflects actual emission activity.
            nativeFileWatcher.flushBufferedEmission();

            // Update file count after initial scan completes
            long currentCount = countFiles();
            observer.updateFileCount(effectiveAgentId, currentCount);

            // Transition to EMITTING_UPDATES only if files were buffered (meaning
            // the hash filter found at least one changed/new file). If nothing
            // was buffered, all files are already known — stay IDLE.
            if (nativeFileWatcher.scanBufferedAnyFile()) {
                notifyStatusChange(STATUS_EMITTING_UPDATES);
            } else {
                notifyStatusChange(STATUS_IDLE);
                log.info("Scanner initialised for folder: {} – no new files, staying IDLE", folderPath);
            }

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
     * Update the scanner's status.
     * <p>
     * Updates the internal status field only. Does not trigger callbacks —
     * this method is the target of the {@code onStatusChanged} callback chain
     * so calling the callback here would cause infinite recursion.
     *
     * @param newStatus the new status string
     */
    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    /**
     * Get the current scanner status.
     *
     * @return the current status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Transition this scanner to the ERROR state.
     *
     * @param reason human-readable description of the error
     */
    public void transitionToError(String reason) {
        this.errorMessage = reason;
        this.status = STATUS_ERROR;
        log.error("Scanner for agent {} entered ERROR state: {}", effectiveAgentId, reason);
    }

    /**
     * Recover from the ERROR state.
     * Resets status to IDLE and clears the error message.
     */
    public void recover() {
        this.errorMessage = null;
        this.status = STATUS_IDLE;
        log.info("Recovered scanner for agent {} from ERROR state", effectiveAgentId);
    }

    /**
     * Get the error message, if any.
     *
     * @return the error message, or null if no error
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Push a metrics change event to the UI.
     *
     * @param event the metrics change event
     */
    public void pushMetricsEvent(ScannerMetricsChangedEvent event) {
        if (metricsEventPublisher != null) {
            try {
                metricsEventPublisher.accept(event);
            } catch (Exception e) {
                log.warn("Error publishing metrics event: {}", e.getMessage());
            }
        }
    }

    /**
     * Record that a file was emitted, resetting the idle timer.
     */
    public void recordEmission() {
        this.lastEmittedAt = LocalDateTime.now();
        log.debug("Recorded emission for agent {} – resetting idle timer", effectiveAgentId);
    }

    /**
     * Get the last emission timestamp.
     *
     * @return the last emission timestamp, or null if none
     */
    public LocalDateTime getLastEmittedAt() {
        return lastEmittedAt;
    }

    /**
     * Get the creation timestamp.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Start the idle-checker that monitors this scanner for inactivity.
     */
    private void startIdleChecker() {
        idleChecker.scheduleAtFixedRate(this::checkIdle,
                IDLE_CHECK_INTERVAL.toSeconds(),
                IDLE_CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
        log.debug("Idle checker started for agent {} (interval={}s)", effectiveAgentId, IDLE_CHECK_INTERVAL.getSeconds());
    }

    /**
     * Check if this scanner is idle. If in EMITTING_UPDATES and no emission
     * for IDLE_TIMEOUT, transition to IDLE.
     */
    private void checkIdle() {
        if (disposed) {
            return;
        }
        // Only check scanners that are actively emitting
        if (!STATUS_EMITTING_UPDATES.equals(status)) {
            return;
        }

        // If we have a lastEmittedAt, check whether it's older than the idle timeout
        LocalDateTime lastEmit = lastEmittedAt;
        if (lastEmit != null) {
            Duration sinceLastEmission = Duration.between(lastEmit, LocalDateTime.now());
            if (sinceLastEmission.compareTo(IDLE_TIMEOUT) >= 0) {
                notifyStatusChange(STATUS_IDLE);
                log.info("Scanner for agent {} transitioned to IDLE after {}s of inactivity",
                        effectiveAgentId, sinceLastEmission.getSeconds());
            }
        }
    }

    /**
     * Notify the registry of a status change.
     * Updates internal state and fires the external callback.
     */
    private void notifyStatusChange(String newStatus) {
        this.status = newStatus;
        if (onStatusChanged != null) {
            onStatusChanged.accept(newStatus);
        }
    }

    /** Status: performing a full scan of existing files. */
    public static final String STATUS_EMITTING_INITIAL = "EMITTING_INITIAL";
    /** Status: watching for incremental changes. */
    public static final String STATUS_EMITTING_UPDATES = "EMITTING_UPDATES";
    /** Status: no event for 30 seconds, idle watching. */
    public static final String STATUS_IDLE = "IDLE";
    /** Status: hash filter rejected a file (unchanged / already known). */
    public static final String STATUS_FILTERED = "FILTERED";
    /** Status: scanner encountered an error. */
    public static final String STATUS_ERROR = "ERROR";

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
     * <p>
     * Status transitions: EMITTING_INITIAL (before scan, hash filter runs)
     * → EMITTING_UPDATES (after scan completes).
     */
    public void resetToFullScan() {
        log.info("Resetting scanner to full scan at: {}", folderPath);
        notifyStatusChange(STATUS_EMITTING_INITIAL);
        scanAllFiles();
        notifyStatusChange(STATUS_EMITTING_UPDATES);
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
                                // Trigger filtered status for hash-rejected file
                                if (onStatusChanged != null) {
                                    if (filteredResetTask != null) {
                                        filteredResetTask.cancel(false);
                                    }
                                    onStatusChanged.accept(STATUS_FILTERED);
                                    final String agentIdForReset = effectiveAgentId;
                                    filteredResetTask = filteredResetScheduler.schedule(
                                            () -> onStatusChanged.accept(STATUS_IDLE), 2, TimeUnit.SECONDS);
                                }
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
        // Cancel any pending FILTERED reset task
        if (filteredResetTask != null) {
            filteredResetTask.cancel(false);
        }
        filteredResetScheduler.shutdownNow();
        // Stop the idle checker
        idleChecker.shutdownNow();
        try {
            if (!idleChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Idle checker for agent {} did not terminate within timeout", effectiveAgentId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    /**
     * Convert this scanner to a public DTO.
     *
     * @return a ScannerInfo DTO
     */
    public com.hdekker.ai_workflow.rest.dto.ScannerInfo toInfo() {
        return new com.hdekker.ai_workflow.rest.dto.ScannerInfo(
                effectiveAgentId,
                effectiveAgentId,
                folderPath,
                status,
                createdAt,
                lastEmittedAt,
                errorMessage
        );
    }
}
