package com.hdekker.ai_workflow.app.pipeline.management;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hdekker.ai_workflow.adapter.outbound.persistence.filemetadata.FileMetadataDatabaseAdapter;
import com.hdekker.ai_workflow.domain.file.FileHistory;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;
import com.hdekker.ai_workflow.adapter.outbound.file.EmissionDelayConfig;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;
import com.hdekker.ai_workflow.ui.events.ScannerMetricsChangedEvent;
import com.hdekker.ai_workflow.usecases.Scanner;
import com.hdekker.ai_workflow.usecases.ScannerObserverUseCase;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Scanner registry — thin collection + orchestration layer.
 * <p>
 * Stores {@link Scanner} instances by agentId and delegates lifecycle
 * operations (status, error handling, idle detection, metrics, DTO conversion)
 * to the Scanner domain object itself.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Create scanner instances for agents (one-to-one mapping)</li>
 *   <li>Store and retrieve scanners by agentId</li>
 *   <li>Delegate lifecycle to Scanner (status, error, idle, metrics, DTO)</li>
 * </ul>
 */
@Component
public class ScannerRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistry.class);

    private final ConcurrentHashMap<String, Scanner> scanners = new ConcurrentHashMap<>();
    private final FileMetadataDatabaseAdapter fileMetadataDatabase;
    private final ScannerObserverUseCase observer;
    private final EmissionDelayConfig emissionDelayConfig;

    /**
     * Creates a new ScannerRegistry with the required Spring dependencies.
     * <p>
     * Uses the default emission delay if {@code emissionDelayConfig} is null.
     *
     * @param applicationContext            Spring application context
     * @param fileMetadataDatabase          Database for file metadata change detection
     * @param observer                      scanner observer use case for metrics and UI push
     * @param emissionDelayConfig           Configuration for emission delay behaviour (nullable)
     */
    @Autowired
    public ScannerRegistry(
            ApplicationContext applicationContext,
            FileMetadataDatabaseAdapter fileMetadataDatabase,
            ScannerObserverUseCase observer,
            EmissionDelayConfig emissionDelayConfig) {
        this.fileMetadataDatabase = fileMetadataDatabase;
        this.observer = observer;
        this.emissionDelayConfig = emissionDelayConfig != null
                ? emissionDelayConfig
                : new EmissionDelayConfig(EmissionDelayConfig.DEFAULT_DELAY_SECONDS);
    }

    /**
     * Creates a new ScannerRegistry without emission delay configuration.
     * <p>
     * Defaults to the standard emission delay.
     * Useful for tests that don't need emission delay behaviour.
     *
     * @param applicationContext            Spring application context
     * @param fileMetadataDatabase          Database for file metadata change detection
     * @param observer                      scanner observer use case for metrics and UI push
     */
    public ScannerRegistry(
            ApplicationContext applicationContext,
            FileMetadataDatabaseAdapter fileMetadataDatabase,
            ScannerObserverUseCase observer) {
        this(applicationContext, fileMetadataDatabase, observer, null);
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
        Scanner existing = scanners.get(agentId);
        if (existing != null) {
            log.warn("Scanner already exists for agent {}, returning existing", agentId);
            return existing.toInfo();
        }

        Duration delay = Duration.ofSeconds(delaySeconds);
        Duration emissionDelay = Duration.ofSeconds(emissionDelayConfig.getEmissionDelaySeconds());

        // Create the scanner — it uses the observer for metrics and UI push
        Scanner scanner = new Scanner(
                agentId,
                targetDirectory,
                delay,
                emissionDelay,
                fileMetadataDatabase,
                observer);

        // Put in map BEFORE initSource() so callbacks can find it
        scanners.put(agentId, scanner);
        log.info("Created scanner for agent {} (target={}, delay={}s)",
                agentId, targetDirectory, delaySeconds);

        // Now initialise — status transitions (IDLE → EMITTING_INITIAL → EMITTING_UPDATES)
        // happen synchronously after the hash filter processes all files
        scanner.initSource(agentId);

        // Return the current state after initSource() has run
        return scanner.toInfo();
    }

    /**
     * Destroy a scanner by its ID.
     * Cleans up the scanner resources.
     *
     * @param scannerId the scanner ID to destroy (agentId)
     */
    public void destroyForAgent(String scannerId) {
        Scanner scanner = scanners.remove(scannerId);
        if (scanner != null) {
            try {
                scanner.destroy();
                log.info("Destroyed scanner for agent {}", scannerId);
            } catch (Exception e) {
                log.warn("Error destroying scanner {}: {}", scannerId, e.getMessage());
            }
        } else {
            log.warn("No scanner found for ID/agentId: {}", scannerId);
        }
    }

    /**
     * Refresh a scanner: reset it to full-scan mode.
     *
     * @param scannerId the scanner ID to refresh (agentId)
     */
    public void refreshAgent(String scannerId) {
        Scanner scanner = scanners.get(scannerId);
        if (scanner == null) {
            log.warn("Cannot refresh: no scanner found for ID/agentId: {}", scannerId);
            return;
        }

        // Reset scanner to full-scan mode.
        // Status transitions (EMITTING_INITIAL → EMITTING_UPDATES) happen inside
        // resetToFullScan() after the hash filter has processed all files.
        scanner.resetToFullScan();
        log.info("Refreshed scanner for agent {} to full-scan mode", scannerId);
    }

    /**
     * Get the scanner's flux for processing.
     *
     * @param scannerId the scanner ID (agentId)
     * @return the flux of file history events
     */
    public Flux<FileHistory> getScannerFlux(String scannerId) {
        Scanner scanner = scanners.get(scannerId);
        if (scanner == null) {
            log.warn("No scanner found for flux lookup: {}", scannerId);
            return Flux.empty();
        }
        return scanner.flux();
    }

    /**
     * List all registered scanners.
     */
    public List<ScannerInfo> listAll() {
        return new ArrayList<>(scanners.values()).stream()
                .map(Scanner::toInfo)
                .toList();
    }

    /**
     * Get a scanner by agentId.
     */
    public Optional<ScannerInfo> getById(String agentId) {
        Scanner scanner = scanners.get(agentId);
        return scanner != null ? Optional.of(scanner.toInfo()) : Optional.empty();
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
     * Delegates to the Scanner's own status management.
     * The observer handles UI push notifications.
     *
     * @param scannerId the agent ID or scanner ID
     * @param status    the new status enum
     */
    public void updateStatus(String scannerId, ScannerStatus status) {
        Scanner scanner = scanners.get(scannerId);
        if (scanner != null) {
            scanner.updateStatus(status);
            observer.pushToUI(new ScannerMetricsChangedEvent(scannerId, status, null, null, null));
            log.debug("Updated scanner {} status to {}", scannerId, status);
        }
    }

    /**
     * Transition a scanner to the ERROR state.
     * Delegates to the Scanner's own error handling.
     * The observer handles UI push notifications.
     *
     * @param agentId the owning agent's ID
     * @param reason  human-readable description of the error
     */
    public void transitionToError(String agentId, String reason) {
        Scanner scanner = scanners.get(agentId);
        if (scanner != null) {
            scanner.transitionToError(reason);
        } else {
            log.warn("Cannot transition to error: no scanner found for agent {}", agentId);
        }
    }

    /**
     * Recover a scanner from the ERROR state.
     * Delegates to the Scanner's own recovery.
     * The observer handles UI push notifications.
     *
     * @param agentId the owning agent's ID
     */
    public void recoverFromError(String agentId) {
        Scanner scanner = scanners.get(agentId);
        if (scanner != null) {
            scanner.recover();
            // Trigger a full rescan
            scanner.resetToFullScan();
        } else {
            log.warn("Cannot recover: no scanner found for agent {}", agentId);
        }
    }

    /**
     * Notify a scanner that an event has been emitted, resetting the idle timer.
     * Delegates to the Scanner's own emission tracking.
     *
     * @param agentId the owning agent's ID
     */
    public void recordEmission(String agentId) {
        Scanner scanner = scanners.get(agentId);
        if (scanner != null && scanner.getStatus() != ScannerStatus.ERROR) {
            scanner.recordEmission();
            log.debug("Recorded emission for agent {} – resetting idle timer", agentId);
        }
    }

    /**
     * Get the error message for a scanner, if any.
     *
     * @param scannerId the agent ID or scanner ID
     * @return the error message, or empty if no error
     */
    public Optional<String> getErrorMessage(String scannerId) {
        Scanner scanner = scanners.get(scannerId);
        return scanner != null
                ? Optional.ofNullable(scanner.getErrorMessage())
                : Optional.empty();
    }

    @Override
    public void destroy() {
        log.info("Destroying ScannerRegistry, cleaning up {} scanners", scanners.size());
        scanners.forEach((agentId, scanner) -> {
            try {
                scanner.destroy();
            } catch (Exception e) {
                log.warn("Error cleaning up scanner for agent {}: {}", agentId, e.getMessage());
            }
        });
        scanners.clear();
        log.info("ScannerRegistry destroyed");
    }
}
