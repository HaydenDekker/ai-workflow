package com.hdekker.ai_workflow.application.scanner;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.application.file.FileComparator;
import com.hdekker.ai_workflow.application.file.port.FileCounterPort;
import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.file.FileMetadata;
import com.hdekker.ai_workflow.domain.scanner.RawFileEvent;
import com.hdekker.ai_workflow.domain.scanner.ScannerEventType;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;
import com.hdekker.ai_workflow.domain.shared.FileHash;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Scanner service — the central application-layer orchestrator for file watching.
 * <p>
 * Owns scanner lifecycle status, error handling, and DTO conversion.
 * Delegates all observability concerns (metrics recording + event publishing)
 * to {@link ScannerObservabilityUseCase} — the single entry point for observability.
 * <p>
 * Subscribes to raw {@link RawFileEvent} from the port and applies business logic:
 * <ul>
 *   <li>Computes hash via {@link FileHash#hash(String)}</li>
 *   <li>Creates {@link FileMetadata} and compares via {@link FileComparator}</li>
 *   <li>Produces {@link FileHistory} and decides whether to emit</li>
 *   <li>Delegates metrics + event publishing to {@link ScannerObservabilityUseCase}</li>
 *   <li>Applies emission delay throttling</li>
 * </ul>
 *
 * @see ScannerObservabilityUseCase
 */
public class ScannerService implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(ScannerService.class);

    private final String folderPath;
    private final String effectiveAgentId;
    private final Duration emissionDelay;
    private final FileWatcherPort fileWatcherPort;
    private final FileComparator fileComparator;
    private final ScannerObservabilityUseCase observability;
    private final FileCounterPort fileCounter;

    private volatile boolean disposed = false;

    // -- Status state --
    private volatile ScannerStatus status = ScannerStatus.IDLE;
    private volatile String errorMessage;
    private volatile LocalDateTime lastEmittedAt;
    private final LocalDateTime createdAt;

    // -- Emission throttle state --
    private volatile LocalDateTime lastEmissionTime;
    private volatile FileHistory latestBufferedHistory;
    private volatile boolean scanBufferedAnyFile = false;

    // -- Scanner's own sink for FileHistory --
    private final Sinks.Many<FileHistory> fileHistorySink;

    /**
     * Creates a new ScannerService.
     *
     * @param agentId           owning agent's ID (used for metric tagging)
     * @param folderPath        absolute path to watch
     * @param delayBetweenReads poll interval for the watch service
     * @param emissionDelay     minimum interval between consecutive file emissions
     * @param fileWatcherPort   file watching infrastructure port
     * @param fileComparator    metadata comparison utility (application layer)
     * @param fileCounter       file counter port for computing file counts
     * @param observability     orchestrator for metrics recording + event publishing
     */
    public ScannerService(String agentId,
                          String folderPath,
                          Duration delayBetweenReads,
                          Duration emissionDelay,
                          FileWatcherPort fileWatcherPort,
                          FileComparator fileComparator,
                          FileCounterPort fileCounter,
                          ScannerObservabilityUseCase observability) {
        this.folderPath = folderPath;
        this.effectiveAgentId = agentId != null ? agentId : folderPath;
        this.emissionDelay = emissionDelay;
        this.fileWatcherPort = fileWatcherPort;
        this.fileComparator = fileComparator;
        this.fileCounter = fileCounter;
        this.observability = observability;
        this.createdAt = LocalDateTime.now();
        this.lastEmissionTime = LocalDateTime.now();

        // Scanner's own sink for FileHistory
        this.fileHistorySink = Sinks.many().multicast().directBestEffort();

        // Subscribe to raw events from the port and apply business logic
        subscribeToRawEvents();

        log.debug("Scanner created for agent {} (folder={})", effectiveAgentId, folderPath);
    }

    /**
     * Subscribe to raw events from the port and apply business logic.
     */
    private void subscribeToRawEvents() {
        fileWatcherPort.flux().subscribe(
                rawEvent -> processRawEvent(rawEvent),
                error -> {
                    log.error("Error in raw event subscription for agent {}: {}",
                            effectiveAgentId, error.getMessage());
                    observability.transitionToError(effectiveAgentId, "raw event error");
                }
        );
    }

    /**
     * Process a raw file event: apply hashing, comparison, and emission logic.
     * <p>
     * Delegates all observability concerns (metrics recording + event publishing)
     * to {@link ScannerObservabilityUseCase} instead of calling ports directly.
     */
    private void processRawEvent(RawFileEvent rawEvent) {
        Path path = rawEvent.path();
        String content = rawEvent.content();

        try {
            // Handle DELETE events — content is not available
            if (rawEvent.eventType() == RawFileEvent.RawFileEventType.DELETE) {
                long fileCount = countFiles();
                observability.recordFileEvent(effectiveAgentId, ScannerEventType.DELETION,
                        fileCount, folderPath);
                log.debug("Deleted file: {}", path);
                return;
            }

            String hash = FileHash.hash(content);
            Path directory = fileWatcherPort.getDirectory();
            String relativePath = directory.relativize(path).toString().replace("\\", "/");
            FileMetadata metadata = new FileMetadata(relativePath, content, hash);
            FileHistory history = fileComparator.matches(metadata);

            if (!history.hashMatches()) {
                ScannerEventType eventType = history.previousFile().isEmpty()
                        ? ScannerEventType.CREATION : ScannerEventType.MODIFICATION;
                long fileCount = countFiles();
                observability.recordFileEvent(effectiveAgentId, eventType, fileCount, folderPath);
                log.debug("{} file: {}", eventType == ScannerEventType.CREATION
                        ? "New" : "Changed", relativePath);
                emitWithDelay(history);
            } else {
                long fileCount = countFiles();
                observability.recordFileEvent(effectiveAgentId, ScannerEventType.UNCHANGED,
                        fileCount, folderPath);
                log.debug("Unchanged file (skipped): {}", relativePath);
            }
        } catch (Exception e) {
            log.warn("Failed to process raw event for path {}: {}", path, e.getMessage());
            observability.transitionToError(effectiveAgentId, e.getMessage());
        }
    }

    /**
     * Attempt to emit a file history through the scanner's sink,
     * respecting the emission delay.
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
     */
    public boolean scanBufferedAnyFile() {
        return scanBufferedAnyFile;
    }

    /**
     * Handle the emission callback — record emission via use case.
     */
    private void onEmitCallback() {
        this.lastEmittedAt = LocalDateTime.now();
        observability.recordEmission(effectiveAgentId);
    }

    /**
     * Initialise the file watcher for watching the target directory.
     * <p>
     * Uses {@link ScannerObservabilityUseCase} for lifecycle event recording
     * (EMITTING_INITIAL, ERROR) — the only lifecycle statuses that require
     * observability recording.
     */
    public void initSource(String effectiveAgentId) {
        try {
            log.info("Setting up scanner for folder: {}", fileWatcherPort.getDirectory());

            // Compute initial file count
            long initialFileCount = countFiles();

            // Transition to EMITTING_INITIAL before the initial full scan
            this.status = ScannerStatus.EMITTING_INITIAL;

            fileWatcherPort.start();

            // Flush any buffered files
            flushBufferedEmission();

            // Transition to EMITTING_UPDATES only if files were buffered
            if (scanBufferedAnyFile) {
                this.status = ScannerStatus.EMITTING_UPDATES;
            } else {
                this.status = ScannerStatus.IDLE;
                log.info("Scanner initialised for folder: {} - no new files, staying IDLE",
                        folderPath);
            }

            // Store the initial file count via use case
            observability.recordFileEvent(effectiveAgentId, null, initialFileCount, folderPath);

            log.info("Scanner initialised for folder: {}", folderPath);

        } catch (Exception e) {
            log.error("Failed to initialise scanner for folder: {}", folderPath, e);
            observability.transitionToError(effectiveAgentId, e.getMessage());
        }
    }

    /**
     * Update the scanner's status.
     */
    public void updateStatus(ScannerStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * Get the current scanner status.
     */
    public ScannerStatus getStatus() {
        return status;
    }

    /**
     * Transition this scanner to the ERROR state.
     * <p>
     * Uses {@link ScannerObservabilityUseCase#transitionToError} for observability.
     */
    public void transitionToError(String reason) {
        this.errorMessage = reason;
        this.status = ScannerStatus.ERROR;
        observability.transitionToError(effectiveAgentId, reason);
        log.error("Scanner for agent {} entered ERROR state: {}", effectiveAgentId, reason);
    }

    /**
     * Recover from the ERROR state.
     */
    public void recover() {
        this.errorMessage = null;
        this.status = ScannerStatus.IDLE;
        log.info("Recovered scanner for agent {} from ERROR state", effectiveAgentId);
    }

    /**
     * Get the error message, if any.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Record that a file was emitted, delegating to the observability use case.
     * <p>
     * Preserves the existing fileCount so emission events don't reset it.
     */
    public void recordEmission() {
        this.lastEmittedAt = LocalDateTime.now();
        observability.recordEmission(effectiveAgentId);
        log.debug("Recorded emission for agent {} - resetting idle timer", effectiveAgentId);
    }

    /**
     * Get the last emission timestamp.
     */
    public LocalDateTime getLastEmittedAt() {
        return lastEmittedAt;
    }

    /**
     * Get the creation timestamp.
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // -- Backward-compatible string constants --

    public static final String STATUS_EMITTING_INITIAL = ScannerStatus.EMITTING_INITIAL.name();
    public static final String STATUS_EMITTING_UPDATES = ScannerStatus.EMITTING_UPDATES.name();
    public static final String STATUS_IDLE = ScannerStatus.IDLE.name();
    public static final String STATUS_FILTERED = ScannerStatus.FILTERED.name();
    public static final String STATUS_ERROR = ScannerStatus.ERROR.name();

    /**
     * Returns the flux of file changes.
     */
    @Override
    public Flux<FileHistory> flux() {
        return fileHistorySink.asFlux().onBackpressureBuffer();
    }

    /**
     * Reset the scanner to full-scan mode.
     * <p>
     * Uses direct status updates for lifecycle transitions and the use case
     * for lifecycle event recording.
     */
    public void resetToFullScan() {
        log.info("Resetting scanner to full scan at: {}", folderPath);
        this.status = ScannerStatus.EMITTING_INITIAL;

        try {
            if (!java.nio.file.Files.exists(Path.of(folderPath))) {
                log.warn("Target folder does not exist: {}", folderPath);
                observability.transitionToError(effectiveAgentId, "folder does not exist");
                return;
            }

            scanBufferedAnyFile = false;
            fileWatcherPort.rawScan();
            flushBufferedEmission();

        } catch (Exception e) {
            log.error("Failed to walk folder during full scan: {}", folderPath, e);
            observability.transitionToError(effectiveAgentId, e.getMessage());
            return;
        }

        this.status = ScannerStatus.EMITTING_UPDATES;
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
        fileWatcherPort.stop();

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
     * Get the effective agent ID used for metrics.
     */
    public String getEffectiveAgentId() {
        return effectiveAgentId;
    }

    /**
     * Count the number of files in the watched folder.
     */
    private long countFiles() {
        try {
            return fileCounter.countFiles(folderPath);
        } catch (Exception e) {
            log.warn("Failed to count files for folder {}: {}", folderPath, e.getMessage());
            return 0L;
        }
    }

    /**
     * Convert this scanner to a public DTO.
     * <p>
     * Note: Returns an internal record for backward compatibility.
     * The adapter layer converts this to the final DTO.
     *
     * @return an info record
     */
    public ScannerInfo toInfo() {
        return new ScannerInfo(
                effectiveAgentId,
                effectiveAgentId,
                folderPath,
                status.name(),
                createdAt,
                lastEmittedAt,
                errorMessage
        );
    }

    /**
     * Record for scanner info — adapter layer converts to REST DTO.
     */
    public static class ScannerInfo {
        private final String agentId;
        private final String id;
        private final String folderPath;
        private final String status;
        private final LocalDateTime createdAt;
        private final LocalDateTime lastEmittedAt;
        private final String errorMessage;

        public ScannerInfo(String agentId, String id, String folderPath,
                           String status, LocalDateTime createdAt,
                           LocalDateTime lastEmittedAt, String errorMessage) {
            this.agentId = agentId;
            this.id = id;
            this.folderPath = folderPath;
            this.status = status;
            this.createdAt = createdAt;
            this.lastEmittedAt = lastEmittedAt;
            this.errorMessage = errorMessage;
        }

        public String agentId() { return agentId; }
        public String id() { return id; }
        public String folderPath() { return folderPath; }
        public String status() { return status; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime lastEmittedAt() { return lastEmittedAt; }
        public String errorMessage() { return errorMessage; }
    }
}
