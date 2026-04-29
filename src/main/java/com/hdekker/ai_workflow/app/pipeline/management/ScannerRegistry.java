package com.hdekker.ai_workflow.app.pipeline.management;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.EmissionDelayConfig;
import com.hdekker.ai_workflow.files.FileSystemScannerAdapter;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * Full implementation of the scanner registry.
 * Manages the lifecycle of {@link FileSystemScannerAdapter} instances, one per agent.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Create scanner instances for agents (one-to-one mapping)</li>
 *   <li>Track scanner status lifecycle (IDLE ↔ EMITTING_INITIAL ↔ EMITTING_UPDATES ↔ ERROR)</li>
 *   <li>Support full-scan reset via {@link #refreshAgent(String)}</li>
 *   <li>Thread-safe access via ConcurrentHashMap</li>
 *   <li>Automatic idle detection via a shared scheduled executor</li>
 *   <li>Error handling with {@link #transitionToError(String, String)} and recovery via {@link #recoverFromError(String)}</li>
 * </ul>
 * <p>
 * Scanner statuses:
 * <ul>
 *   <li>IDLE — no file system event for 30 seconds; watching, waiting for events</li>
 *   <li>EMITTING_INITIAL — performing a full scan (all existing files emitted)</li>
 *   <li>EMITTING_UPDATES — file system event detected (CREATE, MODIFY, DELETE)</li>
 *   <li>ERROR — scanner encountered an unrecoverable error; manual recovery required</li>
 * </ul>
 * <p>
 * Status lifecycle:
 * <pre>
 *   EMITTING_INITIAL ──(full scan complete)──► EMITTING_UPDATES
 *   EMITTING_UPDATES ──(30s no events)──► IDLE
 *   IDLE ──(event detected)──► EMITTING_UPDATES
 *   Any state ──(exception)──► ERROR
 * </pre>
 */
@Component
public class ScannerRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistry.class);

    /** Status string: performing a full scan of existing files. */
    public static final String STATUS_EMITTING_INITIAL = "EMITTING_INITIAL";
    /** Status string: watching for incremental changes. */
    public static final String STATUS_EMITTING_UPDATES = "EMITTING_UPDATES";
    /** Status string: no event for 30 seconds, idle watching. */
    public static final String STATUS_IDLE = "IDLE";
    /** Status string: scanner encountered an error. */
    public static final String STATUS_ERROR = "ERROR";

    /** Seconds of inactivity before transitioning from EMITTING_UPDATES to IDLE. */
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);
    /** Interval for the shared idle-checker scheduler. */
    private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(10);

    /**
     * Internal metadata for a registered scanner.
     * Keyed by agentId (one-to-one: each scanner owned by exactly one agent).
     */
    private record ScannerMetadata(
            FileSystemScannerAdapter scanner,
            String agentId,
            String folderPath,
            String status,
            LocalDateTime createdAt,
            LocalDateTime lastEmittedAt,
            String errorMessage
    ) {
        ScannerMetadata withStatus(String newStatus) {
            return new ScannerMetadata(scanner, agentId, folderPath, newStatus, createdAt, lastEmittedAt, errorMessage);
        }

        ScannerMetadata withError(String errorMsg) {
            return new ScannerMetadata(scanner, agentId, folderPath, STATUS_ERROR, createdAt, lastEmittedAt, errorMsg);
        }
    }

    private final ConcurrentHashMap<String, ScannerMetadata> scanners = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    private final FileMetadataDatabase fileMetadataDatabase;
    private final ScannerObserverUseCase observer;
    private final Consumer<ScannerMetricsChangedEvent> metricsEventPublisher;
    private final EmissionDelayConfig emissionDelayConfig;
    private final ScheduledExecutorService idleChecker;

    /**
     * Creates a new ScannerRegistry with the required Spring dependencies.
     * <p>
     * Uses the default emission delay if {@code emissionDelayConfig} is null.
     *
     * @param applicationContext            Spring application context
     * @param fileMetadataDatabase          Database for file metadata change detection
     * @param observer                      scanner observer use case for metrics tracking
     * @param metricsEventPublisher         Callback to publish metrics change events
     * @param emissionDelayConfig           Configuration for emission delay behaviour (nullable)
     */
    @Autowired
    public ScannerRegistry(
            ApplicationContext applicationContext,
            FileMetadataDatabase fileMetadataDatabase,
            ScannerObserverUseCase observer,
            Consumer<ScannerMetricsChangedEvent> metricsEventPublisher,
            EmissionDelayConfig emissionDelayConfig) {
        this.applicationContext = applicationContext;
        this.fileMetadataDatabase = fileMetadataDatabase;
        this.observer = observer;
        this.metricsEventPublisher = metricsEventPublisher;
        this.emissionDelayConfig = emissionDelayConfig != null
                ? emissionDelayConfig
                : new EmissionDelayConfig(EmissionDelayConfig.DEFAULT_DELAY_SECONDS);
        this.idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scanner-idle-checker");
            t.setDaemon(true);
            return t;
        });
        startIdleChecker();
    }

    /**
     * Creates a new ScannerRegistry without emission delay configuration.
     * <p>
     * Defaults to the standard emission delay and starts the idle checker.
     * Useful for tests that don't need emission delay behaviour.
     *
     * @param applicationContext            Spring application context
     * @param fileMetadataDatabase          Database for file metadata change detection
     * @param observer                      scanner observer use case for metrics tracking
     * @param metricsEventPublisher         Callback to publish metrics change events
     */
    public ScannerRegistry(
            ApplicationContext applicationContext,
            FileMetadataDatabase fileMetadataDatabase,
            ScannerObserverUseCase observer,
            Consumer<ScannerMetricsChangedEvent> metricsEventPublisher) {
        this(applicationContext, fileMetadataDatabase, observer, metricsEventPublisher, null);
    }

    /**
     * Create a scanner for the given agent.
     * Validates that the target directory exists before creating the scanner.
     *
     * @param agentId          the owning agent's ID
     * @param targetDirectory  the absolute path to watch
     * @param delaySeconds     poll delay in seconds
     * @return the created ScannerInfo
     * @throws IllegalArgumentException if the target directory does not exist
     */
    public ScannerInfo createForAgent(String agentId, String targetDirectory, int delaySeconds) {
        // Validate folder exists
        java.nio.file.Path folderPath = java.nio.file.Path.of(targetDirectory).toAbsolutePath();
        if (!java.nio.file.Files.exists(folderPath)) {
            throw new IllegalArgumentException(
                    "Target directory does not exist: " + targetDirectory);
        }
        if (!java.nio.file.Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException(
                    "Target path is not a directory: " + targetDirectory);
        }
        if (!java.nio.file.Files.isReadable(folderPath)) {
            throw new IllegalArgumentException(
                    "Target directory is not readable: " + targetDirectory);
        }

        // Check for duplicate
        if (scanners.containsKey(agentId)) {
            log.warn("Scanner already exists for agent {}, returning existing", agentId);
            return toScannerInfo(scanners.get(agentId));
        }

        Duration delay = Duration.ofSeconds(delaySeconds);
        Duration emissionDelay = Duration.ofSeconds(emissionDelayConfig.getEmissionDelaySeconds());

        // Create the scanner adapter (pass agentId for metric tagging + observer + event publisher)
        final String agentIdForAdapter = agentId;
        FileSystemScannerAdapter scanner = new FileSystemScannerAdapter(
                agentId,
                targetDirectory,
                delay,
                emissionDelay,
                fileMetadataDatabase,
                observer,
                metricsEventPublisher,
                errMsg -> transitionToError(agentIdForAdapter, errMsg));

        ScannerMetadata metadata = new ScannerMetadata(
                scanner, agentId, targetDirectory, STATUS_EMITTING_INITIAL,
                LocalDateTime.now(), null, null);

        scanners.put(agentId, metadata);
        log.info("Created scanner {} for agent {} (target={}, delay={}s)",
                metadata, agentId, targetDirectory, delaySeconds);

        return toScannerInfo(metadata);
    }

    /**
     * Destroy a scanner by its ID.
     * Cleans up the integration flow registration and scanner resources.
     *
     * @param scannerId the scanner ID to destroy
     */
    public void destroyForAgent(String scannerId) {
        // Try to find by key first
        ScannerMetadata meta = scanners.remove(scannerId);
        
        if (meta == null) {
            // Try to find by agentId
            String keyToRemove = null;
            for (Map.Entry<String, ScannerMetadata> entry : scanners.entrySet()) {
                if (entry.getValue().agentId().equals(scannerId)) {
                    keyToRemove = entry.getKey();
                    meta = entry.getValue();
                    break;
                }
            }
            if (keyToRemove != null) {
                scanners.remove(keyToRemove);
            }
        }

        if (meta != null) {
            try {
                meta.scanner().destroy();
                log.info("Destroyed scanner {} for agent {}", meta.agentId(), scannerId);
            } catch (Exception e) {
                log.warn("Error destroying scanner {}: {}", scannerId, e.getMessage());
            }
        } else {
            log.warn("No scanner found for ID/agentId: {}", scannerId);
        }
    }

    /**
     * Refresh a scanner: reset it to full-scan mode.
     * Disposes the current subscription, clears the replay processor,
     * and re-emits all files from the target directory.
     *
     * @param scannerId the scanner ID to refresh
     */
    public void refreshAgent(String scannerId) {
        ScannerMetadata meta = scanners.values().stream()
                .filter(m -> m.agentId().equals(scannerId))
                .findFirst()
                .orElseGet(() -> {
                    for (var entry : scanners.entrySet()) {
                        if (entry.getKey().equals(scannerId)) {
                            return entry.getValue();
                        }
                    }
                    return null;
                });

        if (meta == null) {
            log.warn("Cannot refresh: no scanner found for ID/agentId: {}", scannerId);
            return;
        }

        // Reset scanner to full-scan mode
        meta.scanner().resetToFullScan();

        // Update status
        String key = scanners.entrySet().stream()
                .filter(e -> e.getValue().equals(meta))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(scannerId);

        ScannerMetadata updated = meta.withStatus(STATUS_EMITTING_INITIAL);
        scanners.put(key, updated);
        log.info("Refreshed scanner {} for agent {} to full-scan mode", scannerId, meta.agentId());
    }

    /**
     * Get the scanner's flux for processing.
     *
     * @param scannerId the scanner ID
     * @return the flux of file history events
     */
    public Flux<FileHistory> getScannerFlux(String scannerId) {
        ScannerMetadata meta = scanners.values().stream()
                .filter(m -> m.agentId().equals(scannerId))
                .findFirst()
                .orElseGet(() -> {
                    for (var entry : scanners.entrySet()) {
                        if (entry.getKey().equals(scannerId)) {
                            return entry.getValue();
                        }
                    }
                    return null;
                });

        if (meta == null) {
            log.warn("No scanner found for flux lookup: {}", scannerId);
            return Flux.empty();
        }

        // Update status to EMITTING_UPDATES after initial full scan
        updateStatus(scannerId, STATUS_EMITTING_UPDATES);

        return meta.scanner().flux();
    }

    /**
     * List all registered scanners.
     */
    public List<ScannerInfo> listAll() {
        return new ArrayList<>(scanners.values()).stream()
                .map(this::toScannerInfo)
                .toList();
    }

    /**
     * Get a scanner by agentId.
     */
    public Optional<ScannerInfo> getById(String agentId) {
        return Optional.ofNullable(scanners.get(agentId))
                .map(this::toScannerInfo);
    }

    /**
     * Delete a scanner by agentId.
     */
    public void deleteById(String agentId) {
        destroyForAgent(agentId);
    }

    /**
     * Delete a scanner by its own ID.
     */
    public void deleteById(String agentId, String scannerId) {
        destroyForAgent(scannerId);
    }

    /**
     * Update the status of a scanner.
     *
     * @param scannerId the agent ID or scanner ID
     * @param status    the new status string
     */
    public void updateStatus(String scannerId, String status) {
        ScannerMetadata meta = scanners.values().stream()
                .filter(m -> m.agentId().equals(scannerId))
                .findFirst()
                .orElseGet(() -> {
                    for (var entry : scanners.entrySet()) {
                        if (entry.getKey().equals(scannerId)) {
                            return entry.getValue();
                        }
                    }
                    return null;
                });

        if (meta != null) {
            String key = scanners.entrySet().stream()
                    .filter(e -> e.getValue().equals(meta))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(scannerId);
            ScannerMetadata updated = meta.withStatus(status);
            scanners.put(key, updated);
            log.debug("Updated scanner {} status to {}", scannerId, status);
        }
    }

    /**
     * Transition a scanner to the ERROR state.
     * <p>
     * Called by {@link FileSystemScannerAdapter} when the file watcher
     * encounters an unrecoverable error (e.g. directory becomes inaccessible).
     *
     * @param agentId the owning agent's ID
     * @param reason  human-readable description of the error
     */
    public void transitionToError(String agentId, String reason) {
        ScannerMetadata meta = scanners.values().stream()
                .filter(m -> m.agentId().equals(agentId))
                .findFirst()
                .orElseGet(() -> {
                    for (var entry : scanners.entrySet()) {
                        if (entry.getKey().equals(agentId)) {
                            return entry.getValue();
                        }
                    }
                    return null;
                });

        if (meta != null) {
            String key = scanners.entrySet().stream()
                    .filter(e -> e.getValue().equals(meta))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(agentId);
            ScannerMetadata updated = meta.withError(reason);
            scanners.put(key, updated);
            log.error("Scanner for agent {} entered ERROR state: {}", agentId, reason);
            // Notify UI
            pushMetricsEvent(ScannerMetricsChangedEvent.errorOccurred(agentId, reason));
        } else {
            log.warn("Cannot transition to error: no scanner found for agent {}", agentId);
        }
    }

    /**
     * Recover a scanner from the ERROR state.
     * <p>
     * Resets the status to {@code EMITTING_INITIAL}, clears the error message,
     * and triggers a full rescan of the target directory.
     *
     * @param agentId the owning agent's ID
     */
    public void recoverFromError(String agentId) {
        ScannerMetadata meta = scanners.values().stream()
                .filter(m -> m.agentId().equals(agentId))
                .findFirst()
                .orElseGet(() -> {
                    for (var entry : scanners.entrySet()) {
                        if (entry.getKey().equals(agentId)) {
                            return entry.getValue();
                        }
                    }
                    return null;
                });

        if (meta != null) {
            String key = scanners.entrySet().stream()
                    .filter(e -> e.getValue().equals(meta))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(agentId);
            ScannerMetadata updated = meta.withStatus(STATUS_EMITTING_INITIAL);
            updated = updated.withError(null);
            scanners.put(key, updated);
            // Trigger a full rescan
            meta.scanner().resetToFullScan();
            log.info("Recovered scanner for agent {} from ERROR state", agentId);
            pushMetricsEvent(ScannerMetricsChangedEvent.recoveredFromError(agentId));
        } else {
            log.warn("Cannot recover: no scanner found for agent {}", agentId);
        }
    }

    /**
     * Notify a scanner that an event has been emitted, resetting the idle timer.
     * <p>
     * Called from {@link ScannerObserverUseCase} callbacks so the idle checker
     * can accurately detect when a scanner has become idle.
     *
     * @param agentId the owning agent's ID
     */
    public void recordEmission(String agentId) {
        // Update lastEmittedAt in metadata
        ScannerMetadata meta = scanners.values().stream()
                .filter(m -> m.agentId().equals(agentId))
                .findFirst()
                .orElseGet(() -> {
                    for (var entry : scanners.entrySet()) {
                        if (entry.getKey().equals(agentId)) {
                            return entry.getValue();
                        }
                    }
                    return null;
                });

        if (meta != null && !STATUS_ERROR.equals(meta.status())) {
            String key = scanners.entrySet().stream()
                    .filter(e -> e.getValue().equals(meta))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(agentId);
            ScannerMetadata updated = new ScannerMetadata(
                    meta.scanner(), meta.agentId(), meta.folderPath(),
                    STATUS_EMITTING_UPDATES, meta.createdAt(),
                    LocalDateTime.now(), meta.errorMessage());
            scanners.put(key, updated);
            log.debug("Recorded emission for agent {} – resetting idle timer", agentId);
        }
    }

    /**
     * Convert internal metadata to public DTO.
     */
    private ScannerInfo toScannerInfo(ScannerMetadata meta) {
        return new ScannerInfo(
                meta.agentId(),
                meta.agentId(),
                meta.folderPath(),
                meta.status(),
                meta.createdAt(),
                meta.lastEmittedAt(),
                meta.errorMessage()
        );
    }

    /**
     * Get the error message for a scanner, if any.
     *
     * @param scannerId the agent ID or scanner ID
     * @return the error message, or empty if no error
     */
    public Optional<String> getErrorMessage(String scannerId) {
        return scanners.values().stream()
                .filter(m -> m.agentId().equals(scannerId))
                .findFirst()
                .map(ScannerMetadata::errorMessage);
    }

    /**
     * Get the internal metadata for a scanner (for testing/advanced use).
     */
    public Optional<ScannerMetadata> getMetadata(String scannerId) {
        return scanners.values().stream()
                .filter(m -> m.agentId().equals(scannerId) || scanners.entrySet().stream()
                        .anyMatch(e -> e.getValue().equals(m) && e.getKey().equals(scannerId)))
                .findFirst();
    }

    /**
     * Start the shared idle-checker that monitors all scanners for inactivity.
     */
    private void startIdleChecker() {
        idleChecker.scheduleAtFixedRate(this::checkAllScannersForIdle,
                IDLE_CHECK_INTERVAL.toSeconds(),
                IDLE_CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
        log.info("Idle checker started (interval={}s)", IDLE_CHECK_INTERVAL.getSeconds());
    }

    /**
     * Check every registered scanner. If a scanner is in EMITTING_UPDATES and
     * has not emitted for IDLE_TIMEOUT, transition it to IDLE.
     */
    private void checkAllScannersForIdle() {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, ScannerMetadata> entry : scanners.entrySet()) {
            ScannerMetadata meta = entry.getValue();
            String agentId = meta.agentId();

            // Only check scanners that are actively emitting
            if (!STATUS_EMITTING_UPDATES.equals(meta.status())) {
                continue;
            }

            // If we have a lastEmittedAt, check whether it's older than the idle timeout
            LocalDateTime lastEmit = meta.lastEmittedAt();
            if (lastEmit != null) {
                Duration sinceLastEmission = Duration.between(lastEmit, now);
                if (sinceLastEmission.compareTo(IDLE_TIMEOUT) >= 0) {
                    updateStatus(agentId, STATUS_IDLE);
                    log.info("Scanner for agent {} transitioned to IDLE after {}s of inactivity",
                            agentId, sinceLastEmission.getSeconds());
                    pushMetricsEvent(ScannerMetricsChangedEvent.idleReached(agentId));
                }
            }
        }
    }

    /**
     * Push a metrics change event to all registered UI callbacks.
     */
    private void pushMetricsEvent(ScannerMetricsChangedEvent event) {
        if (metricsEventPublisher != null) {
            try {
                metricsEventPublisher.accept(event);
            } catch (Exception e) {
                log.warn("Error publishing metrics event: {}", e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        log.info("Destroying ScannerRegistry, cleaning up {} scanners", scanners.size());
        // Stop idle checker
        idleChecker.shutdownNow();
        try {
            if (!idleChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Idle checker did not terminate within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scanners.forEach((agentId, meta) -> {
            try {
                meta.scanner().destroy();
            } catch (Exception e) {
                log.warn("Error cleaning up scanner for agent {}: {}", agentId, e.getMessage());
            }
        });
        scanners.clear();
        log.info("ScannerRegistry destroyed");
    }
}
