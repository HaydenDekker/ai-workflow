package com.hdekker.ai_workflow.app.pipeline.management;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.hdekker.ai_workflow.database.filemetadata.FileMetadataDatabase;
import com.hdekker.ai_workflow.files.FileSystemScannerAdapter;
import com.hdekker.ai_workflow.files.FileHistory;
import com.hdekker.ai_workflow.rest.dto.ScannerInfo;

import reactor.core.publisher.Flux;

/**
 * Full implementation of the scanner registry.
 * Manages the lifecycle of {@link FileSystemScannerAdapter} instances, one per agent.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Create scanner instances for agents (one-to-one mapping)</li>
 *   <li>Track IntegrationFlow lifecycle (start/stop/destroy)</li>
 *   <li>Support full-scan reset via {@link #refreshAgent(String)}</li>
 *   <li>Thread-safe access via ConcurrentHashMap</li>
 * </ul>
 * <p>
 * Scanner statuses:
 * <ul>
 *   <li>IDLE — scanner created but not yet emitting</li>
 *   <li>EMITTING_ALL — performing a full scan (all files emitted)</li>
 *   <li>EMITTING_UPDATES — watching for incremental changes</li>
 *   <li>ERROR — scanner encountered an error</li>
 * </ul>
 */
@Component
public class ScannerRegistry implements org.springframework.beans.factory.DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistry.class);
    private static final Duration DEFAULT_DELAY = Duration.ofSeconds(5);

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
            LocalDateTime lastEmittedAt
    ) {
        ScannerMetadata withStatus(String newStatus) {
            return new ScannerMetadata(scanner, agentId, folderPath, newStatus, createdAt, lastEmittedAt);
        }
    }

    private final ConcurrentHashMap<String, ScannerMetadata> scanners = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    private final FileMetadataDatabase fileMetadataDatabase;

    /**
     * Creates a new ScannerRegistry with the required Spring dependencies.
     *
     * @param applicationContext        Spring application context
     * @param fileMetadataDatabase      Database for file metadata change detection
     */
    public ScannerRegistry(
            ApplicationContext applicationContext,
            FileMetadataDatabase fileMetadataDatabase) {
        this.applicationContext = applicationContext;
        this.fileMetadataDatabase = fileMetadataDatabase;
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

        // Create the scanner adapter
        FileSystemScannerAdapter scanner = new FileSystemScannerAdapter(
                targetDirectory,
                delay,
                fileMetadataDatabase);

        ScannerMetadata metadata = new ScannerMetadata(
                scanner, agentId, targetDirectory, "EMITTING_ALL",
                LocalDateTime.now(), null);

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

        ScannerMetadata updated = meta.withStatus("EMITTING_ALL");
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
        updateStatus(scannerId, "EMITTING_UPDATES");

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
     * Seed the registry with sample scanner data for UI development.
     * Only seeds if the registry is empty.
     */
    public void seedDummyData() {
        if (!scanners.isEmpty()) {
            return; // Already seeded
        }

        scanners.put("agent-alpha", new ScannerMetadata(
                null, "agent-alpha", "/data/inbox/documents", "EMITTING_UPDATES",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(5)));

        scanners.put("agent-beta", new ScannerMetadata(
                null, "agent-beta", "/data/inbox/images", "IDLE",
                LocalDateTime.now().minusHours(1), null));

        scanners.put("agent-gamma", new ScannerMetadata(
                null, "agent-gamma", "C:\\data\\uploads\\contracts", "EMITTING_ALL",
                LocalDateTime.now().minusMinutes(30), LocalDateTime.now().minusMinutes(2)));

        scanners.put("agent-delta", new ScannerMetadata(
                null, "agent-delta", "/data/inbox/reports", "ERROR",
                LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(4)));

        log.info("Seeded {} dummy scanners", scanners.size());
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
                meta.lastEmittedAt()
        );
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

    @Override
    public void destroy() {
        log.info("Destroying ScannerRegistry, cleaning up {} scanners", scanners.size());
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
