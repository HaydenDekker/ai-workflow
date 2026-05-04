package com.hdekker.ai_workflow.usecases;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;
import com.hdekker.ai_workflow.files.*;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Domain scanner — the central concept for file watching.
 * <p>
 * Owns status, idle timer, error handling, metrics publishing, DTO conversion,
 * and the FileHash/FileHistory business rules. Composes {@link NativeFileWatcherAdapter}.
 * <p>
 * Subscribes to raw {@link RawFileEvent} from the adapter and applies business logic:
 * <ul>
 *   <li>Computes hash via {@link FileHash#hash(String)}</li>
 *   <li>Creates {@link FileMetadata} and compares via {@link FileComparator}</li>
 *   <li>Produces {@link FileHistory} and decides whether to emit</li>
 *   <li>Saves metadata to {@link FileMetadataStore} on discovery</li>
 *   <li>Tracks metrics via {@link ScannerObserverUseCase}</li>
 *   <li>Applies emission delay throttling</li>
 * </ul>
 */
public class Scanner implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(Scanner.class);

    private final String folderPath;
    private final String effectiveAgentId;
    private final Duration emissionDelay;
    private final FileMetadataStore fileMetadataStore;
    private final FileComparator fileComparator;

    private final NativeFileWatcherAdapter nativeFileWatcher;
    private final ScannerObserverUseCase observer;
    private volatile boolean disposed = false;

    // -- Status state (moved from ScannerRegistry) --
    private volatile ScannerStatus status = ScannerStatus.IDLE;
    private volatile String errorMessage;
    private volatile LocalDateTime lastEmittedAt;
    private final LocalDateTime createdAt;

    /** Seconds of inactivity before transitioning from EMITTING_UPDATES to IDLE. */
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);
    /** Interval for the idle-checker scheduler. */
    private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(10);

    // -- Idle checker (moved from ScannerRegistry) --
    private final ScheduledExecutorService idleChecker;

    private final ScheduledExecutorService filteredResetScheduler;
    private volatile java.util.concurrent.ScheduledFuture<?> filteredResetTask;

    // -- Emission throttle state (moved from NativeFileWatcherAdapter) --
    private volatile LocalDateTime lastEmissionTime;
    private volatile FileHistory latestBufferedHistory;
    private volatile boolean scanBufferedAnyFile = false;

    // -- Scanner's own sink for FileHistory (consumers subscribe to this) --
    private final Sinks.Many<FileHistory> fileHistorySink;

    /**
     * Creates a new Scanner.
     *
     * @param agentId             owning agent's ID (used for metric tagging)
     * @param folderPath          absolute path to watch
     * @param delayBetweenReads   poll interval for the watch service
     * @param emissionDelay       minimum interval between consecutive file emissions
     * @param fileMetadataStore   metadata for change detection
     * @param observer            scanner observer use case for metrics and UI push
     */
    public Scanner(String agentId,
                                     String folderPath,
                                     Duration delayBetweenReads,
                                     Duration emissionDelay,
                                     FileMetadataStore fileMetadataStore,
                                     ScannerObserverUseCase observer) {
        this.folderPath = folderPath;
        this.effectiveAgentId = agentId != null ? agentId : folderPath;
        this.emissionDelay = emissionDelay;
        this.fileMetadataStore = fileMetadataStore;
        this.fileComparator = new FileComparator(fileMetadataStore);
        this.observer = observer;
        this.createdAt = LocalDateTime.now();
        this.lastEmissionTime = LocalDateTime.now();

        // Idle checker — monitors this scanner for inactivity
        this.idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scanner-idle-checker-" + this.effectiveAgentId);
            t.setDaemon(true);
            return t;
        });
        startIdleChecker();

        // Scheduled executor for resetting FILTERED status back to IDLE
        this.filteredResetScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "filtered-reset");
            t.setDaemon(true);
            return t;
        });

        // Scanner's own sink for FileHistory — consumers (pipeline) subscribe to this
        this.fileHistorySink = Sinks.many().multicast().directBestEffort();

        // Create the native file watcher adapter (pure infrastructure — no business rules)
        this.nativeFileWatcher = new NativeFileWatcherAdapter(
                Path.of(folderPath), delayBetweenReads);

        // Subscribe to raw events from the adapter and apply business logic
        subscribeToRawEvents();

        log.debug("Scanner created for agent {} (folder={})", effectiveAgentId, folderPath);
    }


    /**
     * Subscribe to raw events from the adapter and apply business logic.
     * <p>
     * For each raw event:
     * <ol>
     *   <li>Compute hash via FileHash</li>
     *   <li>Create FileMetadata</li>
     *   <li>Compare with stored metadata via FileComparator → FileHistory</li>
     *   <li>If changed (hash mismatch): save metadata, apply emission delay, emit FileHistory</li>
     *   <li>If unchanged: record unchanged, set FILTERED status</li>
     * </ol>
     */
    private void subscribeToRawEvents() {
        nativeFileWatcher.flux().subscribe(
                rawEvent -> processRawEvent(rawEvent),
                error -> {
                    log.error("Error in raw event subscription for agent {}: {}",
                            effectiveAgentId, error.getMessage());
                    observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                            effectiveAgentId, ScannerStatus.ERROR, null, null,
                            "Error processing raw event: " + error.getMessage()));
                }
        );
    }

    /**
     * Process a raw file event: apply hashing, comparison, and emission logic.
     */
    private void processRawEvent(RawFileEvent rawEvent) {
        Path path = rawEvent.path();
        String content = rawEvent.content();

        try {
            // Handle DELETE events — content is not available
            if (rawEvent.eventType() == RawFileEvent.RawFileEventType.DELETE) {
                observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                        effectiveAgentId, ScannerStatus.EMITTING_UPDATES, ScannerEventType.DELETION, folderPath, null));
                log.debug("Deleted file: {}", path);
                return;
            }

            String hash = FileHash.hash(content);
            Path directory = nativeFileWatcher.getDirectory();
            String relativePath = directory.relativize(path).toString().replace("\\", "/");
            FileMetadata metadata = new FileMetadata(relativePath, content, hash);
            FileHistory history = fileComparator.matches(metadata);

            if (!history.hashMatches()) {
                ScannerEventType eventType = history.previousFile().isEmpty()
                        ? ScannerEventType.CREATION : ScannerEventType.MODIFICATION;
                observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                        effectiveAgentId, ScannerStatus.EMITTING_UPDATES, eventType, folderPath, null));
                log.debug("{} file: {}", eventType == ScannerEventType.CREATION ? "New" : "Changed", relativePath);
                fileMetadataStore.save(metadata);
                emitWithDelay(history);
            } else {
                observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                        effectiveAgentId, ScannerStatus.FILTERED, ScannerEventType.UNCHANGED, folderPath, null));
                cancelAndScheduleFilteredReset();
                log.debug("Unchanged file (skipped): {}", relativePath);
            }
        } catch (Exception e) {
            log.warn("Failed to process raw event for path {}: {}", path, e.getMessage());
            observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                    effectiveAgentId, ScannerStatus.ERROR, null, null,
                    "Failed to process raw event: " + e.getMessage()));
        }
    }

    /**
     * Attempt to emit a file history through the scanner's sink, respecting the emission delay.
     * <p>
     * If the delay has not elapsed since the last emission, the history is buffered
     * and will be emitted when the delay elapses (or on the next call).
     *
     * @param history the file history to emit
     */
    private void emitWithDelay(FileHistory history) {
        if (history == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Coalesce: always update the buffered history
        latestBufferedHistory = history;

        if (emissionDelay == null || emissionDelay.isZero() || emissionDelay.isNegative()) {
            // No delay configured — emit immediately
            fileHistorySink.tryEmitNext(history);
            lastEmissionTime = now;
            scanBufferedAnyFile = true;
            onEmitCallback();
            return;
        }

        Duration elapsed = Duration.between(lastEmissionTime, now);
        if (elapsed.getSeconds() >= emissionDelay.getSeconds()) {
            // Delay has elapsed — emit and record time
            fileHistorySink.tryEmitNext(history);
            lastEmissionTime = now;
            scanBufferedAnyFile = true;
            onEmitCallback();
        }
        // else: delay not elapsed, history is buffered for later emission
    }

    /**
     * Flush any buffered history if the emission delay has elapsed.
     */
    public void flushBufferedEmission() {
        if (latestBufferedHistory == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Duration elapsed = Duration.between(lastEmissionTime, now);

        if (elapsed.getSeconds() >= emissionDelay.getSeconds()) {
            fileHistorySink.tryEmitNext(latestBufferedHistory);
            lastEmissionTime = now;
            latestBufferedHistory = null;
            onEmitCallback();
        }
    }

    /**
     * Check whether any files were buffered during the initial scan.
     * Used to determine whether to transition to EMITTING_UPDATES or stay IDLE.
     *
     * @return true if at least one file was buffered during the initial scan
     */
    public boolean scanBufferedAnyFile() {
        return scanBufferedAnyFile;
    }

    /**
     * Cancel any pending FILTERED reset task and schedule a new one.
     * Sets status to FILTERED, then resets to IDLE after 2 seconds.
     */
    private void cancelAndScheduleFilteredReset() {
        if (filteredResetTask != null) {
            filteredResetTask.cancel(false);
        }
        notifyStatusChange(ScannerStatus.FILTERED);
        filteredResetTask = filteredResetScheduler.schedule(
                () -> notifyStatusChange(ScannerStatus.IDLE), 2, TimeUnit.SECONDS);
    }

    /**
     * Handle the emission callback — record emission with observer and reset idle timer.
     */
    private void onEmitCallback() {
        this.lastEmittedAt = LocalDateTime.now();
        observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                effectiveAgentId, ScannerStatus.EMITTING_UPDATES, null, null, null));
    }

    /**
     * Initialise the native file watcher for watching the target directory.
     * <p>
     * Status transitions happen here so they occur after the hash filter
     * in {@code rawScan()} has processed all existing files:
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
            notifyStatusChange(ScannerStatus.EMITTING_INITIAL);

            nativeFileWatcher.start();

            // Flush any buffered files so they are emitted immediately if the
            // emission delay has elapsed.
            flushBufferedEmission();

            // Transition to EMITTING_UPDATES only if files were buffered (meaning
            // the hash filter found at least one changed/new file). If nothing
            // was buffered, all files are already known — stay IDLE.
            if (scanBufferedAnyFile) {
                notifyStatusChange(ScannerStatus.EMITTING_UPDATES);
            } else {
                notifyStatusChange(ScannerStatus.IDLE);
                log.info("Scanner initialised for folder: {} – no new files, staying IDLE", folderPath);
            }

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            String errorMsg = "Failed to initialise scanner: " + e.getMessage();
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
            observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                    effectiveAgentId, ScannerStatus.ERROR, null, null, errorMsg));
        }
    }

    /**
     * Update the scanner's status.
     * <p>
     * Updates the internal status field only. Does not trigger callbacks —
     * this method is the target of the {@code onStatusChanged} callback chain
     * so calling the callback here would cause infinite recursion.
     *
     * @param newStatus the new status (enum)
     */
    public void updateStatus(ScannerStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * Get the current scanner status.
     *
     * @return the current status enum
     */
    public ScannerStatus getStatus() {
        return status;
    }

    /**
     * Transition this scanner to the ERROR state.
     * Records both the status change and the error with the observer.
     *
     * @param reason human-readable description of the error
     */
    public void transitionToError(String reason) {
        this.errorMessage = reason;
        observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                effectiveAgentId, ScannerStatus.ERROR, null, null, reason));
        notifyStatusChange(ScannerStatus.ERROR);
        log.error("Scanner for agent {} entered ERROR state: {}", effectiveAgentId, reason);
    }

    /**
     * Recover from the ERROR state.
     * Resets status to IDLE and clears the error message.
     */
    public void recover() {
        this.errorMessage = null;
        observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                effectiveAgentId, ScannerStatus.IDLE, null, null, null));
        notifyStatusChange(ScannerStatus.IDLE);
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
     * Record that a file was emitted, resetting the idle timer.
     */
    public void recordEmission() {
        this.lastEmittedAt = LocalDateTime.now();
        observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                effectiveAgentId, ScannerStatus.EMITTING_UPDATES, null, null, null));
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
        if (status != ScannerStatus.EMITTING_UPDATES) {
            return;
        }

        // If we have a lastEmittedAt, check whether it's older than the idle timeout
        LocalDateTime lastEmit = lastEmittedAt;
        if (lastEmit != null) {
            Duration sinceLastEmission = Duration.between(lastEmit, LocalDateTime.now());
            if (sinceLastEmission.compareTo(IDLE_TIMEOUT) >= 0) {
                notifyStatusChange(ScannerStatus.IDLE);
                log.info("Scanner for agent {} transitioned to IDLE after {}s of inactivity",
                        effectiveAgentId, sinceLastEmission.getSeconds());
            }
        }
    }

    /**
     * Update the scanner's status and notify the observer.
     * The observer pushes the status change to the UI.
     *
     * @param newStatus the new status enum
     */
    private void notifyStatusChange(ScannerStatus newStatus) {
        this.status = newStatus;
        observer.pushToUI(new ScannerMetricsChangedEvent(
                effectiveAgentId, newStatus, null, null, null));
    }

    // -- Backward-compatible string constants (aliased to enum values) --

    /** Status: performing a full scan of existing files. */
    public static final String STATUS_EMITTING_INITIAL = ScannerStatus.EMITTING_INITIAL.name();
    /** Status: watching for incremental changes. */
    public static final String STATUS_EMITTING_UPDATES = ScannerStatus.EMITTING_UPDATES.name();
    /** Status: no event for 30 seconds, idle watching. */
    public static final String STATUS_IDLE = ScannerStatus.IDLE.name();
    /** Status: hash filter rejected a file (unchanged / already known). */
    public static final String STATUS_FILTERED = ScannerStatus.FILTERED.name();
    /** Status: scanner encountered an error. */
    public static final String STATUS_ERROR = ScannerStatus.ERROR.name();

    /**
     * Returns the flux of file changes. Subscribers receive incremental updates
     * from the watch service.
     */
    @Override
    public Flux<FileHistory> flux() {
        return fileHistorySink.asFlux().onBackpressureBuffer();
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
        notifyStatusChange(ScannerStatus.EMITTING_INITIAL);

        try {
            Path folder = Path.of(folderPath).toAbsolutePath();
            if (!Files.exists(folder)) {
                log.warn("Target folder does not exist: {}", folderPath);
                observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                        effectiveAgentId, ScannerStatus.ERROR, null, null,
                        "Target folder does not exist: " + folderPath));
                return;
            }

            // Reset buffered file tracking for the new scan
            scanBufferedAnyFile = false;

            // Trigger raw scan through the adapter — raw events flow through
            // the subscription and business logic is applied automatically
            nativeFileWatcher.rawScan();

            // Flush any buffered files
            flushBufferedEmission();

        } catch (IOException e) {
            String errorMsg = "Failed to walk folder during full scan: " + e.getMessage();
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
            observer.recordScannerEvent(new ScannerMetricsChangedEvent(
                    effectiveAgentId, ScannerStatus.ERROR, null, null, errorMsg));
        }

        notifyStatusChange(ScannerStatus.EMITTING_UPDATES);
        log.info("Full scan complete for: {}", folderPath);
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
        // Complete the FileHistory sink
        fileHistorySink.tryEmitComplete();
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
     * Convert this scanner to a public DTO.
     *
     * @return a ScannerInfo DTO
     */
    public com.hdekker.ai_workflow.rest.dto.ScannerInfo toInfo() {
        return new com.hdekker.ai_workflow.rest.dto.ScannerInfo(
                effectiveAgentId,
                effectiveAgentId,
                folderPath,
                status.name(),
                createdAt,
                lastEmittedAt,
                errorMessage
        );
    }
}
