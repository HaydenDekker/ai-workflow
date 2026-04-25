package com.hdekker.ai_workflow.app.pipeline.management;

import com.hdekker.ai_workflow.rest.dto.ScannerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 stub: in-memory scanner registry with dummy data.
 * Scanners are tracked in a concurrent map keyed by agentId (one-to-one).
 *
 * Full implementation (Phase 2) will manage real {@code FileSystemScannerAdapter} instances.
 */
@Component
public class ScannerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistry.class);

    private final ConcurrentHashMap<String, ScannerInfo> scanners = new ConcurrentHashMap<>();

    /**
     * Create a scanner for the given agent (stub — no actual file scanning).
     */
    public ScannerInfo createForAgent(String agentId, String targetDirectory, int delaySeconds) {
        ScannerInfo info = new ScannerInfo(
            UUID.randomUUID().toString(),
            agentId,
            targetDirectory,
            "IDLE",
            LocalDateTime.now(),
            null
        );
        scanners.put(agentId, info);
        log.info("Created scanner {} for agent {} (target={}, delay={}s)", info.id(), agentId, targetDirectory, delaySeconds);
        return info;
    }

    /**
     * Delete a scanner by agentId.
     */
    public void deleteById(String agentId) {
        scanners.remove(agentId);
        log.info("Deleted scanner for agent {}", agentId);
    }

    /**
     * Delete a scanner by its own ID.
     */
    public void deleteById(String agentId, String scannerId) {
        // Stub: use agentId as key
        scanners.remove(agentId);
        log.info("Deleted scanner {} for agent {}", scannerId, agentId);
    }

    /**
     * List all registered scanners.
     */
    public List<ScannerInfo> listAll() {
        return new ArrayList<>(scanners.values());
    }

    /**
     * Get a scanner by agentId.
     */
    public Optional<ScannerInfo> getById(String agentId) {
        return Optional.ofNullable(scanners.get(agentId));
    }

    /**
     * Refresh agent: reset scanner to emit all files (stub for Phase 2).
     */
    public void refreshAgent(String agentId) {
        scanners.computeIfPresent(agentId, (key, info) ->
            new ScannerInfo(info.id(), info.agentId(), info.targetDirectory(), "EMITTING_ALL", info.createdAt(), info.lastEmittedAt())
        );
        log.info("Refreshed scanner for agent {}", agentId);
    }

    // ── Dummy data for Phase 1 UI development ──

    /**
     * Seed the registry with sample scanner data for UI development.
     */
    public void seedDummyData() {
        if (!scanners.isEmpty()) {
            return; // Already seeded
        }

        scanners.put("agent-alpha", new ScannerInfo(
            UUID.randomUUID().toString(),
            "agent-alpha",
            "/data/inbox/documents",
            "EMITTING_UPDATES",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusMinutes(5)
        ));

        scanners.put("agent-beta", new ScannerInfo(
            UUID.randomUUID().toString(),
            "agent-beta",
            "/data/inbox/images",
            "IDLE",
            LocalDateTime.now().minusHours(1),
            null
        ));

        scanners.put("agent-gamma", new ScannerInfo(
            UUID.randomUUID().toString(),
            "agent-gamma",
            "C:\\data\\uploads\\contracts",
            "EMITTING_ALL",
            LocalDateTime.now().minusMinutes(30),
            LocalDateTime.now().minusMinutes(2)
        ));

        scanners.put("agent-delta", new ScannerInfo(
            UUID.randomUUID().toString(),
            "agent-delta",
            "/data/inbox/reports",
            "ERROR",
            LocalDateTime.now().minusHours(5),
            LocalDateTime.now().minusHours(4)
        ));

        log.info("Seeded {} dummy scanners", scanners.size());
    }
}
