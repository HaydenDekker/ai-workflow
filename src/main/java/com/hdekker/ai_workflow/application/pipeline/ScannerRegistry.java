package com.hdekker.ai_workflow.application.pipeline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.hdekker.ai_workflow.application.file.FileComparator;
import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;
import com.hdekker.ai_workflow.application.file.port.FileWatcherPort;
import com.hdekker.ai_workflow.application.scanner.ScannerService;
import com.hdekker.ai_workflow.application.scanner.port.ScannerMetricsPort;
import com.hdekker.ai_workflow.domain.scanner.ScannerStatus;

import reactor.core.publisher.Flux;

/**
 * Scanner registry — thin collection + orchestration layer.
 * <p>
 * Stores {@link ScannerService} instances by agentId and delegates lifecycle
 * operations (status, error handling, idle detection, metrics, DTO conversion)
 * to the ScannerService itself.
 * <p>
 * Depends on port interfaces, not concrete infrastructure:
 * <ul>
 *   <li>{@link FileMetadataRepository} — for file metadata change detection</li>
 *   <li>{@link ScannerMetricsPort} — for metrics observation and UI push</li>
 * </ul>
 *
 * @see ScannerService
 */
public class ScannerRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistry.class);

    private final ConcurrentHashMap<String, ScannerService> scanners = new ConcurrentHashMap<>();
    private final FileMetadataRepository fileMetadataRepository;
    private final ScannerMetricsPort observer;
    private final FileWatcherPort fileWatcherFactory;
    private final Duration defaultEmissionDelay;
    private final Duration defaultPollInterval;

    /**
     * Creates a new ScannerRegistry with port dependencies.
     *
     * @param fileMetadataRepository  repository for file metadata change detection
     * @param observer                scanner observer port for metrics and UI push
     * @param fileWatcherFactory      factory for creating file watcher instances
     * @param defaultEmissionDelay    default emission delay between consecutive file emissions
     * @param defaultPollInterval     default poll interval for the watch service
     */
    public ScannerRegistry(FileMetadataRepository fileMetadataRepository,
                           ScannerMetricsPort observer,
                           FileWatcherPort fileWatcherFactory,
                           Duration defaultEmissionDelay,
                           Duration defaultPollInterval) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.observer = observer;
        this.fileWatcherFactory = fileWatcherFactory;
        this.defaultEmissionDelay = defaultEmissionDelay;
        this.defaultPollInterval = defaultPollInterval;
    }

    /**
     * Creates a ScannerRegistry with default emission delay and poll interval (1 second).
     */
    public ScannerRegistry(FileMetadataRepository fileMetadataRepository,
                           ScannerMetricsPort observer,
                           FileWatcherPort fileWatcherFactory) {
        this(fileMetadataRepository, observer, fileWatcherFactory,
                Duration.ofSeconds(2), Duration.ofSeconds(1));
    }

    /**
     * Create a scanner for the given agent.
     * Validates that the target directory exists before creating the scanner.
     *
     * @param agentId         the owning agent's ID
     * @param targetDirectory the absolute path to watch
     * @param delaySeconds    poll delay in seconds
     * @return the created scanner info
     * @throws IllegalArgumentException if the target directory does not exist
     */
    public ScannerService.ScannerInfo createForAgent(String agentId, String targetDirectory, int delaySeconds) {
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
        ScannerService existing = scanners.get(agentId);
        if (existing != null) {
            log.warn("Scanner already exists for agent {}, returning existing", agentId);
            return existing.toInfo();
        }

        Duration delay = Duration.ofSeconds(delaySeconds);
        Duration emissionDelay = defaultEmissionDelay != null ? defaultEmissionDelay : Duration.ofSeconds(2);

        // Create file watcher via port
        FileWatcherPort watcher = fileWatcherFactory.forDirectory(folderPath, delay);

        // Create the comparator
        FileComparator comparator = new FileComparator(fileMetadataRepository);

        // Create the scanner — it uses the observer for metrics and UI push
        ScannerService scanner = new ScannerService(
                agentId,
                targetDirectory,
                delay,
                emissionDelay,
                watcher,
                comparator,
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
     */
    public void destroyForAgent(String scannerId) {
        ScannerService scanner = scanners.remove(scannerId);
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
     */
    public void refreshAgent(String scannerId) {
        ScannerService scanner = scanners.get(scannerId);
        if (scanner == null) {
            log.warn("Cannot refresh: no scanner found for ID/agentId: {}", scannerId);
            return;
        }

        scanner.resetToFullScan();
        log.info("Refreshed scanner for agent {} to full-scan mode", scannerId);
    }

    /**
     * Get the scanner's flux for processing.
     */
    public Flux<com.hdekker.ai_workflow.domain.file.FileHistory> getScannerFlux(String scannerId) {
        ScannerService scanner = scanners.get(scannerId);
        if (scanner == null) {
            log.warn("No scanner found for flux lookup: {}", scannerId);
            return Flux.empty();
        }
        return scanner.flux();
    }

    /**
     * List all registered scanners.
     */
    public List<ScannerService.ScannerInfo> listAll() {
        return new ArrayList<>(scanners.values()).stream()
                .map(ScannerService::toInfo)
                .toList();
    }

    /**
     * Get a scanner by agentId.
     */
    public Optional<ScannerService.ScannerInfo> getById(String agentId) {
        ScannerService scanner = scanners.get(agentId);
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
     */
    public void updateStatus(String scannerId, ScannerStatus status) {
        ScannerService scanner = scanners.get(scannerId);
        if (scanner != null) {
            scanner.updateStatus(status);
            observer.pushToUI(scannerId, status);
            log.debug("Updated scanner {} status to {}", scannerId, status);
        }
    }

    /**
     * Transition a scanner to the ERROR state.
     */
    public void transitionToError(String agentId, String reason) {
        ScannerService scanner = scanners.get(agentId);
        if (scanner != null) {
            scanner.transitionToError(reason);
        } else {
            log.warn("Cannot transition to error: no scanner found for agent {}", agentId);
        }
    }

    /**
     * Recover a scanner from the ERROR state.
     */
    public void recoverFromError(String agentId) {
        ScannerService scanner = scanners.get(agentId);
        if (scanner != null) {
            scanner.recover();
            scanner.resetToFullScan();
        } else {
            log.warn("Cannot recover: no scanner found for agent {}", agentId);
        }
    }

    /**
     * Notify a scanner that an event has been emitted, resetting the idle timer.
     */
    public void recordEmission(String agentId) {
        ScannerService scanner = scanners.get(agentId);
        if (scanner != null && scanner.getStatus() != ScannerStatus.ERROR) {
            scanner.recordEmission();
            log.debug("Recorded emission for agent {} – resetting idle timer", agentId);
        }
    }

    /**
     * Get the error message for a scanner, if any.
     */
    public Optional<String> getErrorMessage(String scannerId) {
        ScannerService scanner = scanners.get(scannerId);
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
